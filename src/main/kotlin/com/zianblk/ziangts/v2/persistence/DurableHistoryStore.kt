package com.zianblk.ziangts.v2.persistence

import com.google.gson.Gson
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.v2.port.HistoryPort
import com.zianblk.ziangts.v2.port.TradeHistoryRecord
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import com.zianblk.ziangts.storage.StorageFiles as Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Append-only, checksummed V2 history. A record is durable before append returns.
 * History is evidence, not market state, so it intentionally lives in its own file.
 */
class DurableHistoryStore(private val file: Path) : HistoryPort, AutoCloseable {
    private val gson = Gson()
    private data class Pointer(val index: Int, val offset: Long, val length: Int, val hash: ByteArray)
    private val records = mutableListOf<Pointer>()
    private val operationIds = hashSetOf<UUID>()
    private val recordsByPlayer = mutableMapOf<UUID, MutableList<Pointer>>()
    private var previousHash = ByteArray(32)
    private val channel: FileChannel
    private val lock: java.nio.channels.FileLock
    private var healthy = true
    private var compactable = true

    init {
        Files.createDirectories(file.parent)
        channel = Files.channel(file, CREATE, READ, WRITE)
        try {
            lock = channel.tryLock() ?: error("history already in use")
            replay()
            channel.position(channel.size())
        } catch (error: Exception) {
            try { channel.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
            throw error
        }
    }

    @Synchronized
    override fun append(record: TradeHistoryRecord) {
        check(available()) { "history unavailable" }
        require(record.operationId !in operationIds && (file.fileName.toString()!="history-v2.wal" || !Files.wasRecorded(file,"gts-history",record.operationId))) { "duplicate history operation" }
        val payload = gson.toJson(Wire.from(record)).toByteArray(StandardCharsets.UTF_8)
        val hash = digest(previousHash, payload)
        val frame = ByteBuffer.allocate(4 + payload.size + hash.size)
            .putInt(payload.size).put(payload).put(hash)
        frame.flip()
        val offset = channel.size()
        try {
            channel.position(offset)
            while (frame.hasRemaining()) channel.write(frame)
            channel.force(true)
            previousHash = hash
            val pointer = Pointer(records.size, offset, payload.size, hash.copyOf())
            records += pointer
            operationIds += record.operationId
            index(record, pointer)
        } catch (error: Exception) { healthy = false; throw error }

    }

    @Synchronized
    override fun all(): List<TradeHistoryRecord> = records.map(::readRecord)

    @Synchronized override fun available(): Boolean = healthy && channel.isOpen

    @Synchronized
    override fun forPlayer(playerId: UUID, limit: Int): List<TradeHistoryRecord> {
        require(limit > 0) { "history limit must be positive" }
        val indexed = recordsByPlayer[playerId] ?: return emptyList()
        return indexed.asReversed().asSequence().take(limit).map(::readRecord).toList()
    }

    private fun index(record: TradeHistoryRecord, pointer: Pointer) {
        recordsByPlayer.getOrPut(record.sellerId) { mutableListOf() }.add(pointer)
        if (record.buyerId != record.sellerId) {
            recordsByPlayer.getOrPut(record.buyerId) { mutableListOf() }.add(pointer)
        }
    }

    private fun replay() {
        channel.position(0)
        var expectedPrevious = ByteArray(32)
        while (channel.position() < channel.size()) {
            val offset = channel.position()
            val lengthBuffer = ByteBuffer.allocate(4)
            readFully(lengthBuffer)
            lengthBuffer.flip()
            val length = lengthBuffer.int
            require(length in 1..1_048_576) { "invalid history frame length" }
            val payloadBuffer = ByteBuffer.allocate(length)
            readFully(payloadBuffer)
            val hashBuffer = ByteBuffer.allocate(32)
            readFully(hashBuffer)
            val payload = payloadBuffer.array()
            val actualHash = hashBuffer.array()
            val expectedHash = digest(expectedPrevious, payload)
            require(MessageDigest.isEqual(actualHash, expectedHash)) { "history checksum mismatch" }
            val json = com.google.gson.JsonParser.parseString(String(payload, StandardCharsets.UTF_8)).asJsonObject
            if (json.keySet() != setOf("operationId","offerId","sellerId","buyerId","pokemonId","species","adapter","currency","amount","completedAt")) compactable = false
            val wire = gson.fromJson(json, Wire::class.java)
            val record = wire.toRecord()
            require(record.operationId !in operationIds) { "duplicate history operation" }
            val pointer = Pointer(records.size, offset, length, actualHash.copyOf())
            records += pointer
            operationIds += record.operationId
            index(record, pointer)
            expectedPrevious = actualHash
        }
        previousHash = expectedPrevious
    }

    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            require(read >= 0) { "truncated history frame" }
        }
    }

    private fun readRecord(pointer: Pointer): TradeHistoryRecord {
        try {
            check(available()) { "history unavailable" }
            fun readAt(buffer: ByteBuffer, offset: Long) {
                var at = offset
                while (buffer.hasRemaining()) {
                    val read = channel.read(buffer, at)
                    require(read > 0) { "truncated history frame" }
                    at += read
                }
            }
            val header = ByteBuffer.allocate(4)
            readAt(header, pointer.offset)
            require(header.flip().int == pointer.length) { "history frame length changed" }
            val payload = ByteBuffer.allocate(pointer.length)
            readAt(payload, pointer.offset + 4)
            val stored = ByteBuffer.allocate(32)
            readAt(stored, pointer.offset + 4 + pointer.length)
            val previous = if (pointer.index == 0) ByteArray(32) else records[pointer.index - 1].hash
            require(MessageDigest.isEqual(stored.array(), pointer.hash)
                && MessageDigest.isEqual(digest(previous, payload.array()), pointer.hash)) { "history checksum changed" }
            return gson.fromJson(String(payload.array(), Charsets.UTF_8), Wire::class.java).toRecord()
        } catch (error: Exception) { healthy = false; throw error }
    }

    @Synchronized internal fun visit(action: (TradeHistoryRecord) -> Unit) { records.forEach { action(readRecord(it)) } }
    internal fun canCompact(): Boolean = compactable
    internal fun archiveCompressed(target: Path) {
        check(available())
        val original = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(65536)
        java.util.zip.GZIPOutputStream(Files.newOutputStream(target, CREATE_NEW)).use { out ->
            var at = 0L
            while (at < channel.size()) {
                buffer.clear()
                val n = channel.read(buffer,at)
                require(n > 0)
                original.update(buffer.array(),0,n)
                out.write(buffer.array(),0,n)
                at += n
            }
        }
        val archived = MessageDigest.getInstance("SHA-256")
        java.util.zip.GZIPInputStream(Files.newInputStream(target)).use { input ->
            val bytes = ByteArray(65536)
            while(true){val n=input.read(bytes);if(n<0)break;archived.update(bytes,0,n)}
        }
        check(MessageDigest.isEqual(original.digest(),archived.digest())) { "history archive mismatch" }
    }

    override fun close() { healthy = false; try { if(lock.isValid) lock.release() } finally { channel.close() } }


    private fun digest(previous: ByteArray, payload: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update(previous)
            update(payload)
        }.digest()

    private data class Wire(
        val operationId: String,
        val offerId: String,
        val sellerId: String,
        val buyerId: String,
        val pokemonId: String,
        val species: String,
        val adapter: String,
        val currency: String,
        val amount: Long,
        val completedAt: String
    ) {
        fun toRecord() = TradeHistoryRecord(
            UUID.fromString(operationId), OfferId(UUID.fromString(offerId)),
            UUID.fromString(sellerId), UUID.fromString(buyerId), UUID.fromString(pokemonId),
            species, PaymentSpec(adapter, currency, amount), Instant.parse(completedAt)
        )

        companion object {
            fun from(r: TradeHistoryRecord) = Wire(
                r.operationId.toString(), r.offerId.value.toString(), r.sellerId.toString(),
                r.buyerId.toString(), r.pokemonId.toString(), r.species,
                r.payment.adapter, r.payment.currency, r.payment.amount, r.completedAt.toString()
            )
        }
    }
}
