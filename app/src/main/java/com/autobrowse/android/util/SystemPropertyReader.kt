package com.autobrowse.android.util

internal object SystemPropertyReader {
    fun get(key: String, defaultValue: String = ""): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        method.invoke(null, key, defaultValue) as String
    }.getOrDefault(defaultValue)
}