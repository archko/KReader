package com.archko.reader.viewer

import kotlinx.serialization.Serializable

data class TtsTask(
    val text: String,
    val priority: Int = 0 // 0 = normal, 1 = high priority
)

@Serializable
data class Voice(
    val name: String,
    val countryCode: String,
    val description: String,
    val rate: Float = 0.25f,
    val volume: Float = 0.8f
) {
    override fun toString(): String {
        return "$name ($countryCode) - $description"
    }
}

interface SpeechService {
    fun speak(text: String)
    fun addToQueue(text: String)
    fun clearQueue()
    fun stop()
    fun pause()
    fun resume()
    fun setRate(rate: Float)
    fun setVolume(volume: Float)
    fun setVoice(voiceId: String)
    fun getAvailableVoices(): List<Voice>
    fun isSpeaking(): Boolean
    fun isPaused(): Boolean
    fun getQueueSize(): Int
    fun getCurrentText(): String?
    fun getDefaultVoice(): Voice
    suspend fun saveVoiceSetting(voice: Voice)
    suspend fun getVoiceSetting(): Voice?
}