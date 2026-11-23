package com.jwwviewer.app.translate

/** True if [text] contains Hiragana, Katakana, or CJK ideographs used in Japanese notes. */
fun containsJapanese(text: String): Boolean {
    for (ch in text) {
        val c = ch.code
        when {
            c in 0x3040..0x309F -> return true // Hiragana
            c in 0x30A0..0x30FF -> return true // Katakana
            c in 0x31F0..0x31FF -> return true // Katakana phonetic ext
            c in 0xFF66..0xFF9D -> return true // Half-width katakana
            c in 0x4E00..0x9FFF -> return true // CJK unified (common in JA notes)
        }
    }
    return false
}

fun normalizeCadText(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n').trim()
