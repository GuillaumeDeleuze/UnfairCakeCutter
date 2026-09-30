package com.unfaircake.cutter.domain

import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

const val MIN_PEOPLE = 1
const val MAX_PEOPLE = 12
const val DEFAULT_PEOPLE = 6
const val DEFAULT_UNFAIRNESS = 40

/** Below this max/min ratio the cut is treated as equal. */
const val SAME_RATIO_THRESHOLD = 1.05

/**
 * Pure share maths. No Android types, so it runs in plain JVM unit tests.
 */
object Shares {

    /** Bias strength for an unfairness level u in [0, 100]: k = 1.8 × (u/100)^1.2. */
    fun biasStrength(unfairness: Int): Double {
        val u = unfairness.coerceIn(0, 100) / 100.0
        return 1.8 * u.pow(1.2)
    }

    /**
     * share_i = exp(k·z_i) / Σ exp(k·z_j).
     * [seeds] holds one z in [-1, 1] per person; the result has the same size and sums to 1.
     */
    fun compute(seeds: List<Double>, unfairness: Int): List<Double> {
        if (seeds.isEmpty()) return emptyList()
        val k = biasStrength(unfairness)
        val weights = seeds.map { exp(k * it.coerceIn(-1.0, 1.0)) }
        val total = weights.sum()
        return weights.map { it / total }
    }

    /**
     * The same seeds with the largest one handed to [favorite] (swapped with whoever had it), so
     * that person gets the biggest share at any unfairness above 0 and nobody else's slice looks
     * out of place. Out of range [favorite] (e.g. -1): unchanged.
     */
    fun rig(seeds: List<Double>, favorite: Int): List<Double> {
        if (favorite !in seeds.indices) return seeds
        val top = seeds.indices.maxBy { seeds[it] }
        if (top == favorite) return seeds
        return seeds.toMutableList().also {
            it[favorite] = seeds[top]
            it[top] = seeds[favorite]
        }
    }

    /** One fresh seed in [-1, 1]. */
    fun newSeed(random: Random): Double = random.nextDouble(-1.0, 1.0)

    /** [count] fresh seeds (used by Reroll). */
    fun newSeeds(count: Int, random: Random): List<Double> = List(count) { newSeed(random) }

    /**
     * Makes sure there are at least [count] seeds. Existing seeds are never touched, so shrinking
     * and growing back restores the same people, and growing only appends new random seeds.
     */
    fun ensureSeeds(existing: List<Double>, count: Int, random: Random): List<Double> =
        if (existing.size >= count) existing
        else existing + List(count - existing.size) { newSeed(random) }

    /** Largest share divided by the smallest; 1.0 for zero or one person. */
    fun maxMinRatio(shares: List<Double>): Double {
        if (shares.size < 2) return 1.0
        val min = shares.min()
        return if (min <= 0.0) Double.POSITIVE_INFINITY else shares.max() / min
    }

    fun isEveryoneTheSame(shares: List<Double>): Boolean = maxMinRatio(shares) < SAME_RATIO_THRESHOLD

    /**
     * "7.3%" under 10 %, "23%" from 10 % up. A value that would print as "10.0%" is shown as "10%".
     */
    fun formatPercent(share: Double, locale: Locale = Locale.getDefault()): String {
        val percent = share * 100.0
        val oneDecimal = (percent * 10.0).roundToInt() / 10.0
        return if (oneDecimal < 10.0) {
            String.format(locale, "%.1f%%", oneDecimal)
        } else {
            String.format(locale, "%d%%", percent.roundToInt())
        }
    }

    /** "3.4" style ratio for the "Biggest slice is X× the smallest" line. */
    fun formatRatio(ratio: Double, locale: Locale = Locale.getDefault()): String =
        if (ratio >= 10.0) String.format(locale, "%d", ratio.roundToInt())
        else String.format(locale, "%.1f", ratio)
}

enum class Verdict { PERFECTLY_FAIR, SLIGHTLY_GENEROUS, SUSPICIOUS, SHAMELESSLY_BIASED, CAKE_TYRANNY;

    companion object {
        fun of(unfairness: Int): Verdict = when {
            unfairness <= 0 -> PERFECTLY_FAIR
            unfairness < 25 -> SLIGHTLY_GENEROUS
            unfairness < 50 -> SUSPICIOUS
            unfairness < 75 -> SHAMELESSLY_BIASED
            else -> CAKE_TYRANNY
        }
    }
}
