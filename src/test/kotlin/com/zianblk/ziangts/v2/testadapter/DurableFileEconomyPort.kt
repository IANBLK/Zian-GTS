package com.zianblk.ziangts.v2.testadapter

import com.zianblk.ziangts.v2.port.EconomyPort
import com.zianblk.ziangts.v2.port.EconomyResult
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.UUID

/**
 * Durable test adapter. Each successful mutation is written and fsync'd before
 * returning, so a child JVM may be halted immediately after the call.
 */
class DurableFileEconomyPort(private val file: Path) : EconomyPort {
    private val balances = linkedMapOf<Pair<UUID, String>, Long>()

    init { load() }

    fun setBalance(playerId: UUID, currency: String, amount: Long) {
        require(amount >= 0)
        balances[playerId to currency] = amount
        persist()
    }

    fun balance(playerId: UUID, currency: String): Long = balances[playerId to currency] ?: 0

    override fun canWithdraw(playerId: UUID, currency: String, amount: Long) =
        amount > 0 && balance(playerId, currency) >= amount

    override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
        if (!canWithdraw(playerId, currency, amount)) return EconomyResult.Rejected("insufficient funds")
        balances[playerId to currency] = balance(playerId, currency) - amount
        persist()
        return EconomyResult.Applied
    }

    override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
        if (amount <= 0) return EconomyResult.Rejected("amount must be positive")
        balances[playerId to currency] = Math.addExact(balance(playerId, currency), amount)
        persist()
        return EconomyResult.Applied
    }

    private fun load() {
        if (!Files.exists(file)) return
        Files.readAllLines(file, StandardCharsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('|')
            require(p.size == 3)
            balances[UUID.fromString(p[0]) to p[1]] = p[2].toLong()
        }
    }

    private fun persist() {
        Files.createDirectories(file.parent)
        val text = balances.entries.joinToString("\n") { (key, amount) ->
            "${key.first}|${key.second}|$amount"
        } + if (balances.isEmpty()) "" else "\n"
        FileChannel.open(file, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
            val bytes = StandardCharsets.UTF_8.encode(text)
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
    }
}
