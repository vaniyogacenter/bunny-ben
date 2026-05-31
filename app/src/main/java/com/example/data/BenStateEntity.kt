package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ben_state")
data class BenStateEntity(
    @PrimaryKey val id: Int = 1,
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 100,
    val hunger: Float = 0.8f,     // 1.0f is full, 0.0f is starving
    val happiness: Float = 0.8f,  // 1.0f is ecstatic, 0.0f is sad
    val energy: Float = 0.8f,     // 1.0f is wide awake, 0.0f is asleep
    val selectedOutfitId: String = "classic",
    val unlockedOutfitsCsv: String = "classic",
    val environmentId: String = "classic_meadow"
)
