package uy.kohesive.injekt.api

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

interface InjektFactory {
    fun getInstance(type: Type): Any
}

interface InjektScope : InjektFactory

open class FullTypeReference<T> {
    val type: Type
        get() {
            val generic = javaClass.genericSuperclass
            return if (generic is ParameterizedType) {
                generic.actualTypeArguments.first()
            } else {
                Any::class.java
            }
        }

    fun getType(): Type = type
}

inline fun <reified T : Any> InjektFactory.get(): T =
    getInstance(T::class.java) as T
