package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BenRepository(private val benDao: BenDao) {

    val benStateFlow: Flow<BenStateEntity> = benDao.getBenStateFlow().map { entity ->
        entity ?: BenStateEntity() // Fallback if no row exists yet
    }

    suspend fun getBenState(): BenStateEntity {
        return benDao.getBenState() ?: BenStateEntity()
    }

    suspend fun saveBenState(state: BenStateEntity) {
        benDao.saveBenState(state)
    }

    suspend fun feedBen(itemType: String): Result<BenStateEntity> {
        val current = getBenState()
        val hungerGain = when (itemType) {
            "carrot" -> 0.15f
            "cookie" -> 0.30f
            "cupcake" -> 0.40f
            else -> 0.10f
        }
        val cost = when (itemType) {
            "carrot" -> 10
            "cookie" -> 25
            "cupcake" -> 45
            else -> 0
        }

        if (current.coins < cost) {
            return Result.failure(Exception("Not enough coins!"))
        }

        val newHunger = (current.hunger + hungerGain).coerceAtMost(1.0f)
        val newXp = current.xp + 15
        val (newLevel, updatedXp) = calculateLevelProgress(current.level, newXp)
        
        val updated = current.copy(
            hunger = newHunger,
            coins = current.coins - cost,
            xp = updatedXp,
            level = newLevel,
            happiness = (current.happiness + 0.05f).coerceAtMost(1.0f)
        )
        saveBenState(updated)
        return Result.success(updated)
    }

    suspend fun playMiniGame(earnedCoins: Int): BenStateEntity {
        val current = getBenState()
        val newXp = current.xp + (earnedCoins * 2)
        val (newLevel, updatedXp) = calculateLevelProgress(current.level, newXp)
        
        val updated = current.copy(
            coins = current.coins + earnedCoins,
            xp = updatedXp,
            level = newLevel,
            happiness = (current.happiness + 0.25f).coerceAtMost(1.0f),
            energy = (current.energy - 0.20f).coerceAtLeast(0.1f)
        )
        saveBenState(updated)
        return updated
    }

    suspend fun petBen(): BenStateEntity {
        val current = getBenState()
        val updated = current.copy(
            happiness = (current.happiness + 0.10f).coerceAtMost(1.0f),
            xp = current.xp + 5
        ).let {
            val (lvl, xp) = calculateLevelProgress(it.level, it.xp)
            it.copy(level = lvl, xp = xp)
        }
        saveBenState(updated)
        return updated
    }

    suspend fun putToSleep(): BenStateEntity {
        val current = getBenState()
        val updated = current.copy(
            energy = 1.0f,
            hunger = (current.hunger - 0.10f).coerceAtLeast(0.0f)
        )
        saveBenState(updated)
        return updated
    }

    suspend fun purchaseOutfit(outfitId: String, cost: Int): Result<BenStateEntity> {
        val current = getBenState()
        val unlockedList = current.unlockedOutfitsCsv.split(",").toMutableSet()
        if (unlockedList.contains(outfitId)) {
            // Already owned, just equip
            val updated = current.copy(selectedOutfitId = outfitId)
            saveBenState(updated)
            return Result.success(updated)
        }

        if (current.coins < cost) {
            return Result.failure(Exception("Need $cost coins! You only have ${current.coins}."))
        }

        unlockedList.add(outfitId)
        val updated = current.copy(
            coins = current.coins - cost,
            unlockedOutfitsCsv = unlockedList.joinToString(","),
            selectedOutfitId = outfitId,
            happiness = (current.happiness + 0.15f).coerceAtMost(1.0f)
        )
        saveBenState(updated)
        return Result.success(updated)
    }

    suspend fun changeEnvironment(enviroId: String): BenStateEntity {
        val current = getBenState()
        val updated = current.copy(environmentId = enviroId)
        saveBenState(updated)
        return updated
    }

    suspend fun claimDailyReward(rewardCoins: Int): BenStateEntity {
        val current = getBenState()
        val updated = current.copy(
            coins = current.coins + rewardCoins,
            happiness = 1.0f
        )
        saveBenState(updated)
        return updated
    }

    private fun calculateLevelProgress(currentLevel: Int, currentXp: Int): Pair<Int, Int> {
        var level = currentLevel.coerceAtLeast(1)
        var xp = currentXp.coerceAtLeast(0)
        var xpNeeded = level * 100
        var iterations = 0
        while (xp >= xpNeeded && iterations < 500) {
            xp -= xpNeeded
            level++
            xpNeeded = level * 100
            iterations++
        }
        return Pair(level, xp)
    }
}
