package uy.kohesive.injekt.api

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

interface InjektFactory {
    fun getInstance(type: Type): Any
}

interface InjektScope : InjektFactory

interface TypeReference {
    fun getType(): Type
}

open class FullTypeReference<T> : TypeReference {
    override fun getType(): Type {
        val generic = javaClass.genericSuperclass
        return if (generic is ParameterizedType) {
            generic.actualTypeArguments.first()
        } else {
            Any::class.java
        }
    }
}

inline fun <reified T : Any> InjektFactory.get(): T =
    getInstance(T::class.java) as T
