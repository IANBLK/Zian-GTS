package com.zianblk.ziangts.server

import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.data.ListingsData
import com.zianblk.ziangts.history.TransactionHistoryData
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import java.util.UUID

/** One journal per server instance, including successive integrated worlds in the same JVM. */
internal object GtsJournal {
    private var owner: MinecraftServer? = null
    private var journal: TransactionJournal? = null
    private var stopping = false
    private var shutdownCheckpoint: String? = null
    private var startupError: String? = null

    fun started(event: ServerStartedEvent) {
        journal?.close()
        owner = event.server
        journal = null
        stopping = false
        shutdownCheckpoint = null
        startupError = null
        try {
            journal = TransactionJournal(event.server.getWorldPath(LevelResource.ROOT)
                .resolve("ziangts-journal").resolve("transactions.wal"))
            journal!!.orderlyCheckpoint?.let {
                if (net.minecraft.nbt.TagParser.parseTag(it) != checkpointTag(event.server))
                    startupError = "GTS SavedData differs from orderly shutdown checkpoint; inspect journal before trading"
            }
            if (journal!!.blocksTrading()) ZianGts.LOGGER.error(
                "GTS journal requires recovery: {} entries; error={}", journal!!.entries().size, journal!!.fault)
            startupError?.let { ZianGts.LOGGER.error(it) }
        } catch (error: Exception) {
            startupError = error.message ?: "initialization failed"
            ZianGts.LOGGER.error("GTS journal unavailable; trading blocked", error)
        }
    }

    fun requireTrading(server: MinecraftServer) {
        if (owner !== server || stopping || startupError != null || journal == null || journal!!.blocksTrading())
            throw GtsException("command.ziangts.journal_blocked")
    }

    fun begin(player: ServerPlayer, operation: String, snapshot: CompoundTag): UUID = io {
        requireTrading(player.server)
        journal!!.begin(operation, player.uuid, snapshot.toString(), checkpointTag(player.server).toString())
    }

    fun stage(id: UUID, stage: String) = io { checkNotNull(journal).stage(id, stage) }
    fun finish(player: ServerPlayer, id: UUID) = io {
        // Never certify an operation with an uncertain callback or failed seller credit.
        if (!ListingsData.get(player.serverLevel()).hasUnreadableData()) {
            checkNotNull(journal).finish(id, checkpointTag(player.server).toString())
            player.server.overworld().dataStorage.save()
        }
    }

    fun entries(server: MinecraftServer): List<TransactionJournal.Pending> =
        if (owner === server) journal?.entries().orEmpty() else emptyList()

    fun status(server: MinecraftServer): String = when {
        owner !== server -> "unavailable"
        startupError != null -> startupError!!
        journal?.fault != null -> journal!!.fault!!
        stopping -> "stopping"
        journal == null -> "unavailable"
        journal!!.blocksTrading() -> "reconciliation_required"
        else -> "ready"
    }

    fun resolve(server: MinecraftServer, id: UUID, name: String, actor: UUID?): Boolean = io {
        check(owner === server && !stopping && startupError == null)
        checkNotNull(journal).resolve(id, name, actor)
    }

    fun stopping(event: ServerStoppingEvent) {
        if (owner !== event.server) return
        stopping = true
        try {
            // LOWEST priority: run after ordinary mod stop listeners, never before closing trading.
            if (startupError == null && journal?.blocksTrading() == false &&
                !ListingsData.get(event.server.overworld()).hasUnreadableData()) {
                shutdownCheckpoint = checkpointTag(event.server).toString()
                event.server.overworld().dataStorage.save()
            }
        } catch (error: Exception) {
            shutdownCheckpoint = null
            ZianGts.LOGGER.error("GTS could not prepare orderly shutdown; journal retained", error)
        }
    }

    fun stopped(event: ServerStoppedEvent) {
        if (owner !== event.server) return
        try {
            // A crash may fire Stopped without Stopping. That must not clear uncertain operations.
            if (stopping) shutdownCheckpoint?.let { journal?.orderlyShutdown(it) }
        } catch (error: Exception) {
            ZianGts.LOGGER.error("GTS orderly shutdown marker failed; journal retained", error)
        } finally {
            try { journal?.close() } finally { journal = null; owner = null }
        }
    }

    private fun checkpointTag(server: MinecraftServer) = CompoundTag().apply {
        val level = server.overworld()
        put("listings", ListingsData.get(level).save(CompoundTag(), level.registryAccess()))
        put("history", TransactionHistoryData.get(level).save(CompoundTag(), level.registryAccess()))
    }

    private fun <T> io(action: () -> T): T = try { action() } catch (error: GtsException) {
        throw error
    } catch (error: Exception) {
        startupError = error.message ?: "journal I/O failed"
        ZianGts.LOGGER.error("GTS journal operation failed; trading blocked", error)
        throw GtsException("command.ziangts.journal_blocked")
    }
}
