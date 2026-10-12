package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.storage.StorageFiles as Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.StandardCopyOption.*
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Only successful old trade details are pruned; deduplication IDs remain permanent. */
object HistoryRetention {
    data class Result(val retained: Long, val eligible: Long, val applied: Boolean)
    @JvmStatic fun pruneOffline(worldInput: Path, apply: Boolean): Result {
        val world=worldInput.toAbsolutePath().normalize()
        val config=Properties()
        java.nio.file.Files.newBufferedReader(world.resolve("zian-storage/storage.properties")).use(config::load)
        check(config.getProperty("gts")=="sqlite-v1")
        val days=config.getProperty("gts.history-days",config.getProperty("history-days","90")).toInt()
        val inactive=config.getProperty("gts.inactive-days",config.getProperty("inactive-days","30")).toInt()
        require(days in 7..3650 && inactive in 1..3650)
        val now=System.currentTimeMillis()
        val cutoff=now-days*86400000L;val inactiveCutoff=now-inactive*86400000L
        val path=world.resolve("ziangts-v2/history-v2.wal")
        var retained=0L;var eligible=0L
        FileChannel.open(world.resolve("session.lock"),CREATE,WRITE).use { session ->
            val lock=session.tryLock()?:error("Stop Minecraft before cleanup")
            lock.use {
                try {
                    DurableTradeJournal(world.resolve("ziangts-v2/transactions-v2.wal")).use {
                        check(!it.blocksTrading()) { "Unresolved transactions protect all history from cleanup" }
                    }
                    val market=DurableMarketStore(world.resolve("ziangts-v2/market-v2.state"))
                    DurableHistoryStore(path).use { original ->
                        check(original.canCompact()) { "Unknown history fields; cleanup refused" }
                        val temp=path.parent.resolve("history-rebuild-${UUID.randomUUID()}.tmp")
                        val archive=path.parent.resolve("history-retention.archive-${UUID.randomUUID()}.wal.gz")
                        val expected=MessageDigest.getInstance("SHA-256")
                        val ids=ArrayList<UUID>()
                        val target=if(apply)DurableHistoryStore(temp) else null
                        try {
                            original.visit { record ->
                                val removable=record.completedAt.toEpochMilli()<cutoff &&
                                    Files.knownInactive(path,record.sellerId,inactiveCutoff) &&
                                    Files.knownInactive(path,record.buyerId,inactiveCutoff) &&
                                    market.balances(record.sellerId).values.none { it>0L }
                                ids.add(record.operationId)
                                if(removable)eligible++ else {
                                    retained++;expected.update(record.toString().toByteArray(Charsets.UTF_8))
                                    target?.append(record)
                                }
                            }
                        } finally { target?.close() }
                        if(apply && eligible>0) {
                            DurableHistoryStore(temp).use { staged ->
                                val actual=MessageDigest.getInstance("SHA-256");var count=0L
                                staged.visit { actual.update(it.toString().toByteArray(Charsets.UTF_8));count++ }
                                check(count==retained && MessageDigest.isEqual(expected.digest(),actual.digest())) { "Retained history mismatch" }
                            }
                            // Commit IDs before removing details. A crash may keep extra deduplication IDs,
                            // but can never remove the receipt protecting a previously completed operation.
                            Files.preserveReceipts(path,"gts-history",ids)
                            original.archiveCompressed(archive)
                            original.close()
                            Files.move(temp,path,ATOMIC_MOVE,REPLACE_EXISTING)
                        } else if(apply)Files.deleteIfExists(temp)
                    }
                } finally { Files.closeAll() }
            }
        }
        return Result(retained,eligible,apply)
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==2 && args[0] in listOf("preview","apply"))
        println(pruneOffline(Path.of(args[1]),args[0]=="apply"))
    }
}
