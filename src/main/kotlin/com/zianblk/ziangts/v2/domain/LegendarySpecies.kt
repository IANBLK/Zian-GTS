package com.zianblk.ziangts.v2.domain

/**
 * Única lista de legendarios de GTS (filtro, envoltorio y migración del almacén).
 * Domain-only fallback list. Runtime integrations may later replace this with Cobblemon registry tags.
 * Keeping the decision here deterministic makes filters testable without Minecraft classes.
 */
internal fun isLegendarySpecies(species: String): Boolean = species.substringAfter(':') in LEGENDARY_SPECIES

private val LEGENDARY_SPECIES = setOf(
    "articuno", "zapdos", "moltres", "mewtwo", "mew",
    "raikou", "entei", "suicune", "lugia", "ho_oh", "celebi",
    "regirock", "regice", "registeel", "latias", "latios", "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
    "uxie", "mesprit", "azelf", "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia", "phione", "manaphy", "darkrai", "shaymin", "arceus",
    "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "reshiram", "zekrom", "landorus", "kyurem", "keldeo", "meloetta", "genesect",
    "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion",
    "type_null", "silvally", "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini", "cosmog", "cosmoem", "solgaleo", "lunala", "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord", "necrozma", "magearna", "marshadow", "poipole", "naganadel", "stakataka", "blacephalon", "zeraora", "meltan", "melmetal",
    "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "zarude", "regieleki", "regidrago", "glastrier", "spectrier", "calyrex", "enamorus",
    "wo_chien", "chien_pao", "ting_lu", "chi_yu", "koraidon", "miraidon", "okidogi", "munkidori", "fezandipiti", "ogerpon", "terapagos", "pecharunt"
)
