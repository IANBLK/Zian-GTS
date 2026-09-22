package com.zianblk.ziangts.v2.persistence

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zianblk.ziangts.v2.port.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.UUID

/**
 * Independent V2 append-only WAL adapter.
 * Unresolved entries are evidence: startup must fail closed until reconciled.
 */
class DurableTradeJournal(private val path: Path) : TradeJournalPort, AutoCloseable {
    data class Pending(val ticket: JournalTicket, val subjectId: UUID, val stage: TradeStage, val quarantined: Boolean, val reason: String?)
    private val gson = Gson()
    private val pending = linkedMapOf<UUID, Pending>()
    private var sequence = 0L
    private var hash = ByteArray(32)
    private val channel: FileChannel
    private val lock: java.nio.channels.FileLock

    init {
        Files.createDirectories(path.toAbsolutePath().parent)
        channel = FileChannel.open(path, CREATE, READ, WRITE)
        lock = channel.tryLock() ?: error("V2 trade journal already in use")
        replay()
    }

    fun unresolved(): List<Pending> = pending.values.toList()
    fun blocksTrading(): Boolean = pending.isNotEmpty()

    override fun begin(operation: TradeOperation, subjectId: UUID): JournalTicket {
        check(!blocksTrading()) { "V2 journal requires reconciliation" }
        val ticket = JournalTicket(UUID.randomUUID(), operation)
        append("begin", JsonObject().apply {
            addProperty("id", ticket.operationId.toString())
            addProperty("operation", operation.name)
            addProperty("subject", subjectId.toString())
        })
        return ticket
    }

    override fun stage(ticket: JournalTicket, stage: TradeStage) {
        check(ticket.operationId in pending)
        append("stage", JsonObject().apply {
            addProperty("id", ticket.operationId.toString()); addProperty("stage", stage.name)
        })
    }

    override fun complete(ticket: JournalTicket) {
        check(ticket.operationId in pending)
        append("complete", JsonObject().apply { addProperty("id", ticket.operationId.toString()) })
    }

    override fun abort(ticket: JournalTicket, reason: String) {
        check(ticket.operationId in pending)
        require(reason.isNotBlank()) { "abort reason must not be blank" }
        append("abort", JsonObject().apply {
            addProperty("id", ticket.operationId.toString()); addProperty("reason", reason)
        })
    }

    override fun quarantine(ticket: JournalTicket, reason: String) {
        check(ticket.operationId in pending)
        append("quarantine", JsonObject().apply {
            addProperty("id", ticket.operationId.toString()); addProperty("reason", reason)
        })
    }

    fun resolve(operationId: UUID, note: String) {
        check(operationId in pending)
        require(note.isNotBlank()) { "recovery note must not be blank" }
        append("resolve", JsonObject().apply {
            addProperty("id", operationId.toString()); addProperty("note", note)
        })
    }

    private fun append(kind: String, data: JsonObject) {
        val event = JsonObject().apply {
            addProperty("version", 2); addProperty("sequence", sequence + 1)
            addProperty("kind", kind); add("data", data)
        }
        val bytes = gson.toJson(event).toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME)
        val digest = digest(hash, bytes)
        val frame = ByteBuffer.allocate(4 + bytes.size + 32).putInt(bytes.size).put(bytes).put(digest)
        frame.flip(); channel.position(channel.size())
        while (frame.hasRemaining()) channel.write(frame)
        channel.force(true)
        apply(event); sequence++; hash = digest
    }

    private fun replay() {
        channel.position(0)
        while (channel.position() < channel.size()) {
            val size = ByteBuffer.allocate(4); readFully(size)
            val length = size.flip().int
            require(length in 1..MAX_FRAME) { "invalid V2 journal frame" }
            val payload = ByteBuffer.allocate(length); readFully(payload)
            val stored = ByteBuffer.allocate(32); readFully(stored)
            val next = digest(hash, payload.array())
            require(MessageDigest.isEqual(stored.array(), next)) { "V2 journal checksum mismatch" }
            val event = gson.fromJson(String(payload.array(), Charsets.UTF_8), JsonObject::class.java)
            require(event["version"].asInt == 2 && event["sequence"].asLong == sequence + 1)
            apply(event); sequence++; hash = next
        }
    }

    private fun apply(event: JsonObject) {
        val d = event.getAsJsonObject("data")
        when (event["kind"].asString) {
            "begin" -> {
                val id = UUID.fromString(d["id"].asString)
                val ticket = JournalTicket(id, TradeOperation.valueOf(d["operation"].asString))
                pending[id] = Pending(ticket, UUID.fromString(d["subject"].asString), TradeStage.PREPARED, false, null)
            }
            "stage" -> {
                val id = UUID.fromString(d["id"].asString); val old = pending.getValue(id)
                pending[id] = old.copy(stage = TradeStage.valueOf(d["stage"].asString))
            }
            "quarantine" -> {
                val id = UUID.fromString(d["id"].asString); val old = pending.getValue(id)
                pending[id] = old.copy(quarantined = true, reason = d["reason"].asString)
            }
            "complete", "abort", "resolve" -> require(pending.remove(UUID.fromString(d["id"].asString)) != null)
            else -> error("unknown V2 journal event")
        }
    }

    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) require(channel.read(buffer) > 0) { "truncated V2 journal" }
    }

    override fun close() { try { lock.release() } finally { channel.close() } }

    companion object {
        private const val MAX_FRAME = 8 * 1024 * 1024
        private fun digest(previous: ByteArray, bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").run { update(previous); digest(bytes) }
    }
}
