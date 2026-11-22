package com.zianblk.ziangts.v2.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegendarySpeciesTest {
    @Test fun acceptsNamespacedAndBareIds() {
        assertTrue(isLegendarySpecies("cobblemon:mewtwo"))
        assertTrue(isLegendarySpecies("ho_oh"))
        assertTrue(isLegendarySpecies("cobblemon:pecharunt"))
    }

    @Test fun rejectsRegularSpecies() {
        assertFalse(isLegendarySpecies("cobblemon:pikachu"))
        assertFalse(isLegendarySpecies("cobblemon:charizard"))
    }
}
