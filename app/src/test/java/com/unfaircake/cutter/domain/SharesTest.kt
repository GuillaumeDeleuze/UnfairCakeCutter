package com.unfaircake.cutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

class SharesTest {

    private val random = Random(42)

    @Test
    fun sharesSumToOne() {
        for (n in MIN_PEOPLE..MAX_PEOPLE) {
            val seeds = Shares.newSeeds(n, random)
            for (u in 0..100 step 5) {
                val shares = Shares.compute(seeds, u)
                assertEquals(n, shares.size)
                assertEquals("n=$n u=$u", 1.0, shares.sum(), 1e-12)
                assertTrue(shares.all { it > 0.0 })
            }
        }
    }

    @Test
    fun zeroUnfairnessGivesEqualSlices() {
        for (n in MIN_PEOPLE..MAX_PEOPLE) {
            val shares = Shares.compute(Shares.newSeeds(n, random), 0)
            shares.forEach { assertEquals(1.0 / n, it, 1e-12) }
            assertTrue(Shares.isEveryoneTheSame(shares))
        }
    }

    @Test
    fun maxMinRatioGrowsWithUnfairness() {
        repeat(50) {
            val seeds = Shares.newSeeds(6, random)
            var previous = Shares.maxMinRatio(Shares.compute(seeds, 0))
            assertEquals(1.0, previous, 1e-12)
            for (u in 1..100) {
                val ratio = Shares.maxMinRatio(Shares.compute(seeds, u))
                assertTrue("u=$u ratio=$ratio previous=$previous", ratio > previous)
                previous = ratio
            }
        }
    }

    @Test
    fun ratioMatchesClosedForm() {
        val seeds = listOf(-1.0, -0.2, 0.3, 1.0)
        val ratio = Shares.maxMinRatio(Shares.compute(seeds, 100))
        // k(100) = 1.8, ratio = exp(1.8 * (1 - (-1)))
        assertEquals(kotlin.math.exp(3.6), ratio, 1e-9)
        assertEquals(1.8, Shares.biasStrength(100), 1e-12)
        assertEquals(1.8 * Math.pow(0.4, 1.2), Shares.biasStrength(40), 1e-12)
    }

    @Test
    fun seedsAreInRange() {
        Shares.newSeeds(1000, random).forEach { assertTrue(it in -1.0..1.0) }
    }

    @Test
    fun ensureSeedsKeepsExistingAndAppends() {
        val base = listOf(0.1, -0.5, 0.9)
        assertEquals(base, Shares.ensureSeeds(base, 2, random))
        assertEquals(base, Shares.ensureSeeds(base, 3, random))
        val grown = Shares.ensureSeeds(base, 6, random)
        assertEquals(6, grown.size)
        assertEquals(base, grown.take(3))
    }

    @Test
    fun verdicts() {
        assertEquals(Verdict.PERFECTLY_FAIR, Verdict.of(0))
        assertEquals(Verdict.SLIGHTLY_GENEROUS, Verdict.of(1))
        assertEquals(Verdict.SLIGHTLY_GENEROUS, Verdict.of(24))
        assertEquals(Verdict.SUSPICIOUS, Verdict.of(25))
        assertEquals(Verdict.SUSPICIOUS, Verdict.of(49))
        assertEquals(Verdict.SHAMELESSLY_BIASED, Verdict.of(50))
        assertEquals(Verdict.SHAMELESSLY_BIASED, Verdict.of(74))
        assertEquals(Verdict.CAKE_TYRANNY, Verdict.of(75))
        assertEquals(Verdict.CAKE_TYRANNY, Verdict.of(100))
    }

    @Test
    fun percentFormatting() {
        val us = Locale.US
        assertEquals("7.3%", Shares.formatPercent(0.0731, us))
        assertEquals("0.5%", Shares.formatPercent(0.005, us))
        assertEquals("23%", Shares.formatPercent(0.2349, us))
        assertEquals("10%", Shares.formatPercent(0.0996, us))
        assertEquals("100%", Shares.formatPercent(1.0, us))
        assertEquals("7,3%", Shares.formatPercent(0.0731, Locale.FRANCE))
    }

    @Test
    fun sameThreshold() {
        assertTrue(Shares.isEveryoneTheSame(listOf(0.5, 0.49)))
        assertTrue(!Shares.isEveryoneTheSame(listOf(0.52, 0.48)))
        assertTrue(Shares.isEveryoneTheSame(listOf(1.0)))
    }

    @Test
    fun riggedPersonGetsTheBiggestShare() {
        repeat(200) {
            val n = random.nextInt(2, MAX_PEOPLE + 1)
            val seeds = Shares.newSeeds(n, random)
            val favorite = random.nextInt(n)
            val rigged = Shares.rig(seeds, favorite)
            // Same people, same seeds: only the favourite and the old top swapped.
            assertEquals(seeds.sorted(), rigged.sorted())
            for (u in 5..100 step 5) {
                val shares = Shares.compute(rigged, u)
                assertEquals(shares.max(), shares[favorite], 0.0)
            }
        }
    }

    @Test
    fun rigWithoutFavoriteChangesNothing() {
        val seeds = Shares.newSeeds(5, random)
        assertEquals(seeds, Shares.rig(seeds, -1))
        assertEquals(seeds, Shares.rig(seeds, 5))
        val top = seeds.indices.maxBy { seeds[it] }
        assertEquals(seeds, Shares.rig(seeds, top))
    }
}
