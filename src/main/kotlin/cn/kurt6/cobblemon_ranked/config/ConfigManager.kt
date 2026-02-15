package cn.kurt6.cobblemon_ranked.config

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.RankUtils
import kotlinx.serialization.json.JsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {
    private val configPath: Path = CobblemonRanked.dataPath.resolve("cobblemon_ranked.json")
    private val dbConfigPath: Path = CobblemonRanked.dataPath.resolve("database.json")
    private val jankson = blue.endless.jankson.Jankson.builder().build()

    fun decodeUnicode(input: String): String {
        val regex = Regex("""\\u([0-9a-fA-F]{4})""")
        return regex.replace(input) {
            val hex = it.groupValues[1]
            hex.toInt(16).toChar().toString()
        }
    }

    fun loadDatabaseConfig(): DatabaseConfig {
        return try {
            if (Files.exists(dbConfigPath)) {
                val jsonText = Files.readString(dbConfigPath)
                val json = jankson.load(jsonText) as blue.endless.jankson.JsonObject
                val rawConfig = jankson.fromJson(json, DatabaseConfig::class.java)

                val mysqlJson = json.getObject("mysql")
                val mysqlConfig = if (mysqlJson != null) {
                    MySQLConfig(
                        host = mysqlJson.get(String::class.java, "host") ?: "localhost",
                        port = mysqlJson.get(Int::class.java, "port") ?: 3306,
                        database = mysqlJson.get(String::class.java, "database") ?: "cobblemon_ranked",
                        username = mysqlJson.get(String::class.java, "username") ?: "root",
                        password = mysqlJson.get(String::class.java, "password") ?: "",
                        poolSize = mysqlJson.get(Int::class.java, "poolSize") ?: 10,
                        connectionTimeout = mysqlJson.get(Long::class.java, "connectionTimeout") ?: 5000,
                        parameters = mysqlJson.get(String::class.java, "parameters")
                            ?: "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
                    )
                } else {
                    rawConfig.mysql
                }

                rawConfig.copy(mysql = mysqlConfig)
            } else {
                val default = DatabaseConfig()
                saveDatabaseConfig(default)
                default
            }
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load database config, using defaults", e)
            DatabaseConfig()
        }
    }

    fun saveDatabaseConfig(config: DatabaseConfig) {
        try {
            val json = jankson.toJson(config).toJson(true, true)
            Files.writeString(dbConfigPath, json, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to save database config", e)
        }
    }

    fun load(): RankConfig {
        return try {
            if (Files.exists(configPath)) {
                val jsonText = Files.readString(configPath)
                val json = jankson.load(jsonText) as blue.endless.jankson.JsonObject
                val rawConfig = jankson.fromJson(json, RankConfig::class.java)

                val rawTitlesJson = json.getObject("rankTitles")
                val fixedRankTitles = rawTitlesJson?.entries?.mapNotNull { (k, v) ->
                    k.toIntOrNull()?.let { elo ->
                        val encoded = v.toString().trim('"')
                        elo to decodeUnicode(encoded)
                    }
                }?.toMap() ?: emptyMap()

                val rawRankRewards = json.getObject("rankRewards")
                val fixedRankRewards: Map<String, Map<String, List<String>>> = rawRankRewards?.entries?.mapNotNull { (formatKey, rankMapElement) ->
                    val rankMap = rankMapElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null

                    val rankToCommands = rankMap.entries.associate { (rankKey, commandsElement) ->
                        val jsonArray = commandsElement as? blue.endless.jankson.JsonArray
                        val commandsList = jsonArray?.map { it.toString().trim('"') } ?: emptyList()
                        rankKey.trim() to commandsList
                    }

                    formatKey to rankToCommands
                }?.toMap() ?: emptyMap()

                val rawSinglesBannedCarriedItems = json.get("singlesBannedCarriedItems") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedCarriedItems = rawSinglesBannedCarriedItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedCarriedItems

                val rawSinglesBannedHeldItems = json.get("singlesBannedHeldItems") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedHeldItems = rawSinglesBannedHeldItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedHeldItems

                val rawSinglesRestrictedPokemon = json.get("singlesRestrictedPokemon") as? blue.endless.jankson.JsonArray
                val fixedSinglesRestrictedPokemon = rawSinglesRestrictedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesRestrictedPokemon

                val rawSinglesBannedPokemon = json.get("singlesBannedPokemon") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedPokemon = rawSinglesBannedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedPokemon

                val rawDoublesBannedCarriedItems = json.get("doublesBannedCarriedItems") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedCarriedItems = rawDoublesBannedCarriedItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedCarriedItems

                val rawDoublesBannedHeldItems = json.get("doublesBannedHeldItems") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedHeldItems = rawDoublesBannedHeldItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedHeldItems

                val rawDoublesRestrictedPokemon = json.get("doublesRestrictedPokemon") as? blue.endless.jankson.JsonArray
                val fixedDoublesRestrictedPokemon = rawDoublesRestrictedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesRestrictedPokemon

                val rawDoublesBannedPokemon = json.get("doublesBannedPokemon") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedPokemon = rawDoublesBannedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedPokemon

                val rawAllowedFormats = json.get("allowedFormats") as? blue.endless.jankson.JsonArray
                val fixedAllowedFormats = rawAllowedFormats
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.allowedFormats

                val rawMaxQueueTime = json.get("maxQueueTime") as? JsonPrimitive
                val fixedMaxQueueTime = rawMaxQueueTime?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxQueueTime

                val rawMaxEloMultiplier = json.get("maxEloMultiplier") as? JsonPrimitive
                val fixedMaxEloMultiplier = rawMaxEloMultiplier?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.maxEloMultiplier

                val rawBattleArenas = json.get("battleArenas") as? blue.endless.jankson.JsonArray
                val fixedBattleArenas = rawBattleArenas?.mapNotNull { arenaElement ->
                    val arenaObject = arenaElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null
                    val world = (arenaObject["world"] as? blue.endless.jankson.JsonPrimitive)?.value as? String ?: "minecraft:overworld"
                    val positionsArray = arenaObject["playerPositions"] as? blue.endless.jankson.JsonArray ?: return@mapNotNull null
                    val positions = positionsArray.mapNotNull { posElement ->
                        val posObject = posElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null
                        val x = (posObject["x"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        val y = (posObject["y"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        val z = (posObject["z"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        ArenaCoordinate(x, y, z)
                    }
                    if (positions.size == 2) BattleArena(world, positions) else null
                } ?: rawConfig.battleArenas

                val rawDefaultLang = json.get("defaultLang")
                val fixedDefaultLang = rawDefaultLang?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultLang

                val rawDefaultFormat = json.get("defaultFormat")
                val fixedDefaultFormat = rawDefaultFormat?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultFormat

                val rawMinTeamSize = json.get("minTeamSize")
                val fixedMinTeamSize = rawMinTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minTeamSize

                val rawMaxTeamSize = json.get("maxTeamSize")
                val fixedMaxTeamSize = rawMaxTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxTeamSize

                val rawMaxEloDiff = json.get("maxEloDiff")
                val fixedMaxEloDiff = rawMaxEloDiff?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxEloDiff

                val rawSeasonDuration = json.get("seasonDuration")
                val fixedSeasonDuration = rawSeasonDuration?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.seasonDuration

                val rawInitialElo = json.get("initialElo")
                val fixedInitialElo = rawInitialElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.initialElo

                val rawEloKFactor = json.get("eloKFactor")
                val fixedEloKFactor = rawEloKFactor?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.eloKFactor

                val rawMinElo = json.get("minElo")
                val fixedMinElo = rawMinElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minElo

                val rawSinglesRestrictedCount = json.get("singlesRestrictedCount")
                val fixedSinglesRestrictedCount = rawSinglesRestrictedCount?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.singlesRestrictedCount

                val rawDoublesRestrictedCount = json.get("doublesRestrictedCount")
                val fixedDoublesRestrictedCount = rawDoublesRestrictedCount?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.doublesRestrictedCount

                val rawLoserProtectionRate = json.get("loserProtectionRate")
                val fixedLoserProtectionRate = rawLoserProtectionRate?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.loserProtectionRate

                val rawMaxLevel = json.get("maxLevel")
                val fixedMaxLevel = rawMaxLevel?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxLevel

                val rawAllowDuplicateSpecies = json.get("allowDuplicateSpecies")
                val fixedAllowDuplicateSpecies = rawAllowDuplicateSpecies?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.allowDuplicateSpecies

                val rawCustomBattleLevel = json.get("customBattleLevel")
                val fixedCustomBattleLevel = rawCustomBattleLevel?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.customBattleLevel

                val rawEnableCustomLevel = json.get("enableCustomLevel")
                val fixedEnableCustomLevel = rawEnableCustomLevel?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.enableCustomLevel

                val rawRankRequirementsElement = json.get("rankRequirements")
                val fixedRankRequirements: Map<String, Double> = if (rawRankRequirementsElement is blue.endless.jankson.JsonObject) {
                    rawRankRequirementsElement.entries.mapNotNull { (rank, jsonValue) ->
                        val primitive = jsonValue as? blue.endless.jankson.JsonPrimitive
                        val number = primitive?.value?.toString()?.toDoubleOrNull()
                        if (number != null) rank to number else null
                    }.toMap()
                } else {
                    rawConfig.rankRequirements
                }

                val rawSinglesBannedMoves = json.get("singlesBannedMoves") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedMoves = rawSinglesBannedMoves
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedMoves

                val rawSinglesBannedNatures = json.get("singlesBannedNatures") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedNatures = rawSinglesBannedNatures
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedNatures

                val rawSinglesBannedAbilities = json.get("singlesBannedAbilities") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedAbilities = rawSinglesBannedAbilities
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedAbilities

                val rawSinglesBannedGenders = json.get("singlesBannedGenders") as? blue.endless.jankson.JsonArray
                val fixedSinglesBannedGenders = rawSinglesBannedGenders
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.singlesBannedGenders

                val rawSinglesBannedShiny = json.get("singlesBannedShiny")
                val fixedSinglesBannedShiny = rawSinglesBannedShiny?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.singlesBannedShiny

                val rawDoublesBannedMoves = json.get("doublesBannedMoves") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedMoves = rawDoublesBannedMoves
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedMoves

                val rawDoublesBannedNatures = json.get("doublesBannedNatures") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedNatures = rawDoublesBannedNatures
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedNatures

                val rawDoublesBannedAbilities = json.get("doublesBannedAbilities") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedAbilities = rawDoublesBannedAbilities
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedAbilities

                val rawDoublesBannedGenders = json.get("doublesBannedGenders") as? blue.endless.jankson.JsonArray
                val fixedDoublesBannedGenders = rawDoublesBannedGenders
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.doublesBannedGenders

                val rawDoublesBannedShiny = json.get("doublesBannedShiny")
                val fixedDoublesBannedShiny = rawDoublesBannedShiny?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.doublesBannedShiny

                val rawEnableCrossServer = json.get("enableCrossServer")
                val fixedEnableCrossServer = rawEnableCrossServer?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.enableCrossServer

                val fixedCloudServerId = when (val element = json.get("cloudServerId")) {
                    is blue.endless.jankson.JsonPrimitive -> {
                        decodeUnicode(element.asString().trim('"'))
                    }
                    is blue.endless.jankson.JsonNull -> rawConfig.cloudServerId
                    null -> rawConfig.cloudServerId
                    else -> element.toString().trim('"')
                }

                val rawcloudToken = json.get("cloudToken")
                val fixedcloudToken = rawcloudToken?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudToken

                val rawcloudApiUrl = json.get("cloudApiUrl")
                val fixedcloudApiUrl = rawcloudApiUrl?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudApiUrl

                val rawcloudWebSocketUrl = json.get("cloudWebSocketUrl")
                val fixedcloudWebSocketUrl = rawcloudWebSocketUrl?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudWebSocketUrl

                val rawSinglesBanUsageBelow = json.get("singlesBanUsageBelow")
                val fixedSinglesBanUsageBelow = rawSinglesBanUsageBelow?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.singlesBanUsageBelow

                val rawSinglesBanUsageAbove = json.get("singlesBanUsageAbove")
                val fixedSinglesBanUsageAbove = rawSinglesBanUsageAbove?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.singlesBanUsageAbove

                val rawSinglesBanTopUsed = json.get("singlesBanTopUsed")
                val fixedSinglesBanTopUsed = rawSinglesBanTopUsed?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.singlesBanTopUsed

                val rawSinglesOnlyBaseFormWithEvolution = json.get("singleOnlyBaseFormWithEvolution")
                val fixedSinglesOnlyBaseFormWithEvolution = rawSinglesOnlyBaseFormWithEvolution?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.singlesOnlyBaseFormWithEvolution

                val rawDoublesBanUsageBelow = json.get("doublesBanUsageBelow")
                val fixedDoublesBanUsageBelow = rawDoublesBanUsageBelow?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.doublesBanUsageBelow

                val rawDoublesBanUsageAbove = json.get("doublesBanUsageAbove")
                val fixedDoublesBanUsageAbove = rawDoublesBanUsageAbove?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.doublesBanUsageAbove

                val rawDoublesBanTopUsed = json.get("doublesBanTopUsed")
                val fixedDoublesBanTopUsed = rawDoublesBanTopUsed?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.doublesBanTopUsed

                val rawDoublesOnlyBaseFormWithEvolution = json.get("doublesOnlyBaseFormWithEvolution")
                val fixedDoublesOnlyBaseFormWithEvolution = rawDoublesOnlyBaseFormWithEvolution?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.doublesOnlyBaseFormWithEvolution

                val rawAllowDuplicateItems = json.get("allowDuplicateItems")
                val fixedAllowDuplicateItems = rawAllowDuplicateItems?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.allowDuplicateItems

                val rawEnableTeamPreview = json.get("enableTeamPreview")
                val fixedEnableTeamPreview = rawEnableTeamPreview?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.enableTeamPreview

                val rawTeamSelectionTime = json.get("teamSelectionTime")
                val fixedTeamSelectionTime = rawTeamSelectionTime?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.teamSelectionTime

                val rawSinglesPickCount = json.get("singlesPickCount")
                val fixedSinglesPickCount = rawSinglesPickCount?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.singlesPickCount

                val rawDoublesPickCount = json.get("doublesPickCount")
                val fixedDoublesPickCount = rawDoublesPickCount?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.doublesPickCount

                val rawVictoryRewards = json.get("victoryRewards") as? blue.endless.jankson.JsonArray
                val fixedVictoryRewards = rawVictoryRewards
                    ?.mapNotNull {
                        when (it) {
                            is blue.endless.jankson.JsonPrimitive -> {
                                val rawValue = it.asString()
                                decodeUnicode(rawValue)
                            }
                            is blue.endless.jankson.JsonNull -> null
                            else -> {
                                val stringValue = it.toString().trim('"')
                                decodeUnicode(stringValue)
                            }
                        }
                    }
                    ?: rawConfig.victoryRewards

                rawConfig.copy(
                    rankTitles = fixedRankTitles,
                    rankRewards = fixedRankRewards,
                    allowedFormats = fixedAllowedFormats,
                    singlesBannedCarriedItems = fixedSinglesBannedCarriedItems,
                    singlesBannedHeldItems = fixedSinglesBannedHeldItems,
                    singlesBannedPokemon = fixedSinglesBannedPokemon,
                    singlesRestrictedPokemon = fixedSinglesRestrictedPokemon,
                    doublesBannedCarriedItems = fixedDoublesBannedCarriedItems,
                    doublesBannedHeldItems = fixedDoublesBannedHeldItems,
                    doublesBannedPokemon = fixedDoublesBannedPokemon,
                    doublesRestrictedPokemon = fixedDoublesRestrictedPokemon,
                    maxQueueTime = fixedMaxQueueTime,
                    allowDuplicateItems = fixedAllowDuplicateItems,
                    enableTeamPreview = fixedEnableTeamPreview,
                    teamSelectionTime = fixedTeamSelectionTime,
                    singlesPickCount = fixedSinglesPickCount,
                    doublesPickCount = fixedDoublesPickCount,
                    maxEloMultiplier = fixedMaxEloMultiplier,
                    battleArenas = fixedBattleArenas,
                    defaultFormat = fixedDefaultFormat,
                    defaultLang = fixedDefaultLang,
                    minTeamSize = fixedMinTeamSize,
                    maxTeamSize = fixedMaxTeamSize,
                    maxEloDiff = fixedMaxEloDiff,
                    seasonDuration = fixedSeasonDuration,
                    initialElo = fixedInitialElo,
                    eloKFactor = fixedEloKFactor,
                    minElo = fixedMinElo,
                    singlesRestrictedCount = fixedSinglesRestrictedCount,
                    doublesRestrictedCount = fixedDoublesRestrictedCount,
                    loserProtectionRate = fixedLoserProtectionRate,
                    allowDuplicateSpecies = fixedAllowDuplicateSpecies,
                    maxLevel = fixedMaxLevel,
                    customBattleLevel = fixedCustomBattleLevel,
                    enableCustomLevel = fixedEnableCustomLevel,
                    singlesBanUsageBelow = fixedSinglesBanUsageBelow,
                    singlesBanUsageAbove = fixedSinglesBanUsageAbove,
                    singlesBanTopUsed = fixedSinglesBanTopUsed,
                    singlesOnlyBaseFormWithEvolution = fixedSinglesOnlyBaseFormWithEvolution,
                    doublesBanUsageBelow = fixedDoublesBanUsageBelow,
                    doublesBanUsageAbove = fixedDoublesBanUsageAbove,
                    doublesBanTopUsed = fixedDoublesBanTopUsed,
                    doublesOnlyBaseFormWithEvolution = fixedDoublesOnlyBaseFormWithEvolution,
                    rankRequirements = fixedRankRequirements,
                    singlesBannedMoves = fixedSinglesBannedMoves,
                    singlesBannedNatures = fixedSinglesBannedNatures,
                    singlesBannedAbilities = fixedSinglesBannedAbilities,
                    singlesBannedGenders = fixedSinglesBannedGenders,
                    singlesBannedShiny = fixedSinglesBannedShiny,
                    doublesBannedMoves = fixedDoublesBannedMoves,
                    doublesBannedNatures = fixedDoublesBannedNatures,
                    doublesBannedAbilities = fixedDoublesBannedAbilities,
                    doublesBannedGenders = fixedDoublesBannedGenders,
                    doublesBannedShiny = fixedDoublesBannedShiny,
                    enableCrossServer = fixedEnableCrossServer,
                    cloudServerId = fixedCloudServerId,
                    cloudToken = fixedcloudToken,
                    cloudApiUrl = fixedcloudApiUrl,
                    cloudWebSocketUrl = fixedcloudWebSocketUrl,
                    victoryRewards = fixedVictoryRewards
                )
            } else {
                val default = RankConfig()
                save(default)
                default
            }
        } catch (e: Exception) {
            throw RuntimeException("Configuration file loading failed: ${e.message}", e)
        }
    }

    fun save(config: RankConfig) {
        val json = jankson.toJson(config).toJson(true, true)
        Files.writeString(configPath, json, StandardCharsets.UTF_8)
    }

    fun reload(): RankConfig {
        val newConfig = load()

        CobblemonRanked.matchmakingQueue.clear()
        synchronized(DuoMatchmakingQueue) {

        }

        CobblemonRanked.config = newConfig
        CobblemonRanked.matchmakingQueue.reloadConfig(newConfig)

        try {
            val server = CobblemonRanked.INSTANCE.javaClass.getDeclaredField("server")
                .apply { isAccessible = true }
                .get(CobblemonRanked.INSTANCE) as? net.minecraft.server.MinecraftServer

            server?.playerManager?.playerList?.forEach { player ->
                RankUtils.sendMessage(
                    player,
                    MessageConfig.get("config.reloaded", newConfig.defaultLang)
                )
            }
        } catch (e: Exception) {

        }

        return newConfig
    }
}