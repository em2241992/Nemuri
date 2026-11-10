package uy.kohesive.injekt

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.api.InjektFactory
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

private class SimpleInjektRegistry : InjektRegistrar, InjektFactory {
	private val singletons = ConcurrentHashMap<Type, Any>()
	private val factories = ConcurrentHashMap<Type, () -> Any>()
	private val singletonFactories = ConcurrentHashMap<Type, () -> Any>()

	private fun normalizeType(type: Type): Type {
		if (type is ParameterizedType) return type.rawType
		return type
	}

	override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) {
		singletons[normalizeType(forType.type)] = singleInstance
	}

	override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) {
		singletonFactories[normalizeType(forType.type)] = factoryCalledOnce
	}

	override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) {
		factories[normalizeType(forType.type)] = factoryCalledEveryTime
	}

	fun registerClassSingleton(clazz: Class<*>, instance: Any) {
		singletons[clazz] = instance
	}

	@Suppress("UNCHECKED_CAST")
	override fun <R : Any> getInstance(forType: Type): R {
		val key = normalizeType(forType)
		singletons[key]?.let { return it as R }
		singletonFactories[key]?.let { creator ->
			val created = creator()
			singletons[key] = created
			return created as R
		}
		factories[key]?.let { creator ->
			return creator() as R
		}
		if (key == NetworkHelper::class.java) {
			val app = singletons[Application::class.java] as? Application
			val helper = NetworkHelper(app)
			singletons[NetworkHelper::class.java] = helper
			return helper as R
		}
		throw IllegalStateException("No injectable registered for $forType")
	}

	override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R =
		runCatching { getInstance<R>(forType) }.getOrDefault(default)

	override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
		runCatching { getInstance<R>(forType) }.getOrElse { default() }
}

private val globalInjektRegistry = SimpleInjektRegistry()

@Volatile
var Injekt: InjektScope = InjektScope(globalInjektRegistry, globalInjektRegistry)

object MihonInjektBridge {
	@Volatile
	private var initialized = false

	fun ensureInitialized(application: Application) {
		if (initialized) return
		synchronized(this) {
			if (initialized) return
			globalInjektRegistry.registerClassSingleton(Application::class.java, application)
			globalInjektRegistry.registerClassSingleton(android.content.Context::class.java, application)
			globalInjektRegistry.registerClassSingleton(NetworkHelper::class.java, NetworkHelper(application))
			globalInjektRegistry.registerClassSingleton(
				kotlinx.serialization.json.Json::class.java,
				kotlinx.serialization.json.Json {
					ignoreUnknownKeys = true
					isLenient = true
					coerceInputValues = true
					explicitNulls = false
				},
			)
			initialized = true
		}
	}
}

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.getInstance(T::class.java) }
