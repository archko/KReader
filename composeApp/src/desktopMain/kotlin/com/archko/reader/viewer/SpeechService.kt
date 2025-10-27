package com.archko.reader.viewer

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
    fun getAvailableVoices(): List<String>
    fun isSpeaking(): Boolean
    fun isPaused(): Boolean
    fun getQueueSize(): Int
    fun getCurrentText(): String?
}