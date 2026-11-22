package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlphaDetectionTest {
    @Test fun nativeAlphaFlagIsEnough() = assertTrue(isAlphaPokemon(true, null, emptyList()))

    @Test fun ownedOrActiveAlphaMarkKeepsLegacyDataAsAlpha() {
        assertTrue(isAlphaPokemon(false, "cobblemon:mark_alpha", emptyList()))
        assertTrue(isAlphaPokemon(false, null, listOf("cobblemon:mark_lunchtime", "cobblemon:mark_alpha")))
    }

    @Test fun otherMarksOrNothingAreNotAlpha() {
        assertFalse(isAlphaPokemon(false, null, emptyList()))
        assertFalse(isAlphaPokemon(false, "cobblemon:mark_lunchtime", listOf("cobblemon:mark_rare")))
    }
}
