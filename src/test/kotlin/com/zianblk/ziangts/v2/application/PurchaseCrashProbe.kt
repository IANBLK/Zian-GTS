package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object PurchaseCrashProbe {
    private val seller = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val buyer = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val pokemonId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val offerId = OfferId(UUID.fromString("44444444-4444-4444-4444-444444444444"))

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val root = Path.of(args[0])
        val killAt = TradeStage.valueOf(args[1])
        val economy = DurableFileEconomyPort(root.resolve("economy.state")).apply {
            setBalance(buyer, "avecoins:coppercoin", 20)
        }
        val pokemon = DurableFilePokemonPort(root.resolve("pokemon.state"))
        val offers = InMemoryOfferBook().apply {
            add(TradeOffer(
                offerId, OfferOwner(seller, "Seller"),
                PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8),
                PokemonEnvelope(pokemonId, "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
                Instant.parse("2026-09-22T16:00:00Z"), Instant.parse("2026-09-24T16:00:00Z")
            ))
        }
        val engine = TradeEngine(
            offers, pokemon, economy, InMemoryProceedsStore(), RecordingTradeJournal(),
            Clock.fixed(Instant.parse("2026-09-22T17:00:00Z"), ZoneOffset.UTC),
            faultHook = { stage -> if (stage == killAt) Runtime.getRuntime().halt(29) }
        )
        engine.purchase(buyer, offerId)
        error("probe did not halt")
    }
}
