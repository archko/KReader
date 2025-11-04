package com.archko.reader.pdf.tts

import com.archko.reader.pdf.entity.ReflowBean
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

public data class TtsTask(
    val reflowBean: ReflowBean,
    val priority: Int = 0 // 0 = normal, 1 = high priority
)

@Serializable
public data class Voice(
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

public interface SpeechService {
    public val isSpeakingFlow: StateFlow<Boolean>

    public fun speak(reflowBean: ReflowBean)
    public fun addToQueue(reflowBean: ReflowBean)
    public fun clearQueue()
    public fun stop()
    public fun pause()
    public fun resume()
    public fun setRate(rate: Float)
    public fun setVolume(volume: Float)
    public fun setVoice(voiceId: String)
    public fun getAvailableVoices(): List<Voice>
    public fun isSpeaking(): Boolean
    public fun isPaused(): Boolean
    public fun getQueueSize(): Int
    public fun getQueue(): List<TtsTask>
    public fun getCurrentReflowBean(): ReflowBean?
    public fun getDefaultVoice(): Voice
    public suspend fun saveVoiceSetting(voice: Voice)
    public suspend fun getVoiceSetting(): Voice
}