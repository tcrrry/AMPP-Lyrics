package com.tcrrry.lyrics.plugin

internal object OfflineModelPolicy {
    fun downloads(language: String, supported: List<String>): Set<String> {
        require(language in supported)
        return setOf(language, "zh").filterNot { it == "en" }.toSet()
    }
    fun canDelete(language: String, supported: List<String>) = language in supported && language != "en"
}
