package ai.octomil.app.keyboard

import com.arm.aichat.InferenceEngine.Prediction

/**
 * Converts raw subword token predictions from the LLM into usable word suggestions.
 *
 * LLMs predict subword tokens (e.g. "Ġhello", "##ing") — this filter strips
 * leading-space markers, drops punctuation-only tokens, and deduplicates.
 */
object TokenSuggestionFilter {

    fun process(rawTokens: List<Prediction>): List<String> {
        return rawTokens
            .map { it.text.trimStart('\u0120', ' ') }  // strip Ġ and leading spaces
            .filter { it.length > 1 }
            .filter { it.first().isLetter() }
            .distinct()
            .take(3)
    }
}
