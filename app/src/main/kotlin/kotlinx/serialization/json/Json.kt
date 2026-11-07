package kotlinx.serialization.json

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.ParameterizedType

class JsonBuilder {
	var ignoreUnknownKeys: Boolean = false
	var isLenient: Boolean = false
	var coerceInputValues: Boolean = false
	var prettyPrint: Boolean = false
	var encodeDefaults: Boolean = true
}

class Json(
	val configuration: JsonBuilder = JsonBuilder(),
) {
	inline fun <reified T> decodeFromString(raw: String): T {
		return decodeObject(raw, T::class.java)
	}

	@Suppress("UNCHECKED_CAST")
	fun <T> decodeObject(raw: String, clazz: Class<T>): T {
		val trimmed = raw.trim()
		val jsonObj = JSONObject(trimmed)
		val constructors = clazz.declaredConstructors
		// Prefer the primary constructor (avoiding synthetic default-mask constructors if possible, or using reflection)
		val primaryCtor = constructors
			.filter { ctor ->
				val paramTypes = ctor.parameterTypes
				paramTypes.isEmpty() || paramTypes.last().name != "kotlin.jvm.internal.DefaultConstructorMarker"
			}
			.maxByOrNull { it.parameterCount }
			?: constructors.first()
		primaryCtor.isAccessible = true

		// Create a default instance if zero-arg constructor exists for fallback default values
		val defaultInstance: T? = runCatching {
			val noArg = clazz.getDeclaredConstructor()
			noArg.isAccessible = true
			noArg.newInstance()
		}.getOrNull()

		val fields = clazz.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
		val paramTypes = primaryCtor.parameterTypes
		val genericTypes = primaryCtor.genericParameterTypes
		val args = arrayOfNulls<Any?>(paramTypes.size)

		for (i in paramTypes.indices) {
			val field = fields.getOrNull(i)
			val fieldName = field?.name ?: "arg$i"
			val pType = paramTypes[i]
			val gType = genericTypes.getOrNull(i)

			val defaultVal: Any? = if (defaultInstance != null && field != null) {
				runCatching {
					field.isAccessible = true
					field.get(defaultInstance)
				}.getOrNull()
			} else {
				null
			}

			if (!jsonObj.has(fieldName) || jsonObj.isNull(fieldName)) {
				args[i] = defaultVal
				continue
			}

			val rawVal = jsonObj.opt(fieldName)
			args[i] = runCatching {
				convertJsonValue(rawVal, pType, gType, defaultVal)
			}.getOrElse {
				if (configuration.coerceInputValues) defaultVal else throw it
			}
		}

		return primaryCtor.newInstance(*args) as T
	}

	private fun convertJsonValue(
		rawVal: Any?,
		targetType: Class<*>,
		genericType: java.lang.reflect.Type?,
		defaultVal: Any?,
	): Any? {
		if (rawVal == null || rawVal == JSONObject.NULL) return defaultVal
		return when {
			targetType == String::class.java -> rawVal.toString()
			targetType == Int::class.javaPrimitiveType || targetType == Int::class.javaObjectType -> {
				when (rawVal) {
					is Number -> rawVal.toInt()
					is String -> rawVal.toIntOrNull() ?: defaultVal
					else -> defaultVal
				}
			}
			targetType == Long::class.javaPrimitiveType || targetType == Long::class.javaObjectType -> {
				when (rawVal) {
					is Number -> rawVal.toLong()
					is String -> rawVal.toLongOrNull() ?: defaultVal
					else -> defaultVal
				}
			}
			targetType == Float::class.javaPrimitiveType || targetType == Float::class.javaObjectType -> {
				when (rawVal) {
					is Number -> rawVal.toFloat()
					is String -> rawVal.toFloatOrNull() ?: defaultVal
					else -> defaultVal
				}
			}
			targetType == Boolean::class.javaPrimitiveType || targetType == Boolean::class.javaObjectType -> {
				when (rawVal) {
					is Boolean -> rawVal
					is String -> rawVal.toBooleanStrictOrNull() ?: defaultVal
					else -> defaultVal
				}
			}
			targetType.isEnum -> {
				val name = rawVal.toString().trim()
				val constants = targetType.enumConstants ?: return defaultVal
				constants.firstOrNull { (it as Enum<*>).name.equals(name, ignoreCase = true) }
					?: if (configuration.coerceInputValues) defaultVal else throw IllegalArgumentException("Unknown enum $name")
			}
			List::class.java.isAssignableFrom(targetType) -> {
				val arr = rawVal as? JSONArray ?: return defaultVal ?: emptyList<Any>()
				val elemClass = (genericType as? ParameterizedType)
					?.actualTypeArguments
					?.firstOrNull() as? Class<*>
					?: String::class.java
				val out = ArrayList<Any?>(arr.length())
				for (idx in 0 until arr.length()) {
					if (!arr.isNull(idx)) {
						val converted = convertJsonValue(arr.get(idx), elemClass, null, null)
						if (converted != null) out.add(converted)
					}
				}
				out
			}
			else -> defaultVal
		}
	}
}

fun Json(builderAction: JsonBuilder.() -> Unit = {}): Json {
	val builder = JsonBuilder()
	builder.builderAction()
	return Json(builder)
}
