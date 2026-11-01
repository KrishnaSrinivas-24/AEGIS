package com.krishna.aegis.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills WHERE goalPattern = :normalizedGoal LIMIT 1")
    suspend fun findExactMatch(normalizedGoal: String): Skill?

    @Query("SELECT * FROM skills WHERE :normalizedGoal LIKE '%' || goalPattern || '%' OR goalPattern LIKE '%' || :normalizedGoal || '%' ORDER BY successCount DESC LIMIT 1")
    suspend fun findBestFuzzyMatch(normalizedGoal: String): Skill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: Skill): Long

    @Query("UPDATE skills SET successCount = successCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementSuccessCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM skills ORDER BY lastUsedAt DESC")
    suspend fun getAllSkills(): List<Skill>

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: Long)

    @Query("DELETE FROM skills")
    suspend fun clearAllSkills()
}
