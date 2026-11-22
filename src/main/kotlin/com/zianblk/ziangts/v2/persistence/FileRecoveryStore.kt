package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.port.RecoveryPort
import com.zianblk.ziangts.v2.port.RecoveryRecord
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Writes `<dir>/<ticket>.json` with tmp + atomic move, same pattern as DurableMarketStore. */
class FileRecoveryStore(private val dir: Path) : RecoveryPort {
    override fun dump(record: RecoveryRecord) {
        Files.createDirectories(dir)
        val target = dir.resolve("${record.ticket.operationId}.json")
        val tmp = dir.resolve("${record.ticket.operationId}.json.tmp")
        Files.write(tmp, record.toJson().toByteArray(Charsets.UTF_8))
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
