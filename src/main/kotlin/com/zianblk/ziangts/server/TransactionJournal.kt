package com.zianblk.ziangts.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.UUID

/** Append-only, checksummed write-ahead evidence. Runtime completion is NOT durable external commit. */
internal class TransactionJournal(private val path: Path) : AutoCloseable {
    data class Pending(val id: UUID, val operation: String, val actor: UUID, val snapshot: String,
                       val stage: String, val runtimeComplete: Boolean = false)
    private val gson = Gson()
    private val pending = linkedMapOf<UUID, Pending>()
    private val inherited = mutableSetOf<UUID>()
    private var sequence = 0L
    private var hash = ByteArray(32)
    private var channel: FileChannel? = null
    private var lock: java.nio.channels.FileLock? = null
    var fault: String? = null
        private set
    var latestCheckpoint: String? = null
        private set
    var orderlyCheckpoint: String? = null
        private set

    init {
        try {
            val directory = path.toAbsolutePath().parent
            Files.createDirectories(directory)
            FileChannel.open(directory.parent, READ).use { it.force(true) }
            channel = FileChannel.open(path, CREATE, READ, WRITE)
            lock = channel!!.tryLock() ?: error("Journal is already in use")
            FileChannel.open(directory, READ).use { it.force(true) }
            replay()
            inherited.addAll(pending.keys)
        } catch (error: Exception) {
            fault = error.message ?: error.javaClass.simpleName
        }
    }

    fun entries(): List<Pending> = pending.values.toList()
    fun blocksTrading(): Boolean = fault != null || inherited.isNotEmpty() || pending.values.any { !it.runtimeComplete }

    fun begin(operation: String, actor: UUID, snapshot: String, checkpointBefore: String? = null): UUID {
        check(!blocksTrading()) { "Journal requires reconciliation" }
        val id = UUID.randomUUID()
        append("begin", JsonObject().apply {
            addProperty("id", id.toString()); addProperty("operation", operation)
            addProperty("actor", actor.toString()); addProperty("snapshot", snapshot)
            checkpointBefore?.let { addProperty("checkpointBefore", it) }
        })
        return id
    }

    fun stage(id: UUID, stage: String) {
        check(pending.containsKey(id))
        append("stage", JsonObject().apply { addProperty("id", id.toString()); addProperty("stage", stage) })
    }

    fun finish(id: UUID, checkpoint: String) {
        check(pending.containsKey(id))
        append("finish", JsonObject().apply { addProperty("id", id.toString()); addProperty("checkpoint", checkpoint) })
    }

    fun resolve(id: UUID, administrator: String, administratorId: UUID?): Boolean {
        if (!pending.containsKey(id)) return false
        append("resolve", JsonObject().apply {
            addProperty("id", id.toString()); addProperty("administrator", administrator)
            administratorId?.let { addProperty("administratorId", it.toString()) }
        })
        return true
    }

    /** Called only after both normal stopping and stopped events; never from finally after an operation. */
    fun orderlyShutdown(checkpoint: String) {
        check(!blocksTrading()) { "Unresolved operations cannot be cleared by shutdown" }
        append("orderly_shutdown", JsonObject().apply { addProperty("checkpoint", checkpoint) })
    }

    private fun append(kind: String, data: JsonObject) {
        check(fault == null) { "Journal unavailable: $fault" }
        try {
            val event = JsonObject().apply {
                addProperty("version", 1); addProperty("sequence", sequence + 1)
                addProperty("kind", kind); addProperty("time", System.currentTimeMillis()); add("data", data)
            }
            val bytes = gson.toJson(event).toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..MAX_FRAME) { "Journal frame too large" }
            val digest = digest(hash, bytes)
            val frame = ByteBuffer.allocate(4 + bytes.size + 32).putInt(bytes.size).put(bytes).put(digest)
            frame.flip()
            val file = checkNotNull(channel)
            file.position(file.size())
            while (frame.hasRemaining()) file.write(frame)
            file.force(true) // No external mutation is allowed until this returns.
            apply(event)
            sequence++
            hash = digest
        } catch (error: Exception) {
            fault = error.message ?: error.javaClass.simpleName
            throw error
        }
    }

    private fun replay() {
        val file = checkNotNull(channel)
        file.position(0)
        while (file.position() < file.size()) {
            val size = ByteBuffer.allocate(4)
            readFully(file, size)
            val length = size.flip().int
            require(length in 1..MAX_FRAME) { "Invalid journal frame length" }
            val payload = ByteBuffer.allocate(length)
            readFully(file, payload)
            val storedHash = ByteBuffer.allocate(32)
            readFully(file, storedHash)
            val nextHash = digest(hash, payload.array())
            require(MessageDigest.isEqual(storedHash.array(), nextHash)) { "Journal checksum mismatch" }
            val event = gson.fromJson(String(payload.array(), Charsets.UTF_8), JsonObject::class.java)
            require(event.get("version").asInt == 1 && event.get("sequence").asLong == sequence + 1) { "Unsupported journal sequence/version" }
            apply(event)
            sequence++
            hash = nextHash
        }
    }

    private fun apply(event: JsonObject) {
        val data = event.getAsJsonObject("data")
        when (event.get("kind").asString) {
            "begin" -> {
                orderlyCheckpoint = null
                data.get("checkpointBefore")?.let { latestCheckpoint = it.asString }
                val id = UUID.fromString(data.get("id").asString)
                require(id !in pending)
                pending[id] = Pending(id, data.get("operation").asString,
                    UUID.fromString(data.get("actor").asString), data.get("snapshot").asString, "prepared")
            }
            "stage" -> {
                val id = UUID.fromString(data.get("id").asString)
                pending[id] = pending.getValue(id).copy(stage = data.get("stage").asString)
            }
            "finish" -> {
                val id = UUID.fromString(data.get("id").asString)
                pending[id] = pending.getValue(id).copy(stage = "runtime_complete", runtimeComplete = true)
                latestCheckpoint = data.get("checkpoint").asString
            }
            "resolve" -> {
                val id = UUID.fromString(data.get("id").asString)
                require(pending.remove(id) != null)
                inherited.remove(id)
            }
            "orderly_shutdown" -> {
                require(pending.values.all { it.runtimeComplete })
                latestCheckpoint = data.get("checkpoint").asString
                orderlyCheckpoint = latestCheckpoint
                pending.clear()
                inherited.clear()
            }
            else -> error("Unknown journal event")
        }
    }

    override fun close() {
        try { lock?.release() } finally { channel?.close() }
    }

    companion object {
        private const val MAX_FRAME = 32 * 1024 * 1024
        private fun digest(previous: ByteArray, bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").run { update(previous); digest(bytes) }
        private fun readFully(file: FileChannel, buffer: ByteBuffer) {
            while (buffer.hasRemaining()) require(file.read(buffer) > 0) { "Truncated journal; preserve original file for recovery" }
        }
    }
}
