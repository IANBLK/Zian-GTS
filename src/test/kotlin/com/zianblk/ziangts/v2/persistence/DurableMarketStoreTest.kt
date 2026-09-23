package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class DurableMarketStoreTest {
    @TempDir lateinit var dir: Path

    @Test fun offersAndProceedsSurviveReopen() {
        val path = dir.resolve("market-v2.state")
        val seller = UUID.randomUUID()
        val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"),
            PaymentSpec(key.adapter, key.currency, 8),
            PokemonEnvelope(
                UUID.randomUUID(), "cobblemon:gimmighoul", 17, true, true,
                gender = "MALE", nature = "cobblemon:timid", ability = "cobblemon:levitate",
                ivs = listOf(31, 30, 29, 28, 27, 26),
                moves = listOf("cobblemon:tackle", "cobblemon:protect"),
                serialized = "{x:1}", legendary = true
            ),
            Instant.parse("2026-09-22T16:00:00Z"), Instant.parse("2026-09-24T16:00:00Z"))

        DurableMarketStore(path).apply { add(offer); credit(seller, key, 13) }
        val reopened = DurableMarketStore(path)

        assertEquals(offer, reopened.find(offer.id))
        assertEquals(13, reopened.balance(seller, key))
    }

    @Test fun legacyV1OfferStillLoadsWithSafeDefaults() {
        val path = dir.resolve("legacy-market-v1.state")
        val offerId = UUID.randomUUID()
        val seller = UUID.randomUUID()
        val pokemon = UUID.randomUUID()
        val sellerName = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("Seller".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{legacy:1}".toByteArray())
        java.nio.file.Files.writeString(path, "ZIANGTS_V2|1\nO|$offerId|$seller|$sellerName|avecoins_wallet|avecoins:coppercoin|5|$pokemon|cobblemon:arceus|1|true|false|$payload|1780000000000|1880000000000\n")
        val loaded = DurableMarketStore(path).find(OfferId(offerId))!!
        assertEquals("{legacy:1}", loaded.pokemon.serialized)
        assertEquals("UNKNOWN", loaded.pokemon.gender)
        assertEquals(List(6) { 0 }, loaded.pokemon.ivs)
        assertTrue(loaded.pokemon.legendary)
    }

    @Test fun corruptHeaderFailsClosed() {
        val path = dir.resolve("market-v2.state")
        java.nio.file.Files.writeString(path, "NOT_ZIAN_GTS")
        assertThrows(IllegalArgumentException::class.java) { DurableMarketStore(path) }
    }
}
