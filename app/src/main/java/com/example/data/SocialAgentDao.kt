package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialAgentDao {
    // Agents
    @Query("SELECT * FROM agents ORDER BY isCustom ASC, name ASC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :agentId")
    suspend fun deleteAgentById(agentId: String)

    // Chat Messages
    @Query("SELECT * FROM chat_messages WHERE agentId = :agentId ORDER BY timestamp ASC")
    fun getMessagesForAgent(agentId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE agentId = :agentId")
    suspend fun deleteMessagesForAgent(agentId: String)

    // Saved Content
    @Query("SELECT * FROM saved_content ORDER BY dateCreated DESC")
    fun getAllSavedContent(): Flow<List<SavedContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedContent(content: SavedContentEntity)

    @Query("DELETE FROM saved_content WHERE id = :id")
    suspend fun deleteSavedContentById(id: Int)

    // Voice Notes
    @Query("SELECT * FROM voice_notes ORDER BY dateRecorded DESC")
    fun getAllVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteVoiceNoteById(id: Int)
}
