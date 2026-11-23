package com.jwwviewer.app.data

/** Selectable backends for Japanese CAD-note translation. */
enum class TranslateEngine(val tag: String) {
    /** Google Translate — usually best meaning */
    GOOGLE("google"),
    /** MyMemory — alternate online engine */
    MYMEMORY("mymemory"),
    /** On-device ML Kit — offline after model download */
    ML_KIT("mlkit"),
    /** Try Google → MyMemory → ML Kit until one succeeds */
    AUTO("auto"),
    ;

    companion object {
        fun fromTag(tag: String): TranslateEngine =
            entries.firstOrNull { it.tag == tag }
                ?: when (tag) {
                    "online" -> GOOGLE // migrate old setting
                    else -> GOOGLE
                }
    }
}
