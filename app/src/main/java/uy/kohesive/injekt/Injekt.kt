package uy.kohesive.injekt

import uy.kohesive.injekt.api.InjektScope
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

object Injekt : InjektScope {
    private val instances = ConcurrentHashMap<Type, Any>()

    fun <T : Any> addSingleton(type: Type, instance: T) {
        instances[type] = instance
    }

    inline fun <reified T : Any> addSingleton(instance: T) {
        addSingleton(T::class.java, instance)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> get(): T =
        getInstance(T::class.java) as T

    override fun getInstance(type: Type): Any {
        instances[type]?.let { return it }
        if (type is Class<*>) {
            instances.entries.firstOrNull { (registered, value) ->
                (registered is Class<*> && type.isAssignableFrom(registered)) ||
                    (registered is Class<*> && registered.isAssignableFrom(type)) ||
                    type.isInstance(value)
            }?.value?.let { return it }
        }
        if (type is ParameterizedType) {
            instances[type.rawType]?.let { return it }
        }
        throw IllegalStateException("No Inku host dependency registered for $type")
    }
}

fun getInjekt(): InjektScope = Injekt

inline fun <reified T : Any> injectLazy(): Lazy<T> =
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Injekt.get<T>() }
