package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.*
import kotlin.random.Random

/**
 * Generates procedural quests based on dungeon state
 */
class QuestGenerator(private val seed: Long? = null) {
    private val random = seed?.let { Random(it) } ?: Random.Default

    /**
     * Quest templates for each dungeon theme
     */
    private val questTitles = mapOf(
        DungeonTheme.CRYPT to listOf(
            "Cleanse the Tomb",
            "Retrieve the Lost Relic",
            "Put the Dead to Rest",
            "Silence the Necromancer",
            "Recover Ancient Bones"
        ),
        DungeonTheme.CASTLE to listOf(
            "Reclaim the Throne Room",
            "Find the Royal Seal",
            "Defeat the Usurper",
            "Recover the Crown Jewels",
            "Restore Honor to the Keep"
        ),
        DungeonTheme.CAVE to listOf(
            "Clear the Goblin Nest",
            "Mine the Rare Crystals",
            "Defeat the Cave Troll",
            "Map the Deep Tunnels",
            "Retrieve the Earth Stone"
        ),
        DungeonTheme.TEMPLE to listOf(
            "Purify the Sanctuary",
            "Recover Sacred Texts",
            "Defeat the False Prophet",
            "Restore the Divine Wards",
            "Find the Holy Artifact"
        )
    )

    /**
     * Generate a quest for a given world state
     */
    fun generateQuest(
        worldState: WorldState,
        theme: DungeonTheme
    ): Quest {
        if (worldState.spaces.isEmpty()) {
            return generateFallbackQuest(worldState, theme)
        }

        val questType = random.nextInt(5) // 5 quest types

        return when (questType) {
            0 -> generateKillQuest(worldState, theme)
            1 -> generateCollectQuest(worldState, theme)
            2 -> generateExploreQuest(worldState, theme)
            3 -> generateTalkQuest(worldState, theme)
            4 -> generateSkillQuest(worldState, theme)
            else -> generateKillQuest(worldState, theme)
        }
    }

