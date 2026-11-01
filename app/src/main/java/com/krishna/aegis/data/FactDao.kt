package com.krishna.aegis.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FactDao {
    @Query("SELECT factValue FROM user_facts WHERE factKey = :key LIMIT 1")
    suspend fun getFact(key: String): String?

    @Query("SELECT * FROM user_facts ORDER BY updatedAt DESC")
    suspend fun getAllFacts(): List<UserFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFact(fact: UserFact): Long

    @Query("DELETE FROM user_facts WHERE factKey = :key")
    suspend fun deleteFact(key: String)
}
