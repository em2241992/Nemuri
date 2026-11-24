package org.koitharu.kotatsu.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user API keys encrypted via Android Keystore (AES-256-GCM) in a dedicated
 * SharedPreferences file that is never included in backups or Google Drive sync.
 */
@Singleton
class AiSecretStore @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	@Synchronized
	fun getGeminiApiKey(): String = readSecret(KEY_GEMINI_API_KEY)

	@Synchronized
	fun setGeminiApiKey(key: String) = writeSecret(KEY_GEMINI_API_KEY, key)

	@Synchronized
	fun hasGeminiApiKey(): Boolean = prefs.contains(KEY_GEMINI_API_KEY) && getGeminiApiKey().isNotBlank()

	@Synchronized
	fun getOpenAiApiKey(): String = readSecret(KEY_OPENAI_API_KEY)

	@Synchronized
	fun setOpenAiApiKey(key: String) = writeSecret(KEY_OPENAI_API_KEY, key)

	@Synchronized
	fun hasOpenAiApiKey(): Boolean = prefs.contains(KEY_OPENAI_API_KEY) && getOpenAiApiKey().isNotBlank()

	@Synchronized
	fun clearAll() {
		prefs.edit { clear() }
	}

	private fun writeSecret(prefKey: String, value: String) {
		val trimmed = value.trim()
		if (trimmed.isEmpty()) {
			prefs.edit { remove(prefKey) }
			return
		}
		val encoded = runCatching {
			val secretKey = getOrCreateSecretKey() ?: return@runCatching null
			val cipher = Cipher.getInstance(TRANSFORMATION)
			cipher.init(Cipher.ENCRYPT_MODE, secretKey)
			val iv = cipher.iv
			val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
			if (iv.size != GCM_IV_LENGTH) return@runCatching null
			val payload = ByteArray(1 + iv.size + ciphertext.size)
			payload[0] = iv.size.toByte()
			System.arraycopy(iv, 0, payload, 1, iv.size)
			System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
			Base64.encodeToString(payload, Base64.NO_WRAP)
		}.getOrNull() ?: return
		prefs.edit { putString(prefKey, encoded) }
	}

	private fun readSecret(prefKey: String): String {
		val raw = prefs.getString(prefKey, null)?.takeIf { it.isNotBlank() } ?: return ""
		return runCatching {
			val payload = Base64.decode(raw, Base64.NO_WRAP)
			if (payload.size < 2) return@runCatching ""
			val ivLen = payload[0].toInt() and 0xFF
			if (ivLen <= 0 || payload.size <= 1 + ivLen) return@runCatching ""
			val iv = payload.copyOfRange(1, 1 + ivLen)
			val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
			val secretKey = getOrCreateSecretKey() ?: return@runCatching ""
			val cipher = Cipher.getInstance(TRANSFORMATION)
			cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
			String(cipher.doFinal(ciphertext), Charsets.UTF_8)
		}.getOrDefault("")
	}

	private fun getOrCreateSecretKey(): SecretKey? = runCatching {
		val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
		val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
		if (existing != null) return@runCatching existing
		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
		generator.init(
			KeyGenParameterSpec.Builder(
				KEY_ALIAS,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
			)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build(),
		)
		generator.generateKey()
	}.getOrNull()

	private companion object {
		const val PREFS_NAME = "nemuri_ai_secrets"
		const val ANDROID_KEYSTORE = "AndroidKeyStore"
		const val KEY_ALIAS = "nemuri_ai_secrets_v1"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val GCM_IV_LENGTH = 12
		const val GCM_TAG_BITS = 128
		const val KEY_GEMINI_API_KEY = "gemini_api_key_enc"
		const val KEY_OPENAI_API_KEY = "openai_api_key_enc"
	}
}
