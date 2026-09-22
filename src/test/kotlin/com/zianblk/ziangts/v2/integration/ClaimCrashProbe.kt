package com.zianblk.ziangts.v2.integration

import com.zianblk.ziangts.v2.application.TradeEngine
import com.zianblk.ziangts.v2.domain.ProceedsKey
import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

object ClaimCrashProbe {
    val owner: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val root = Path.of(args[0])
        val killAt = TradeStage.valueOf(args[1])
        val market = DurableMarketStore(root.resolve("market-v2.state"))
        if (market.balance(owner, key) == 0L) market.credit(owner, key, 13)
        val economy = DurableFileEconomyPort(root.resolve("economy.state"))
        val pokemon = DurableFilePokemonPort(root.resolve("pokemon.state"))
        DurableTradeJournal(root.resolve("transactions-v2.wal")).use { journal ->
            val engine = TradeEngine(market, pokemon, economy, market, journal, Clock.systemUTC(),
                faultHook = { if (it == killAt) Runtime.getRuntime().halt(29) })
            engine.claim(owner, key)
        }
        error("probe did not halt")
    }
}
