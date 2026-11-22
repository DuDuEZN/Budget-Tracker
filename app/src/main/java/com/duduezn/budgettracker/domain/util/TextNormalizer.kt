package com.duduezn.budgettracker.domain.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation partagée par le parseur et le moteur de catégorisation.
 *
 * Les SMS béninois mélangent majuscules, accents, espaces insécables et
 * abréviations. On travaille donc toujours sur une forme sans accent,
 * en minuscules et avec des espaces simples.
 */
object TextNormalizer {

    private val DIACRITICS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")

    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.FRENCH)
            .replace(WHITESPACE, " ")
            .trim()

    /** Vérifie la présence d'un mot ou d'une expression entière. */
    fun containsWord(text: String, word: String): Boolean {
        val normalizedWord = normalize(word)
        if (normalizedWord.isEmpty()) return false
        return Regex("(?:^|[^a-z0-9])${Regex.escape(normalizedWord)}(?:[^a-z0-9]|$)")
            .containsMatchIn(text)
    }

    /** Découpe un texte normalisé en mots exploitables par le dictionnaire. */
    fun tokens(text: String): List<String> =
        normalize(text)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
}
