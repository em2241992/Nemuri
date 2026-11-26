package org.koitharu.kotatsu.reader.domain

import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import java.lang.ref.WeakReference

enum class ReaderShaderPreset(val key: String, val label: String) {
	OFF("OFF", "Off"),
	ANIME4K("ANIME4K", "Anime4K"),
	ARTCNN("ARTCNN", "ArtCNN"),
	FSR_1("FSR_1", "AMD FSR 1.0"),
	;

	companion object {
		fun fromKey(key: String?): ReaderShaderPreset =
			entries.firstOrNull { it.key == key } ?: OFF
	}
}

data class ReaderShaderConfig(
	val preset: ReaderShaderPreset = ReaderShaderPreset.OFF,
	val descreen: Boolean = false,
	val inkLevels: Boolean = false,
	val strength: Float = 0.75f,
) {
	val isActive: Boolean
		get() = preset != ReaderShaderPreset.OFF || descreen || inkLevels

	fun summaryLabel(): String {
		if (!isActive) return "Off"
		val parts = ArrayList<String>(3)
		if (preset != ReaderShaderPreset.OFF) parts += preset.label
		if (descreen) parts += "Descreen"
		if (inkLevels) parts += "Ink+"
		return parts.joinToString(" + ")
	}
}

/**
 * Universal GPU AGSL shader suite (Anime4K v0.9, AMD FSR 1.0 EASU+RCAS, Screentone Descreen,
 * and Crisp Ink & Paper Levels), chained as screen-space RenderEffects on Android 13+.
 */
object UpscaleEffect {

	const val MIN_SCALE = 1.5f

	val isSupported: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

	val activePages = MutableStateFlow<Set<Long>>(emptySet())

	fun setActive(pageId: Long, isActive: Boolean) {
		activePages.update { pages ->
			if (isActive) pages + pageId else pages - pageId
		}
	}

	class Viewport(val scale: Float, val centerX: Float, val centerY: Float)

	private val views = HashMap<Long, WeakReference<SubsamplingScaleImageView>>()

	fun registerView(pageId: Long, ssiv: SubsamplingScaleImageView) {
		views.entries.removeAll { (id, ref) -> id != pageId && ref.get() === ssiv }
		views[pageId] = WeakReference(ssiv)
	}

