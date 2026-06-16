package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workflow_presets")
data class WorkflowPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val jsonContent: String,
    val thumbnailPath: String? = null,
    val dateModified: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
)

@Entity(tableName = "generated_images")
data class GeneratedImage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val localPath: String,
    val prompt: String,
    val negativePrompt: String? = null,
    val steps: Int = 20,
    val cfg: Float = 7.0f,
    val width: Int = 512,
    val height: Int = 512,
    val seed: Long = 0L,
    val sampler: String = "euler",
    val timestamp: Long = System.currentTimeMillis(),
    val workflowName: String? = null,
    val isFavorite: Boolean = false
)

@Entity(tableName = "prompt_history")
data class PromptHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val prompt: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_prompts")
data class FavoritePrompt(
    @PrimaryKey val prompt: String,
    val timestamp: Long = System.currentTimeMillis()
)
