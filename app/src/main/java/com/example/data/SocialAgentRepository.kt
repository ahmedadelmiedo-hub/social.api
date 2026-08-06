package com.example.data

import kotlinx.coroutines.flow.Flow

class SocialAgentRepository(private val socialAgentDao: SocialAgentDao) {
    val allAgents: Flow<List<AgentEntity>> = socialAgentDao.getAllAgents()
    val allSavedContent: Flow<List<SavedContentEntity>> = socialAgentDao.getAllSavedContent()
    val allVoiceNotes: Flow<List<VoiceNoteEntity>> = socialAgentDao.getAllVoiceNotes()

    fun getMessagesForAgent(agentId: String): Flow<List<ChatMessageEntity>> {
        return socialAgentDao.getMessagesForAgent(agentId)
    }

    suspend fun insertAgent(agent: AgentEntity) {
        socialAgentDao.insertAgent(agent)
    }

    suspend fun deleteAgent(agentId: String) {
        socialAgentDao.deleteAgentById(agentId)
    }

    suspend fun insertMessage(message: ChatMessageEntity) {
        socialAgentDao.insertMessage(message)
    }

    suspend fun deleteMessagesForAgent(agentId: String) {
        socialAgentDao.deleteMessagesForAgent(agentId)
    }

    suspend fun insertSavedContent(content: SavedContentEntity) {
        socialAgentDao.insertSavedContent(content)
    }

    suspend fun deleteSavedContent(id: Int) {
        socialAgentDao.deleteSavedContentById(id)
    }

    suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {
        socialAgentDao.insertVoiceNote(voiceNote)
    }

    suspend fun deleteVoiceNote(id: Int) {
        socialAgentDao.deleteVoiceNoteById(id)
    }
}
