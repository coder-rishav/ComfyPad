package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflow_presets ORDER BY dateModified DESC")
    fun getAllPresets(): Flow<List<WorkflowPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: WorkflowPreset): Long

    @Query("DELETE FROM workflow_presets WHERE id = :id")
    suspend fun deletePresetById(id: Int)

    @Update
    suspend fun updatePreset(preset: WorkflowPreset)
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM generated_images ORDER BY timestamp DESC")
    fun getAllImages(): Flow<List<GeneratedImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: GeneratedImage): Long

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteImageById(id: Int)

    @Query("DELETE FROM generated_images WHERE id IN (:ids)")
    suspend fun deleteImagesByIds(ids: List<Int>)

    @Update
    suspend fun updateImage(image: GeneratedImage)
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompt_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<PromptHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: PromptHistory)

    @Query("DELETE FROM prompt_history WHERE prompt = :prompt")
    suspend fun deleteHistoryItemByPrompt(prompt: String)

    @Query("SELECT * FROM favorite_prompts ORDER BY timestamp DESC")
    fun getFavoritePrompts(): Flow<List<FavoritePrompt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(item: FavoritePrompt)

    @Query("DELETE FROM favorite_prompts WHERE prompt = :prompt")
    suspend fun deleteFavorite(prompt: String)
}
