package uy.kohesive.injekt.api

import java.lang.reflect.Type

interface TypeReference<T> {
	val type: Type
}

open class FullTypeReference<T> protected constructor() : TypeReference<T> {
	override val type: Type = javaClass.genericSuperclass
}

inline fun <reified T : Any> fullType(): FullTypeReference<T> = object : FullTypeReference<T>() {}

interface InjektFactory {
	fun <R : Any> getInstance(forType: Type): R
	fun <R : Any> getInstanceOrElse(forType: Type, default: R): R
	fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R
}

interface InjektRegistrar {
	fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T)
	fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R)
	fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R)
}

interface InjektModule {
	fun InjektRegistrar.registerInjectables()
}

open class InjektScope(val registrar: InjektRegistrar, val factory: InjektFactory) :
	InjektRegistrar by registrar, InjektFactory by factory

inline fun <reified T : Any> InjektFactory.get(): T = getInstance(T::class.java)
