package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BenDao {
    @Query("SELECT * FROM ben_state WHERE id = 1 LIMIT 1")
    fun getBenStateFlow(): Flow<BenStateEntity?>

    @Query("SELECT * FROM ben_state WHERE id = 1 LIMIT 1")
    suspend fun getBenState(): BenStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBenState(state: BenStateEntity)
}