    /**
     * Generate a quest to kill an enemy
     */
    private fun generateKillQuest(worldState: WorldState, theme: DungeonTheme): Quest {
        // V3: Get all NPCs from global entity storage
        val hostileNpcs = worldState.entities.values
            .filterIsInstance<Entity.NPC>()
            .filter { it.isHostile }

        val targetNpc = hostileNpcs.randomOrNull(random) ?: run {
            // Fallback if no hostile NPCs
            return generateCollectQuest(worldState, theme)
        }

        val title = questTitles[theme]?.randomOrNull(random) ?: "Defeat the Enemy"
        val objective = QuestObjective.KillEnemy(
            id = "obj_kill_${targetNpc.id}",
            description = "Defeat ${targetNpc.name}",
            targetNpcId = targetNpc.id,
            targetName = targetNpc.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "The ${targetNpc.name} poses a threat to all who enter this place. Defeat them to make the area safer.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 100,
                goldAmount = 50,
                description = "Experience and gold reward"
            ),
            flavorText = when (theme) {
                DungeonTheme.CRYPT -> "The undead must be put to rest."
                DungeonTheme.CASTLE -> "Restore order to these halls."
                DungeonTheme.CAVE -> "Make the caverns safe for travelers."
                DungeonTheme.TEMPLE -> "Purify this sacred place."
            }
        )
    }

    /**
     * Generate a quest to collect items
     */
    private fun generateCollectQuest(worldState: WorldState, theme: DungeonTheme): Quest {
        // V3: Get all items from global entity storage
        val items = worldState.entities.values
            .filterIsInstance<Entity.Item>()
            .filter { it.isPickupable && it.itemType != ItemType.CONSUMABLE }

        val targetItem = items.randomOrNull(random) ?: run {
            // Fallback if no collectible items
            return generateExploreQuest(worldState, theme)
        }

        val title = questTitles[theme]?.randomOrNull(random) ?: "Collect the Treasure"
        val objective = QuestObjective.CollectItem(
            id = "obj_collect_${targetItem.id}",
            description = "Collect ${targetItem.name}",
            targetItemId = targetItem.id,
            targetName = targetItem.name,
            quantity = 1,
            currentQuantity = 0
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Find and retrieve the ${targetItem.name}. It holds great value.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 75,
                goldAmount = 100,
                description = "Experience and gold reward"
            ),
            flavorText = when (theme) {
                DungeonTheme.CRYPT -> "Ancient treasures lie within these tombs."
                DungeonTheme.CASTLE -> "The royal vault holds many secrets."
                DungeonTheme.CAVE -> "Precious minerals await discovery."
                DungeonTheme.TEMPLE -> "Sacred relics must be recovered."
            }
        )
    }

    /**
     * Generate a quest to explore a space
     */
    private fun generateExploreQuest(worldState: WorldState, theme: DungeonTheme): Quest {
        // V3: Use spaces instead of rooms
        val spaces = worldState.spaces.toList()
        val targetSpace = spaces.randomOrNull(random) ?: return generateFallbackQuest(worldState, theme)
        val spaceId = targetSpace.first
        val space = targetSpace.second

        val title = questTitles[theme]?.randomOrNull(random) ?: "Explore the Unknown"
        val objective = QuestObjective.ExploreRoom(
            id = "obj_explore_$spaceId",
            description = "Explore the ${space.name}",
            targetRoomId = spaceId,
            targetRoomName = space.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Venture into the ${space.name} and discover what lies within.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 50,
                goldAmount = 25,
                description = "Experience and gold reward"
            ),
            flavorText = when (theme) {
                DungeonTheme.CRYPT -> "Map the forgotten chambers."
                DungeonTheme.CASTLE -> "Survey the ancient stronghold."
                DungeonTheme.CAVE -> "Chart the underground passages."
                DungeonTheme.TEMPLE -> "Discover the sacred mysteries."
            }
        )
    }

    /**
     * Generate a quest to talk to an NPC
     */
    private fun generateTalkQuest(worldState: WorldState, theme: DungeonTheme): Quest {
        // V3: Get all NPCs from global entity storage
        val friendlyNpcs = worldState.entities.values
            .filterIsInstance<Entity.NPC>()
            .filter { !it.isHostile }

        val targetNpc = friendlyNpcs.randomOrNull(random) ?: run {
            // Fallback if no friendly NPCs
            return generateExploreQuest(worldState, theme)
        }

        val title = "Seek Wisdom from ${targetNpc.name}"
        val objective = QuestObjective.TalkToNpc(
            id = "obj_talk_${targetNpc.id}",
            description = "Speak with ${targetNpc.name}",
            targetNpcId = targetNpc.id,
            targetName = targetNpc.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "${targetNpc.name} may have valuable information to share.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 30,
                goldAmount = 0,
                description = "Experience reward"
            ),
            flavorText = "Knowledge is its own reward."
        )
    }

    /**
     * Generate a quest to use a skill on a feature
     */
    private fun generateSkillQuest(worldState: WorldState, theme: DungeonTheme): Quest {
        // V3: Get all features from global entity storage
        val features = worldState.entities.values
            .filterIsInstance<Entity.Feature>()
            .filter { it.skillChallenge != null && !it.isCompleted }

        val targetFeature = features.randomOrNull(random) ?: run {
            // Fallback if no skill challenges
            return generateExploreQuest(worldState, theme)
        }

        val skillChallenge = targetFeature.skillChallenge!!
        val title = "Test Your ${skillChallenge.statType.name.lowercase().replaceFirstChar { it.uppercase() }}"
        val objective = QuestObjective.UseSkill(
            id = "obj_skill_${targetFeature.id}",
            description = "Successfully ${skillChallenge.statType.name.lowercase()} check on ${targetFeature.name}",
            skillType = skillChallenge.statType,
            targetFeatureId = targetFeature.id,
            targetName = targetFeature.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Overcome the challenge presented by the ${targetFeature.name}.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 60,
                goldAmount = 30,
                description = "Experience and gold reward"
            ),
            flavorText = "Only skill and courage will see you through."
        )
    }

    /**
     * Generate multiple quests for a dungeon
     */
    fun generateQuestPool(
        worldState: WorldState,
        theme: DungeonTheme,
        count: Int = 3
    ): List<Quest> {
        val quests = mutableListOf<Quest>()
        repeat(count) {
            quests.add(generateQuest(worldState, theme))
        }
        return quests
    }

    /**
     * Generate a safe fallback quest when the world lacks populated spaces (e.g., new repository-backed worlds)
     */
    private fun generateFallbackQuest(
        worldState: WorldState,
        theme: DungeonTheme
    ): Quest {
        val player = worldState.players.values.firstOrNull()
        val currentSpaceId = player?.currentRoomId ?: "unknown_space"
        val spaceName = worldState.getSpace(currentSpaceId)?.name ?: "Unknown Space"

        val title = questTitles[theme]?.randomOrNull(random) ?: "Scout the Abyss"

        val objective = QuestObjective.ExploreRoom(
            id = "obj_explore_$currentSpaceId",
            description = "Scout your immediate surroundings.",
            targetRoomId = currentSpaceId,
            targetRoomName = spaceName
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Before delving deeper, get your bearings and assess the area around you.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 20,
                goldAmount = 15,
                description = "Basic scouting reward"
            ),
            flavorText = "Even seasoned adventurers start by understanding where they stand."
        )
    }
}
