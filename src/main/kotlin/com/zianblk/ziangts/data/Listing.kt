package com.zianblk.ziangts.data

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag

/**
 * One active GTS listing.
 *
 * Field layout intentionally mirrors the proven reference format so existing
 * concepts are easy to migrate: id, seller, price, currency, timestamps and
 * the complete Pokemon.
 */
data class Listing(
    val id: UUID,
    val sellerId: UUID,
    val sellerName: String,
    val price: Int,
    val currency: String,
    val createdAt: Long,
    val expiresAt: Long,
    val pokemon: Pokemon,
    val economyProvider: String = "vanilla_item"
) {
    init {
        com.zianblk.ziangts.economy.EconomyKey(economyProvider, currency)
        require(sellerName.isNotBlank()) { "sellerName must not be blank" }
        require(price > 0) { "price must be greater than zero" }
        require(currency.isNotBlank()) { "currency must not be blank" }
        require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
    }

    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis >= expiresAt

    fun toNbt(registryAccess: RegistryAccess): CompoundTag = CompoundTag().apply {
        putUUID(KEY_ID, this@Listing.id)
        putUUID(KEY_SELLER_ID, sellerId)
        putString(KEY_SELLER_NAME, sellerName)
        putInt(KEY_PRICE, price)
        putString(KEY_CURRENCY, currency)
        putString("economyProvider", economyProvider)
        putLong(KEY_CREATED_AT, createdAt)
        putLong(KEY_EXPIRES_AT, expiresAt)
        put(KEY_POKEMON, pokemon.saveToNBT(registryAccess))
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_SELLER_ID = "sellerId"
        private const val KEY_SELLER_NAME = "sellerName"
        private const val KEY_PRICE = "price"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val KEY_POKEMON = "pokemon"
        private const val LEGACY_DEFAULT_CURRENCY = "minecraft:diamond"

        fun fromNbt(registryAccess: RegistryAccess, nbt: CompoundTag): Listing {
            require(nbt.hasUUID(KEY_ID) && nbt.hasUUID(KEY_SELLER_ID)) { "Listing UUIDs are missing" }
            require(nbt.getString(KEY_SELLER_NAME).isNotBlank()) { "Seller name is missing" }
            require(nbt.getInt(KEY_PRICE) > 0) { "Invalid listing price" }
            require(nbt.getLong(KEY_EXPIRES_AT) > nbt.getLong(KEY_CREATED_AT)) { "Invalid listing expiry" }
            require(nbt.contains(KEY_POKEMON, 10)) { "Pokemon snapshot is missing" }
            val pokemon = Pokemon.loadFromNBT(registryAccess, nbt.getCompound(KEY_POKEMON))
            return Listing(
                id = nbt.getUUID(KEY_ID),
                sellerId = nbt.getUUID(KEY_SELLER_ID),
                sellerName = nbt.getString(KEY_SELLER_NAME),
                price = nbt.getInt(KEY_PRICE),
                currency = if (nbt.contains(KEY_CURRENCY)) {
                    nbt.getString(KEY_CURRENCY)
                } else {
                    LEGACY_DEFAULT_CURRENCY
                },
                createdAt = nbt.getLong(KEY_CREATED_AT),
                expiresAt = nbt.getLong(KEY_EXPIRES_AT),
                pokemon = pokemon,
                economyProvider = if (nbt.contains("economyProvider")) nbt.getString("economyProvider") else "vanilla_item"
            )
        }
    }
}

