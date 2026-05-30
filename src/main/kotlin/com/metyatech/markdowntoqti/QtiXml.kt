package com.metyatech.markdowntoqti

internal fun escapeXml(value: String): String =
    buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

internal fun formatSecondsAsIsoDuration(seconds: Int): String {
    require(seconds > 0) { "seconds must be positive" }
    return "PT${seconds}S"
}
