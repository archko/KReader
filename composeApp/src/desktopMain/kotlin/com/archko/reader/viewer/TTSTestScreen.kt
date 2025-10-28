package com.archko.reader.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TtsTestScreen() {
    val speechService: SpeechService = remember { TtsQueueService() }

    var textToSpeak by remember { mutableStateOf("你好，这是中文语音测试。Hello, this is a bilingual test.") }
    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var queueSize by remember { mutableStateOf(0) }
    var currentText by remember { mutableStateOf<String?>(null) }
    var rate by remember { mutableStateOf(0.25f) }
    var volume by remember { mutableStateOf(0.8f) }
    var availableVoices by remember { mutableStateOf(emptyList<Voice>()) }
    var selectedVoice by remember { mutableStateOf("Mei-Jia") }
    var showVoiceDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availableVoices = speechService.getAvailableVoices()
        if (availableVoices.isNotEmpty()) {
            // 优先选择中文语音
            val chineseVoices = listOf("Ting-Ting", "Sin-ji", "Mei-Jia", "Li-mu", "Yu-shu")
            val availableVoiceNames = availableVoices.map { it.name }
            selectedVoice = chineseVoices.firstOrNull { it in availableVoiceNames } 
                ?: availableVoices.first().name
            speechService.setVoice(selectedVoice)
        }
    }

    // 监听说话状态和队列状态
    LaunchedEffect(Unit) {
        while (true) {
            isSpeaking = speechService.isSpeaking()
            isPaused = speechService.isPaused()
            queueSize = speechService.getQueueSize()
            currentText = speechService.getCurrentText()
            delay(200)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = textToSpeak,
            onValueChange = { textToSpeak = it },
            label = { Text("Text to speak") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = {
                    speechService.setRate(rate)
                    speechService.setVolume(volume)
                    speechService.setVoice(selectedVoice)
                    speechService.speak(textToSpeak)
                }
            ) {
                Text("Speak")
            }

            Button(
                onClick = {
                    speechService.setRate(rate)
                    speechService.setVolume(volume)
                    speechService.setVoice(selectedVoice)
                    speechService.addToQueue(textToSpeak)
                }
            ) {
                Text("Add to Queue")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = {
                    if (isPaused) speechService.resume() else speechService.pause()
                },
                enabled = isSpeaking || isPaused
            ) {
                Text(if (isPaused) "Resume" else "Pause")
            }

            Button(
                onClick = {
                    speechService.stop()
                },
                enabled = isSpeaking || isPaused || queueSize > 0
            ) {
                Text("Stop")
            }

            Button(
                onClick = {
                    speechService.clearQueue()
                }
            ) {
                Text("Clear Queue")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Rate: ${"%.2f".format(rate)}")
        Slider(
            value = rate,
            onValueChange = { rate = it },
            valueRange = 0.1f..1.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Volume: ${"%.2f".format(volume)}")
        Slider(
            value = volume,
            onValueChange = { volume = it },
            valueRange = 0.0f..1.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (availableVoices.isNotEmpty()) {
            Text("Voice: $selectedVoice")
            Box {
                Button(
                    onClick = { showVoiceDropdown = true }
                ) {
                    Text("Select Voice")
                }
                
                DropdownMenu(
                    expanded = showVoiceDropdown,
                    onDismissRequest = { showVoiceDropdown = false }
                ) {
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.toString()) },
                            onClick = {
                                selectedVoice = voice.name
                                speechService.setVoice(voice.name)
                                showVoiceDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // 状态显示
        Column {
            Text("Status: ${when {
                isSpeaking -> "Speaking"
                isPaused -> "Paused"
                else -> "Idle"
            }}", color = MaterialTheme.colorScheme.primary)
            
            Text("Queue Size: $queueSize")
            
            currentText?.let { text ->
                Text(
                    "Current: ${text.take(30)}${if (text.length > 30) "..." else ""}",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}