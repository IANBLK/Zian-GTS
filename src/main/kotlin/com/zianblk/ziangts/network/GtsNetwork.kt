package com.zianblk.ziangts.network

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.google.gson.Gson
import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.data.ListingsData
import com.zianblk.ziangts.economy.AvecoinsCatalog
import com.zianblk.ziangts.server.GtsException
import com.zianblk.ziangts.server.GtsService
import com.zianblk.ziangts.server.GtsPermissions
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

// Request contains identifiers only. Prices, ownership and Pokémon always come from server storage.
data class MarketRequest(val action: Int, val listing: UUID, val page: Int, val filter: Int, val sort: Int) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<MarketRequest>(ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "request"))
        val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, MarketRequest> {
            override fun decode(buf: RegistryFriendlyByteBuf) = MarketRequest(buf.readVarInt(), buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            override fun encode(buf: RegistryFriendlyByteBuf, value: MarketRequest) {
                buf.writeVarInt(value.action); buf.writeUUID(value.listing); buf.writeVarInt(value.page)
                buf.writeVarInt(value.filter); buf.writeVarInt(value.sort)
            }
        }
    }
}

data class MarketPagePayload(val json: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<MarketPagePayload>(ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "page"))
        val CODEC = object : StreamCodec<RegistryFriendlyByteBuf, MarketPagePayload> {
            override fun decode(buf: RegistryFriendlyByteBuf) = MarketPagePayload(buf.readUtf(30000))
            override fun encode(buf: RegistryFriendlyByteBuf, value: MarketPagePayload) { buf.writeUtf(value.json, 30000) }
        }
    }
}

data class MarketEntry(val id: String, val name: String, val species: String, val seller: String,
    val price: Int, val currency: String, val economyProvider: String, val level: Int, val gender: String, val shiny: Boolean,
    val nature: String, val ability: String, val aspects: List<String>, val stats: List<Int>, val ivs: List<Int>,
    val mine: Boolean, val expired: Boolean)
data class MarketPage(val entries: List<MarketEntry>, val page: Int, val pages: Int, val filter: Int, val sort: Int, val openScreen: Boolean, val notice: String)

object GtsNetwork {
    private val gson = Gson()
    var clientReceiver: (MarketPage) -> Unit = {}
    private val lastRequest = java.util.WeakHashMap<ServerPlayer, Long>()

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("3")
        registrar.playToClient(MarketPagePayload.TYPE, MarketPagePayload.CODEC) { payload, _ ->
            clientReceiver(gson.fromJson(payload.json, MarketPage::class.java))
        }
        registrar.playToServer(MarketRequest.TYPE, MarketRequest.CODEC) { request, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            if (request.action !in 0..3 || request.page !in 1..1_000_000 || request.filter !in 0..5 || request.sort !in 0..5)
                return@playToServer
            val now = System.nanoTime()
            if (lastRequest[player]?.let { now - it < 150_000_000L } == true) return@playToServer
            lastRequest[player] = now
            var notice = ""
            try {
                GtsPermissions.require(player, if (request.filter == 5) "ziangts.mine" else "ziangts.list")
                when (request.action) {
                    1 -> GtsService.buy(player, request.listing)
                    2 -> GtsService.cancel(player, request.listing)
                    3 -> notice = GtsService.claim(player)
                }
                if (request.action in 1..2) {
                    notice = if (request.action == 1) "command.ziangts.purchase.success" else "command.ziangts.cancel.success"
                    player.sendSystemMessage(Component.translatable(notice))
                }
            } catch (error: GtsException) {
                notice = error.key
                player.sendSystemMessage(Component.translatable(error.key))
            } catch (error: Exception) {
                // An unexpected failure must not escape the payload handler: NeoForge could disconnect the player.
                ZianGts.LOGGER.error("Unexpected GTS failure for {} (action {})", player.uuid, request.action, error)
                notice = "command.ziangts.storage_failed"
                player.sendSystemMessage(Component.translatable(notice))
            }
            try {
                open(player, request.page, request.filter, request.sort, openScreen = false, notice = notice)
            } catch (error: GtsException) {
                PacketDistributor.sendToPlayer(player, MarketPagePayload(gson.toJson(
                    MarketPage(emptyList(), 1, 1, request.filter, request.sort, false, error.key))))
            } catch (error: Exception) {
                ZianGts.LOGGER.error("Could not refresh the GTS page for {}", player.uuid, error)
                PacketDistributor.sendToPlayer(player, MarketPagePayload(gson.toJson(
                    MarketPage(emptyList(), 1, 1, request.filter, request.sort, false, "command.ziangts.storage_failed"))))
            }
        }
    }

    fun open(player: ServerPlayer, requestedPage: Int = 1, filter: Int = 0, sort: Int = 0,
             openScreen: Boolean = true, notice: String = "") {
        GtsPermissions.require(player, if (filter == 5) "ziangts.mine" else "ziangts.list")
        if (openScreen && player.containerMenu !== player.inventoryMenu) player.closeContainer()
        val listings = ListingsData.get(player.serverLevel()).all().filter {
            if (filter == 5) it.sellerId == player.uuid
            else !it.isExpired() && AvecoinsCatalog.isSupported(it.currency) && when (filter) {
                1 -> it.pokemon.shiny
                2 -> "alpha" in it.pokemon.aspects
                3 -> it.pokemon.isLegendary()
                4 -> it.pokemon.isLegendary() && it.pokemon.shiny
                else -> true
            }
        }
        val ordered = when (sort) {
            1 -> listings.sortedBy { it.createdAt }
            2 -> listings.sortedBy { it.price }
            3 -> listings.sortedByDescending { it.price }
            4 -> listings.sortedBy { it.pokemon.level }
            5 -> listings.sortedByDescending { it.pokemon.level }
            else -> listings.sortedByDescending { it.createdAt }
        }
        val pages = maxOf(1, (ordered.size + 5) / 6)
        val page = requestedPage.coerceIn(1, pages)
        val stats = Stats.PERMANENT.toList()
        val entries = ordered.drop((page - 1) * 6).take(6).map {
            val p = it.pokemon
            MarketEntry(it.id.toString(), p.species.name, p.species.resourceIdentifier.toString(), it.sellerName,
                it.price, it.currency, it.economyProvider, p.level, p.gender.name, p.shiny,
                p.effectiveNature.displayName, p.ability.displayName, p.aspects.toList(), stats.map(p::getStat),
                stats.map { stat -> p.ivs[stat] ?: 0 },
                it.sellerId == player.uuid, it.isExpired())
        }
        PacketDistributor.sendToPlayer(player, MarketPagePayload(gson.toJson(MarketPage(entries, page, pages, filter, sort, openScreen, notice))))
    }
}