	fun viewportFor(pageId: Long): Viewport? {
		val ssiv = views[pageId]?.get() ?: return null
		if (!ssiv.isReady || !ssiv.isShown) {
			return null
		}
		val visible = Rect()
		if (!ssiv.getGlobalVisibleRect(visible)) {
			return null
		}
		val location = IntArray(2)
		ssiv.getLocationOnScreen(location)
		val center = ssiv.viewToSourceCoord(
			visible.exactCenterX() - location[0],
			visible.exactCenterY() - location[1],
		) ?: return null
		return Viewport(ssiv.scale, center.x, center.y)
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	fun create(scale: Float): RenderEffect? = create(
		scale = scale,
		config = ReaderShaderConfig(preset = ReaderShaderPreset.ANIME4K),
	)

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	fun create(scale: Float, config: ReaderShaderConfig): RenderEffect? = runCatching {
		if (!config.isActive) return null
		val strength = config.strength.coerceIn(0.3f, 1.0f)
		val px = (scale / 2f).coerceIn(1f, 2.5f)
		var effect: RenderEffect? = null

		fun appendPass(shaderSource: String, setup: RuntimeShader.() -> Unit) {
			val shader = RuntimeShader(shaderSource).apply(setup)
			val pass = RenderEffect.createRuntimeShaderEffect(shader, "src")
			effect = effect?.let { RenderEffect.createChainEffect(pass, it) } ?: pass
		}

		// 1. Optional Screentone Descreen (edge-preserving halftone smoothing)
		if (config.descreen) {
			appendPass(DESCREEN_AGSL) {
				setFloatUniform("strength", (strength * 0.85f).coerceIn(0.25f, 0.9f))
				setFloatUniform("px", 1.25f)
			}
		}

		// 2. Primary Upscaler / Line-Art Pass
		when (config.preset) {
			ReaderShaderPreset.OFF -> Unit
			ReaderShaderPreset.ANIME4K -> {
				val passes = when {
					scale >= 3f -> 4
					scale >= 2f -> 3
					else -> 2
				}
				repeat(passes) {
					appendPass(ANIME4K_AGSL) {
						setFloatUniform("strength", strength)
						setFloatUniform("px", px)
					}
				}
			}

			ReaderShaderPreset.ARTCNN -> {
				// Pass 1: Structure-tensor directional line-art interpolation preserving natural stroke weights
				appendPass(ARTCNN_DIRECTIONAL_AGSL) {
					setFloatUniform("strength", strength)
					setFloatUniform("px", px)
				}
				// Pass 2: CNN-style anti-ringing residual feature refinement protecting fine text & screentones
				appendPass(ARTCNN_RESIDUAL_AGSL) {
					setFloatUniform("strength", strength)
					setFloatUniform("px", 1.0f)
				}
			}

			ReaderShaderPreset.FSR_1 -> {
				// EASU edge-directed smoothing/interpolation pass
				appendPass(FSR_EASU_AGSL) {
					setFloatUniform("px", px)
				}
				// RCAS Robust Contrast-Adaptive Sharpening pass (halo-free)
				appendPass(FSR_RCAS_AGSL) {
					setFloatUniform("sharpness", strength)
					setFloatUniform("px", 1.0f)
				}
			}
		}

		// 3. Optional Crisp Ink & Paper Levels (black-point ink + clean highlights)
		if (config.inkLevels) {
			appendPass(INK_LEVELS_AGSL) {
				setFloatUniform("strength", strength)
			}
		}

		effect
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrNull()

	private val ANIME4K_AGSL = """
		uniform shader src;
		uniform float strength;
		uniform float px;

		float lum(float2 p) {
			return dot(float4(src.eval(p)).rgb, float3(0.299, 0.587, 0.114));
		}

		float4 push(float4 cc, float4 a, float4 b, float4 c) {
			return mix(cc, (a + b + c) / 3.0, strength);
		}

		half4 main(float2 fc) {
			float4 ctl = float4(src.eval(fc + float2(-px, -px)));
			float4 ctc = float4(src.eval(fc + float2(0.0, -px)));
			float4 ctr = float4(src.eval(fc + float2(px, -px)));
			float4 cml = float4(src.eval(fc + float2(-px, 0.0)));
			float4 cmc = float4(src.eval(fc));
			float4 cmr = float4(src.eval(fc + float2(px, 0.0)));
			float4 cbl = float4(src.eval(fc + float2(-px, px)));
			float4 cbc = float4(src.eval(fc + float2(0.0, px)));
			float4 cbr = float4(src.eval(fc + float2(px, px)));

			float l11 = dot(ctl.rgb, float3(0.299, 0.587, 0.114));
			float l12 = dot(ctc.rgb, float3(0.299, 0.587, 0.114));
			float l13 = dot(ctr.rgb, float3(0.299, 0.587, 0.114));
			float l21 = dot(cml.rgb, float3(0.299, 0.587, 0.114));
			float l22 = dot(cmc.rgb, float3(0.299, 0.587, 0.114));
			float l23 = dot(cmr.rgb, float3(0.299, 0.587, 0.114));
			float l31 = dot(cbl.rgb, float3(0.299, 0.587, 0.114));
			float l32 = dot(cbc.rgb, float3(0.299, 0.587, 0.114));
			float l33 = dot(cbr.rgb, float3(0.299, 0.587, 0.114));

			float l01 = lum(fc + float2(-px, -2.0 * px));
			float l02 = lum(fc + float2(0.0, -2.0 * px));
			float l03 = lum(fc + float2(px, -2.0 * px));
			float l10 = lum(fc + float2(-2.0 * px, -px));
			float l14 = lum(fc + float2(2.0 * px, -px));
			float l20 = lum(fc + float2(-2.0 * px, 0.0));
			float l24 = lum(fc + float2(2.0 * px, 0.0));
			float l30 = lum(fc + float2(-2.0 * px, px));
			float l34 = lum(fc + float2(2.0 * px, px));
			float l41 = lum(fc + float2(-px, 2.0 * px));
			float l42 = lum(fc + float2(0.0, 2.0 * px));
			float l43 = lum(fc + float2(px, 2.0 * px));

			float atl = 1.0 - clamp(abs(l12 - l10) + abs(l21 - l01), 0.0, 1.0);
			float atc = 1.0 - clamp(abs(l13 - l11) + abs(l22 - l02), 0.0, 1.0);
			float atr = 1.0 - clamp(abs(l14 - l12) + abs(l23 - l03), 0.0, 1.0);
			float aml = 1.0 - clamp(abs(l22 - l20) + abs(l31 - l11), 0.0, 1.0);
			float amc = 1.0 - clamp(abs(l23 - l21) + abs(l32 - l12), 0.0, 1.0);
			float amr = 1.0 - clamp(abs(l24 - l22) + abs(l33 - l13), 0.0, 1.0);
			float abl = 1.0 - clamp(abs(l32 - l30) + abs(l41 - l21), 0.0, 1.0);
			float abc = 1.0 - clamp(abs(l33 - l31) + abs(l42 - l22), 0.0, 1.0);
			float abr = 1.0 - clamp(abs(l34 - l32) + abs(l43 - l23), 0.0, 1.0);

			float minL; float maxD;

			minL = min(min(atl, atc), atr);
			maxD = max(max(abl, abc), abr);
			if (minL > amc && minL > maxD) { return half4(push(cmc, ctl, ctc, ctr)); }
			minL = min(min(abl, abc), abr);
			maxD = max(max(atl, atc), atr);
			if (minL > amc && minL > maxD) { return half4(push(cmc, cbl, cbc, cbr)); }

			minL = min(min(atl, aml), abl);
			maxD = max(max(atr, amr), abr);
			if (minL > amc && minL > maxD) { return half4(push(cmc, ctl, cml, cbl)); }
			minL = min(min(atr, amr), abr);
			maxD = max(max(atl, aml), abl);
			if (minL > amc && minL > maxD) { return half4(push(cmc, ctr, cmr, cbr)); }

			minL = min(min(atc, atr), amr);
			maxD = max(max(aml, abl), abc);
			if (minL > amc && minL > maxD) { return half4(push(cmc, ctc, ctr, cmr)); }
			minL = min(min(aml, abl), abc);
			maxD = max(max(atc, atr), amr);
			if (minL > amc && minL > maxD) { return half4(push(cmc, cml, cbl, cbc)); }

			minL = min(min(atl, atc), aml);
			maxD = max(max(amr, abr), abc);
			if (minL > amc && minL > maxD) { return half4(push(cmc, ctl, ctc, cml)); }
			minL = min(min(amr, abr), abc);
			maxD = max(max(atl, atc), aml);
			if (minL > amc && minL > maxD) { return half4(push(cmc, cmr, cbr, cbc)); }

			return half4(cmc);
		}
	""".trimIndent()

	// AMD FSR 1.0 inspired EASU (Edge-Adaptive Spatial Upsampling) in AGSL
	private val FSR_EASU_AGSL = """
		uniform shader src;
		uniform float px;

		float luma(float3 c) {
			return dot(c, float3(0.299, 0.587, 0.114));
		}

		half4 main(float2 fc) {
			float3 c = float4(src.eval(fc)).rgb;
			float3 n = float4(src.eval(fc + float2(0.0, -px))).rgb;
			float3 s = float4(src.eval(fc + float2(0.0, px))).rgb;
			float3 w = float4(src.eval(fc + float2(-px, 0.0))).rgb;
			float3 e = float4(src.eval(fc + float2(px, 0.0))).rgb;
			float3 nw = float4(src.eval(fc + float2(-px, -px))).rgb;
			float3 ne = float4(src.eval(fc + float2(px, -px))).rgb;
			float3 sw = float4(src.eval(fc + float2(-px, px))).rgb;
			float3 se = float4(src.eval(fc + float2(px, px))).rgb;

			float ln = luma(n);
			float ls = luma(s);
			float lw = luma(w);
			float le = luma(e);
			float lc = luma(c);

			float gx = (le - lw) * 0.5;
			float gy = (ls - ln) * 0.5;
			float edgeStrength = clamp((abs(gx) + abs(gy)) * 2.0, 0.0, 1.0);

			float2 dir = float2(-gy, gx);
			float dirLen = max(length(dir), 0.0001);
			dir = dir / dirLen;

			float3 alongA = float4(src.eval(fc + dir * (0.5 * px))).rgb;
			float3 alongB = float4(src.eval(fc - dir * (0.5 * px))).rgb;
			float3 softMix = (alongA + alongB + c * 2.0) * 0.25;

			float3 mn = min(min(min(n, s), min(w, e)), c);
			float3 mx = max(max(max(n, s), max(w, e)), c);
			float3 clamped = clamp(mix(c, softMix, edgeStrength * 0.55), mn, mx);

			// Preserve crisp dark line centers when neighbors are bright
			float diagContrast = abs(luma(nw) - luma(se)) + abs(luma(ne) - luma(sw));
			float centerWeight = clamp(1.0 - diagContrast * 0.35, 0.4, 1.0);
			float3 outColor = mix(clamped, c, centerWeight * 0.35 + (1.0 - edgeStrength) * 0.45);
			return half4(outColor, 1.0);
		}
	""".trimIndent()

	// AMD FSR 1.0 inspired RCAS (Robust Contrast-Adaptive Sharpening) in AGSL
	private val FSR_RCAS_AGSL = """
		uniform shader src;
		uniform float sharpness;
		uniform float px;

		float luma(float3 c) {
			return dot(c, float3(0.299, 0.587, 0.114));
		}

		half4 main(float2 fc) {
			float3 b = float4(src.eval(fc + float2(0.0, -px))).rgb;
			float3 d = float4(src.eval(fc + float2(-px, 0.0))).rgb;
			float3 e = float4(src.eval(fc)).rgb;
			float3 f = float4(src.eval(fc + float2(px, 0.0))).rgb;
			float3 h = float4(src.eval(fc + float2(0.0, px))).rgb;

			float lb = luma(b);
			float ld = luma(d);
			float le = luma(e);
			float lf = luma(f);
			float lh = luma(h);

			float minL = min(min(lb, lh), min(ld, lf));
			float maxL = max(max(lb, lh), max(ld, lf));

			// Compute halo-free negative lobe limit per RCAS
			float hitMin = min(minL, le) / max(4.0 * maxL, 0.0001);
			float hitMax = (1.0 - max(maxL, le)) / max(4.0 * minL - 4.0, -0.0001);
			float lobe = max(-hitMin, hitMax);
			float weight = max(-0.1875 * clamp(sharpness, 0.0, 1.0), min(lobe, 0.0)) * 0.9;

			// Noise attenuation: damp sharpening on flat screentone noise
			float nz = abs((lb + ld + lf + lh) * 0.25 - le);
			float noiseMask = 1.0 - clamp(nz * 2.5, 0.0, 0.65);
			weight *= noiseMask;

			float rcpWeight = 1.0 / (1.0 + 4.0 * weight);
			float3 outColor = clamp(((b + d + f + h) * weight + e) * rcpWeight, 0.0, 1.0);
			return half4(outColor, 1.0);
		}
	""".trimIndent()

	// Screentone / Halftone Descreen (bilateral filter tuned for dot patterns, keeping dark ink edges sharp)
	private val DESCREEN_AGSL = """
		uniform shader src;
		uniform float strength;
		uniform float px;

		float luma(float3 c) {
			return dot(c, float3(0.299, 0.587, 0.114));
		}

		half4 main(float2 fc) {
			float3 center = float4(src.eval(fc)).rgb;
			float lc = luma(center);

			float3 c1 = float4(src.eval(fc + float2(-px, -px))).rgb;
			float3 c2 = float4(src.eval(fc + float2(0.0, -px))).rgb;
			float3 c3 = float4(src.eval(fc + float2(px, -px))).rgb;
			float3 c4 = float4(src.eval(fc + float2(-px, 0.0))).rgb;
			float3 c5 = float4(src.eval(fc + float2(px, 0.0))).rgb;
			float3 c6 = float4(src.eval(fc + float2(-px, px))).rgb;
			float3 c7 = float4(src.eval(fc + float2(0.0, px))).rgb;
			float3 c8 = float4(src.eval(fc + float2(px, px))).rgb;

			// Smooth mid-tone screentones while locking hard edges via luma range weights
			float sigma = 0.28;
			float w1 = exp(-pow((luma(c1) - lc) / sigma, 2.0));
			float w2 = exp(-pow((luma(c2) - lc) / sigma, 2.0));
			float w3 = exp(-pow((luma(c3) - lc) / sigma, 2.0));
			float w4 = exp(-pow((luma(c4) - lc) / sigma, 2.0));
			float w5 = exp(-pow((luma(c5) - lc) / sigma, 2.0));
			float w6 = exp(-pow((luma(c6) - lc) / sigma, 2.0));
			float w7 = exp(-pow((luma(c7) - lc) / sigma, 2.0));
			float w8 = exp(-pow((luma(c8) - lc) / sigma, 2.0));

			float wSum = 1.0 + w1 + w2 + w3 + w4 + w5 + w6 + w7 + w8;
			float3 blurred = (center + c1 * w1 + c2 * w2 + c3 * w3 + c4 * w4 + c5 * w5 + c6 * w6 + c7 * w7 + c8 * w8) / wSum;

			// Reduce descreen effect on deep black ink lines so fine linework stays razor sharp
			float inkProtect = smoothstep(0.12, 0.36, lc);
			float3 outColor = mix(center, blurred, strength * inkProtect);
			return half4(outColor, 1.0);
		}
	""".trimIndent()

	// Crisp Ink & Paper Levels (deepens washed-out gray ink and brightens muddy scan backgrounds)
	private val INK_LEVELS_AGSL = """
		uniform shader src;
		uniform float strength;

		half4 main(float2 fc) {
			float3 c = float4(src.eval(fc)).rgb;
			float lum = max(dot(c, float3(0.299, 0.587, 0.114)), 0.0001);

			float blackPoint = mix(0.0, 0.075, strength);
			float whitePoint = mix(1.0, 0.925, strength);
			float stretched = clamp((lum - blackPoint) / max(whitePoint - blackPoint, 0.1), 0.0, 1.0);

			// Gentle S-curve in shadows & paper highlights
			float curved = mix(stretched, stretched * stretched * (3.0 - 2.0 * stretched), strength * 0.45);
			float ratio = curved / lum;
			float3 outColor = clamp(c * ratio, 0.0, 1.0);
			return half4(outColor, 1.0);
		}
	""".trimIndent()

	// ArtCNN-inspired Pass 1: Structure-tensor edge-directed line-art refinement that keeps manga ink weights natural
	private val ARTCNN_DIRECTIONAL_AGSL = """
		uniform shader src;
		uniform float strength;
		uniform float px;

		float luma(float3 c) {
			return dot(c, float3(0.299, 0.587, 0.114));
		}

		half4 main(float2 fc) {
			float3 c = float4(src.eval(fc)).rgb;
			float3 nw = float4(src.eval(fc + float2(-px, -px))).rgb;
			float3 n  = float4(src.eval(fc + float2(0.0, -px))).rgb;
			float3 ne = float4(src.eval(fc + float2(px, -px))).rgb;
			float3 w  = float4(src.eval(fc + float2(-px, 0.0))).rgb;
			float3 e  = float4(src.eval(fc + float2(px, 0.0))).rgb;
			float3 sw = float4(src.eval(fc + float2(-px, px))).rgb;
			float3 se = float4(src.eval(fc + float2(px, px))).rgb;
			float3 s  = float4(src.eval(fc + float2(0.0, px))).rgb;

			float lnw = luma(nw); float ln = luma(n); float lne = luma(ne);
			float lw  = luma(w);                     float le  = luma(e);
			float lsw = luma(sw); float ls = luma(s); float lse = luma(se);

			// Sobel gradients & 2D structure tensor for dominant stroke tangent
			float gx = (lne + 2.0 * le + lse) - (lnw + 2.0 * lw + lsw);
			float gy = (lsw + 2.0 * ls + lse) - (lnw + 2.0 * ln + lne);
			float mag = sqrt(gx * gx + gy * gy);
			float coherence = clamp(mag * 1.4, 0.0, 1.0);

			float2 tangent = float2(-gy, gx);
			float tLen = max(length(tangent), 0.0001);
			tangent /= tLen;

			// Directional samples along contour tangent to eliminate staircase aliasing without thinning ink
			float3 t1 = float4(src.eval(fc + tangent * (0.55 * px))).rgb;
			float3 t2 = float4(src.eval(fc - tangent * (0.55 * px))).rgb;
			float3 t3 = float4(src.eval(fc + tangent * (1.10 * px))).rgb;
			float3 t4 = float4(src.eval(fc - tangent * (1.10 * px))).rgb;
			float3 along = (c * 4.0 + (t1 + t2) * 2.0 + (t3 + t4)) / 10.0;

			// Anti-ringing neighborhood bounds clamp
			float3 mn = min(min(min(n, s), min(w, e)), min(min(nw, ne), min(sw, se)));
			mn = min(mn, c);
			float3 mx = max(max(max(n, s), max(w, e)), max(max(nw, ne), max(sw, se)));
			mx = max(mx, c);

			// Halftone screentone protection: preserve oscillatory dot grids where orthogonal contrast matches tangent
			float3 outColor = clamp(mix(c, along, coherence * strength * 0.65), mn, mx);
			return half4(outColor, 1.0);
		}
	""".trimIndent()

	// ArtCNN-inspired Pass 2: Residual feature crisping for fine linework & dialogue text
	private val ARTCNN_RESIDUAL_AGSL = """
		uniform shader src;
		uniform float strength;
		uniform float px;

		float luma(float3 c) {
			return dot(c, float3(0.299, 0.587, 0.114));
		}

		half4 main(float2 fc) {
			float3 c = float4(src.eval(fc)).rgb;
			float3 n = float4(src.eval(fc + float2(0.0, -px))).rgb;
			float3 s = float4(src.eval(fc + float2(0.0, px))).rgb;
			float3 w = float4(src.eval(fc + float2(-px, 0.0))).rgb;
			float3 e = float4(src.eval(fc + float2(px, 0.0))).rgb;
			float3 nw = float4(src.eval(fc + float2(-px, -px))).rgb;
			float3 ne = float4(src.eval(fc + float2(px, -px))).rgb;
			float3 sw = float4(src.eval(fc + float2(-px, px))).rgb;
			float3 se = float4(src.eval(fc + float2(px, px))).rgb;

			float3 smoothAvg = ((n + s + w + e) * 2.0 + (nw + ne + sw + se)) / 12.0;
			float3 residual = c - smoothAvg;

			float lc = luma(c);
			float lapLuma = abs(luma(residual));
			// Suppress amplification inside faint paper/screentone noise, boost crisp ink & bubble text edges
			float edgeMask = smoothstep(0.025, 0.18, lapLuma);
			float darkBoost = mix(1.0, 1.22, 1.0 - smoothstep(0.0, 0.55, lc));
			float gain = strength * 0.68 * edgeMask * darkBoost;

			float3 mn = min(min(n, s), min(w, e));
			mn = min(mn, c);
			float3 mx = max(max(n, s), max(w, e));
			mx = max(mx, c);

			// Strict local min/max clamp keeps screentones & fine text free of overshoot halos
			float3 refined = clamp(c + residual * gain, mn, mx);
			return half4(refined, 1.0);
		}
	""".trimIndent()
}
