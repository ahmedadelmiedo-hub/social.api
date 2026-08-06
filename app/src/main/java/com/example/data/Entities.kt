package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val platform: String, // "Facebook", "YouTube", "Both"
    val personality: String, // e.g., "Sarcastic Critic", "Viral Growth Hacker"
    val description: String,
    val systemInstruction: String,
    val isCustom: Boolean = false,
    val specializedTask: String = "المهام العامة وإدارة المحتوى",
    val currentStatus: String = "جاهز للعمل"
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val agentId: String,
    val sender: String, // "user" or "agent"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_content")
data class SavedContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val platform: String, // "Facebook", "YouTube"
    val contentType: String, // "Caption", "Script", "Video Ideas", "Comment Reply"
    val content: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val agentName: String
)

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val durationSeconds: Int = 0,
    val dateRecorded: Long = System.currentTimeMillis()
)

data class TeamPipelineResult(
    val topic: String,
    val trendData: String = "",
    val scriptData: String = "",
    val hookData: String = "",
    val audioFilePath: String = "",
    val audioStatus: String = "",
    val montageData: String = "",
    val shortsData: String = "",
    val seoData: String = "",
    val videoFilePath: String = "",
    val analyticsData: String = "",
    val taskLogs: List<String> = emptyList()
)
