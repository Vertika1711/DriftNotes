package com.driftnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Float,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
