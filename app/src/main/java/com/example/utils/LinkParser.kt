package com.example.utils

object LinkParser {
    // Regex matching vt.tiktok.com, vm.tiktok.com, current www.tiktok.com, or general tiktok link format
    private val TIKTOK_URL_REGEX = """https?://(?:[a-zA-Z0-9-]+\.)?tiktok\.com/[^\s"<>'\\]+""".toRegex()

    /**
     * Extracts all valid TikTok links from raw text, removing unrelated text and duplicate URLs.
     */
    fun extractLinks(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val matches = TIKTOK_URL_REGEX.findAll(text)
        return matches.map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
