package com.krishna.aegis.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_facts",
    indices = [Index(value = ["factKey"], unique = true)]
)
data class UserFact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val factKey: String,
    val factValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)
