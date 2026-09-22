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
            PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{x:1}"),
            Instant.parse("2026-09-22T16:00:00Z"), Instant.parse("2026-09-24T16:00:00Z"))

        DurableMarketStore(path).apply { add(offer); credit(seller, key, 13) }
        val reopened = DurableMarketStore(path)

        assertEquals(offer, reopened.find(offer.id))
        assertEquals(13, reopened.balance(seller, key))
    }

    @Test fun corruptHeaderFailsClosed() {
        val path = dir.resolve("market-v2.state")
        java.nio.file.Files.writeString(path, "NOT_ZIAN_GTS")
        assertThrows(IllegalArgumentException::class.java) { DurableMarketStore(path) }
    }
}
