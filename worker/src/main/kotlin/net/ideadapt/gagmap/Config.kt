package net.ideadapt.gagmap

import java.util.*

object Config {
    private val props: Properties = Properties()

    init {
        val resourceAsStream = javaClass.classLoader.getResourceAsStream(".env")
        if (resourceAsStream != null) {
            props.load(resourceAsStream)
        }
    }

    fun get(key: String): String? = props[key]?.toString() ?: System.getenv(key)
}