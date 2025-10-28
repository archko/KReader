package com.archko.reader.viewer

object TtsUtils {
    private val speechService: SpeechService by lazy { TtsQueueService() }
    
    fun speak(text: String, rate: Float = 0.5f, volume: Float = 0.8f, voice: String? = null) {
        speechService.setRate(rate)
        speechService.setVolume(volume)
        voice?.let { speechService.setVoice(it) }
        speechService.speak(text)
    }
    
    fun addToQueue(text: String) {
        speechService.addToQueue(text)
    }
    
    fun clearQueue() {
        speechService.clearQueue()
    }
    
    fun stop() {
        speechService.stop()
    }
    
    fun pause() {
        speechService.pause()
    }
    
    fun resume() {
        speechService.resume()
    }
    
    fun isSpeaking(): Boolean {
        return speechService.isSpeaking()
    }
    
    fun isPaused(): Boolean {
        return speechService.isPaused()
    }
    
    fun getQueueSize(): Int {
        return speechService.getQueueSize()
    }
    
    fun getCurrentText(): String? {
        return speechService.getCurrentText()
    }
    
    fun getAvailableVoices(): List<Voice> {
        return speechService.getAvailableVoices()
    }
}