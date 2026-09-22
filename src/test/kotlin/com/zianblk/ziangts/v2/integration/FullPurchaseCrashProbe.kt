package com.zianblk.ziangts.v2.integration

import com.zianblk.ziangts.v2.application.TradeEngine
import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object FullPurchaseCrashProbe {
    val seller: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val buyer: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val pokemonId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    val offerId = OfferId(UUID.fromString("44444444-4444-4444-4444-444444444444"))
    val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val root = Path.of(args[0])
        val killAt = TradeStage.valueOf(args[1])
        val market = DurableMarketStore(root.resolve("market-v2.state"))
        if (market.find(offerId) == null) {
            market.add(TradeOffer(offerId, OfferOwner(seller, "Seller"),
                PaymentSpec(key.adapter, key.currency, 8),
                PokemonEnvelope(pokemonId, "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
                Instant.parse("2026-09-22T16:00:00Z"), Instant.parse("2026-09-24T16:00:00Z")))
        }
        val economy = DurableFileEconomyPort(root.resolve("economy.state")).apply {
            if (balance(buyer, key.currency) == 0L) setBalance(buyer, key.currency, 20)
        }
        val pokemon = DurableFilePokemonPort(root.resolve("pokemon.state"))
        DurableTradeJournal(root.resolve("transactions-v2.wal")).use { journal ->
            val engine = TradeEngine(market, pokemon, economy, market, journal,
                Clock.fixed(Instant.parse("2026-09-22T17:00:00Z"), ZoneOffset.UTC),
                faultHook = { if (it == killAt) Runtime.getRuntime().halt(29) })
            engine.purchase(buyer, offerId)
        }
        error("probe did not halt")
    }
}
