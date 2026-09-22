package com.zianblk.ziangts.v2.runtime

import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.v2.application.TradeEngine
import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableHistoryStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.domain.MarketQuery
import com.zianblk.ziangts.v2.domain.MarketView
import com.zianblk.ziangts.v2.domain.query
import com.zianblk.ziangts.v2.port.TradeHistoryRecord
import com.zianblk.ziangts.v2.domain.ProceedsKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.time.Clock

/**
 * Server-scoped V2 composition root.
 *
 * No trading is exposed when durable state cannot be opened or when the journal
 * contains unresolved evidence from a previous process.
 */
object ZianGtsV2Runtime {
    data class Context(
        val market: DurableMarketStore,
        val journal: DurableTradeJournal,
        val engine: TradeEngine,
        val history: DurableHistoryStore
    )

    @Volatile private var context: Context? = null
    @Volatile private var blockedJournal: DurableTradeJournal? = null
    @Volatile private var blockedReason: String? = "V2 runtime not started"

    @Synchronized
    fun start(server: MinecraftServer) {
        if (context != null) return
        check(server.isSameThread) { "Zian GTS V2 runtime must start on the server thread" }
        val root = server.getWorldPath(LevelResource.ROOT).resolve("ziangts-v2")
        try {
            val market = DurableMarketStore(root.resolve("market-v2.state"))
            val journal = DurableTradeJournal(root.resolve("transactions-v2.wal"))
            if (journal.blocksTrading()) {
                blockedJournal = journal
                blockedReason = "unresolved transaction journal"
                ZianGts.LOGGER.error(
                    "Zian GTS V2 trading blocked: {} unresolved transaction(s). Inspect recovery evidence before resolving.",
                    journal.unresolved().size
                )
                return
            }
            val pokemon = CobblemonPokemonPort(server)
            val economy = AvecoinsEconomyPort()
            val history = DurableHistoryStore(root.resolve("history-v2.wal"))
            val engine = TradeEngine(market, pokemon, economy, market, journal, Clock.systemUTC(), history)
            context = Context(market, journal, engine, history)
            blockedJournal = null
            blockedReason = null
            ZianGts.LOGGER.info("Zian GTS V2 runtime ready at {}", root)
        } catch (error: Exception) {
            context = null
            blockedReason = "runtime initialization failed: ${error.javaClass.simpleName}"
            ZianGts.LOGGER.error("Zian GTS V2 failed closed during startup; trading remains unavailable.", error)
        }
    }

    @Synchronized
    fun stop(server: MinecraftServer) {
        check(server.isSameThread) { "Zian GTS V2 runtime must stop on the server thread" }
        val current = context
        val blocked = blockedJournal
        context = null
        blockedJournal = null
        blockedReason = "V2 runtime stopped"
        if (current != null) {
            try {
                current.history.close()
                current.journal.close()
                ZianGts.LOGGER.info("Zian GTS V2 runtime closed cleanly")
            } catch (error: Exception) {
                ZianGts.LOGGER.error("Zian GTS V2 journal close failed", error)
            }
        }
        if (blocked != null) {
            try { blocked.close() } catch (error: Exception) {
                ZianGts.LOGGER.error("Zian GTS V2 blocked journal close failed", error)
            }
        }
    }

    fun engineOrNull(): TradeEngine? = context?.engine

    /**
     * Read-only market projection for commands/network/UI.
     * Returning null instead of opening storage separately preserves the single runtime owner.
     */
    fun marketView(query: MarketQuery): MarketView? = context?.market?.query(query)

    /** Completed trades involving this player, newest first. */
    fun historyFor(playerId: java.util.UUID, limit: Int = 50): List<TradeHistoryRecord>? {
        require(limit in 1..500) { "history limit must be between 1 and 500" }
        val history = context?.history ?: return null
        return history.all().asSequence()
            .filter { it.sellerId == playerId || it.buyerId == playerId }
            .sortedByDescending { it.completedAt }
            .take(limit)
            .toList()
    }

    /** Read-only pending seller proceeds for UI/commands. */
    fun proceedsFor(playerId: java.util.UUID): Map<ProceedsKey, Long>? =
        context?.market?.balances(playerId)
    fun unresolvedTransactions(): List<DurableTradeJournal.Pending> =
        (context?.journal ?: blockedJournal)?.unresolved() ?: emptyList()

    @Synchronized
    fun resolveBlockedTransaction(operationId: java.util.UUID, note: String): Boolean {
        check(context == null) { "recovery resolution is only allowed while V2 trading is blocked" }
        val journal = blockedJournal ?: return false
        if (journal.unresolved().none { it.ticket.operationId == operationId }) return false
        journal.resolve(operationId, note)
        blockedReason = if (journal.blocksTrading()) "unresolved transaction journal"
        else "recovery resolved; restart server to reopen V2 trading"
        return true
    }

    fun isReady(): Boolean = context != null
    fun blockedReason(): String? = blockedReason
}
