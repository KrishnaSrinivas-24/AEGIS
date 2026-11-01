package com.krishna.aegis.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skills",
    indices = [Index(value = ["goalPattern"], unique = true)]
)
data class Skill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalPattern: String,
    val planJson: String,
    val successCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
