package com.zianblk.ziangts.v2.persistence

import com.google.gson.Gson
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.v2.port.HistoryPort
import com.zianblk.ziangts.v2.port.TradeHistoryRecord
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
    private val records = mutableListOf<TradeHistoryRecord>()
    private var previousHash = ByteArray(32)
    private val channel: FileChannel

    init {
        Files.createDirectories(file.parent)
        channel = FileChannel.open(file, CREATE, READ, WRITE)
        replay()
        channel.position(channel.size())
    }

    @Synchronized
    override fun append(record: TradeHistoryRecord) {
        require(records.none { it.operationId == record.operationId }) { "duplicate history operation" }
        val payload = gson.toJson(Wire.from(record)).toByteArray(StandardCharsets.UTF_8)
        val hash = digest(previousHash, payload)
        val frame = ByteBuffer.allocate(4 + payload.size + hash.size)
            .putInt(payload.size).put(payload).put(hash)
        frame.flip()
        while (frame.hasRemaining()) channel.write(frame)
        channel.force(true)
        previousHash = hash
        records += record
    }

    @Synchronized
    override fun all(): List<TradeHistoryRecord> = records.toList()

    private fun replay() {
        channel.position(0)
        var expectedPrevious = ByteArray(32)
        while (channel.position() < channel.size()) {
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
            val wire = gson.fromJson(String(payload, StandardCharsets.UTF_8), Wire::class.java)
            val record = wire.toRecord()
            require(records.none { it.operationId == record.operationId }) { "duplicate history operation" }
            records += record
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

    override fun close() = channel.close()

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
