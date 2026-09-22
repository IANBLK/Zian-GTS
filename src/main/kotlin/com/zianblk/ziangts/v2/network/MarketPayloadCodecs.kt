package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

/**
 * Explicit codecs for the V2 market protocol.
 *
 * Kept separate from payload registration so NeoForge API wiring can change without
 * changing the transport contract or its tests.
 */
object MarketPayloadCodecs {
    val PAGE_REQUEST: StreamCodec<RegistryFriendlyByteBuf, MarketPageRequest> =
        StreamCodec.of(
            { buf, value -> encodeRequest(buf, value) },
            { buf -> decodeRequest(buf) }
        )

    val PAGE_RESPONSE: StreamCodec<RegistryFriendlyByteBuf, MarketPageResponse> =
        StreamCodec.of(
            { buf, value -> encodeResponse(buf, value) },
            { buf -> decodeResponse(buf) }
        )

    private fun encodeRequest(buf: RegistryFriendlyByteBuf, value: MarketPageRequest) {
        buf.writeVarInt(value.protocolVersion)
        buf.writeEnum(value.tab)
        buf.writeVarInt(value.page)
        buf.writeVarInt(value.pageSize)
        buf.writeEnum(value.filter)
        buf.writeEnum(value.sort)
    }

    private fun decodeRequest(buf: RegistryFriendlyByteBuf) = MarketPageRequest(
        protocolVersion = buf.readVarInt(),
        tab = buf.readEnum(MarketTab::class.java),
        page = buf.readVarInt(),
        pageSize = buf.readVarInt(),
        filter = buf.readEnum(OfferFilter::class.java),
        sort = buf.readEnum(OfferSort::class.java)
    )

    private fun encodeResponse(buf: RegistryFriendlyByteBuf, value: MarketPageResponse) {
        buf.writeVarInt(value.protocolVersion)
        buf.writeEnum(value.tab)
        buf.writeVarInt(value.entries.size)
        value.entries.forEach { encodeEntry(buf, it) }
        buf.writeVarInt(value.total)
        buf.writeVarInt(value.page)
        buf.writeVarInt(value.pageSize)
        buf.writeVarInt(value.totalPages)
        buf.writeBoolean(value.hasPrevious)
        buf.writeBoolean(value.hasNext)
        buf.writeEnum(value.filter)
        buf.writeEnum(value.sort)
    }

    private fun decodeResponse(buf: RegistryFriendlyByteBuf): MarketPageResponse {
        val version = buf.readVarInt()
        require(version == MARKET_PROTOCOL_VERSION) { "unsupported market protocol version" }
        val tab = buf.readEnum(MarketTab::class.java)
        val count = buf.readVarInt()
        require(count in 0..50) { "invalid market entry count" }
        val entries = List(count) { decodeEntry(buf) }
        return MarketPageResponse(
            protocolVersion = version,
            tab = tab,
            entries = entries,
            total = buf.readVarInt(),
            page = buf.readVarInt(),
            pageSize = buf.readVarInt(),
            totalPages = buf.readVarInt(),
            hasPrevious = buf.readBoolean(),
            hasNext = buf.readBoolean(),
            filter = buf.readEnum(OfferFilter::class.java),
            sort = buf.readEnum(OfferSort::class.java)
        )
    }

    private fun encodeEntry(buf: RegistryFriendlyByteBuf, value: MarketEntryDto) {
        buf.writeUUID(value.offerId)
        buf.writeUUID(value.sellerId)
        buf.writeUtf(value.sellerName, 64)
        buf.writeUUID(value.pokemonId)
        buf.writeUtf(value.species, 128)
        buf.writeVarInt(value.level)
        buf.writeBoolean(value.shiny)
        buf.writeBoolean(value.alpha)
        buf.writeVarLong(value.price)
        buf.writeUtf(value.currency, 128)
        buf.writeLong(value.publishedAtEpochMilli)
        buf.writeLong(value.expiresAtEpochMilli)
        buf.writeBoolean(value.ownedByViewer)
        buf.writeBoolean(value.canBuy)
        buf.writeBoolean(value.canWithdraw)
    }

    private fun decodeEntry(buf: RegistryFriendlyByteBuf) = MarketEntryDto(
        offerId = buf.readUUID(),
        sellerId = buf.readUUID(),
        sellerName = buf.readUtf(64),
        pokemonId = buf.readUUID(),
        species = buf.readUtf(128),
        level = buf.readVarInt(),
        shiny = buf.readBoolean(),
        alpha = buf.readBoolean(),
        price = buf.readVarLong(),
        currency = buf.readUtf(128),
        publishedAtEpochMilli = buf.readLong(),
        expiresAtEpochMilli = buf.readLong(),
        ownedByViewer = buf.readBoolean(),
        canBuy = buf.readBoolean(),
        canWithdraw = buf.readBoolean()
    )
}
