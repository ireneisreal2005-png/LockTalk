package com.proj.locktalk

object ContentFilter {
    private val explicitWords = listOf(
        "fuck", "shit", "ass", "bitch", "damn", "hell", "crap", "piss", "dick",
        "pussy", "bastard", "slut", "whore", "etx"
    )

    // Keywords related to nudity/adult content
    private val nudityWords = listOf(
        "nude", "naked", "porn", "xxx", "sex", "erotic", "hentai", "strip"
    )

    // Keywords related to self-harm/suicide
    private val suicidalWords = listOf(
        "suicide" ,"die", "kill myself", "end my life", "self harm", "cut myself",
        "want to die", "commit suicide", "hanging myself"
    )
    private val urlPattern = Regex(
        "(https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/[\\w-./?%&=]*)?",
        RegexOption.IGNORE_CASE
    )

    fun isExplicit(text: String): Boolean {
        return hasKeywords(text, explicitWords) ||
                hasKeywords(text, nudityWords) ||
                hasKeywords(text, suicidalWords) ||
                urlPattern.containsMatchIn(text)
    }

    fun containsSuicidalContent(text: String): Boolean {
        return hasKeywords(text, suicidalWords)
    }

    fun containsLinks(text: String): Boolean {
        return urlPattern.containsMatchIn(text)
    }

    fun filterText(text: String): String {
        var result = text
        val allRestrictedWords = (explicitWords + nudityWords + suicidalWords).sortedByDescending { it.length }

        allRestrictedWords.forEach { word ->
            val pattern = word.map { Regex.escape(it.toString()) }.joinToString("[^a-z0-9]*")
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            result = regex.replace(result) { match ->
                "*".repeat(match.value.length)
            }
        }
        result = urlPattern.replace(result) { match ->
            "[LINK REMOVED]"
        }

        return result
    }

    private fun hasKeywords(text: String, keywords: List<String>): Boolean {
        val lowerText = text.lowercase()
        return keywords.any { word ->
            val pattern = word.map { Regex.escape(it.toString()) }.joinToString("[^a-z0-9]*")
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(lowerText)
        }
    }
}
