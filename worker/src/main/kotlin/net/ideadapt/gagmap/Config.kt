package net.ideadapt.gagmap

import java.util.*

object Config {
    private val props: Properties = Properties()

    init {
        props.load(javaClass.classLoader.getResourceAsStream(".env"))
    }

    fun get(key: String): String? = props[key]?.toString()
}