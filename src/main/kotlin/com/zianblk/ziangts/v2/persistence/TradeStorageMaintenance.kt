package com.zianblk.ziangts.v2.persistence

import java.nio.channels.FileChannel
import java.nio.file.*
import com.zianblk.ziangts.storage.StorageFiles as Files
import java.util.UUID

/** Offline, only fully resolved transactions. Market, proceeds and history are never changed. */
object TradeStorageMaintenance {
    data class Result(val beforeBytes:Long,val archive:Path)
    @JvmStatic fun compactClosedOffline(input:Path):Result {
        val path=input.toAbsolutePath().normalize()
        require(Files.isRegularFile(path) && !Files.isSymbolicLink(path))
        val parent=path.parent;val token=UUID.randomUUID().toString()
        val archive=parent.resolve("transactions.archive-$token.wal.gz")
        val temp=parent.resolve("transactions.compact-$token.wal")
        val marker=path.resolveSibling(path.fileName.toString()+".maintenance")
        var owned=false;val before=Files.size(path)
        try {
            DurableTradeJournal(path).use { journal ->
                check(!journal.blocksTrading()) { "pending/quarantined transactions require review; original preserved" }
                Files.writeString(marker,"Offline closed-trade archive; archive=${archive.fileName}",StandardOpenOption.CREATE_NEW)
                owned=true;Files.channel(marker,StandardOpenOption.WRITE).use { it.force(true) };forceDirectory(parent)
                journal.archiveClosed(archive);forceDirectory(parent)
                Files.channel(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use { it.force(true) }
                journal.close()
                Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                forceDirectory(parent)
            }
        } finally {
            Files.deleteIfExists(temp)
            if(owned){Files.deleteIfExists(marker);forceDirectory(parent)}
        }
        return Result(before,archive)
    }
    private fun forceDirectory(path:Path) {
        if(!System.getProperty("os.name").lowercase(java.util.Locale.ROOT).contains("windows"))
            Files.channel(path,StandardOpenOption.READ).use { it.force(true) }
    }
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2 && args[0]=="compact-closed-offline") { "Usage: compact-closed-offline <transactions-v2.wal>; stop server and disable restart first" }
        println(compactClosedOffline(Path.of(args[1])))
    }
}
