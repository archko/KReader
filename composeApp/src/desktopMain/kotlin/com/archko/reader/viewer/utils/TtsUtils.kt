package com.archko.reader.viewer.utils

import com.archko.reader.viewer.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class TtsUtils {

    companion object {
        fun forceKillAllTtsProcesses(isWindows: Boolean) {
            try {
                if (isWindows) {
                    // Windows: 更精确地终止 TTS 相关的 PowerShell 进程
                    ProcessBuilder(
                        "powershell", "-Command",
                        "Get-Process | Where-Object {${'$'}_.ProcessName -eq 'powershell' -and ${'$'}_.CommandLine -like '*Speech*'} | Stop-Process -Force"
                    ).start().waitFor()

                    // 备用方案：终止所有 PowerShell 进程（可能影响其他应用）
                    // ProcessBuilder("taskkill", "/F", "/IM", "powershell.exe").start()
                } else {
                    // macOS: 终止所有 say 进程
                    ProcessBuilder("pkill", "say").start().waitFor()

                    // 如果 pkill 不工作，尝试 killall
                    ProcessBuilder("killall", "say").start().waitFor()
                }
                println("TTS: Force killed all TTS processes")
            } catch (e: Exception) {
                println("TTS: Failed to force kill TTS processes: ${e.message}")
            }
        }

        fun getConfigFilePath(isWindows: Boolean): String {
            val userHome = System.getProperty("user.home")
            return if (isWindows) {
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                "$appData\\KReader\\tts_voice_setting.json"
            } else {
                // macOS
                "$userHome/Library/Application Support/KReader/tts_voice_setting.json"
            }
        }

        suspend fun saveVoiceSetting(voice: Voice, isWindows: Boolean) = withContext(Dispatchers.IO) {
            try {
                val configFile = File(getConfigFilePath(isWindows))
                configFile.parentFile?.mkdirs()

                // 添加调试信息
                println("TTS: Saving voice - name=${voice.name}, rate=${voice.rate}, volume=${voice.volume}")

                val json = Json {
                    prettyPrint = true
                    encodeDefaults = true  // 强制编码默认值
                }
                val jsonString = json.encodeToString(voice)
                configFile.writeText(jsonString)

                println("TTS: Voice setting saved to ${configFile.absolutePath}")
                println("TTS: Saved JSON content: $jsonString")
            } catch (e: Exception) {
                println("TTS: Failed to save voice setting: ${e.message}")
            }
        }

        /**
         * 提取文本中有意义的部分，忽略装饰性字符
         */
        fun extractMeaningfulText(text: String): String {
            // 按行分割，处理每一行
            return text.lines()
                .map { line ->
                    line
                        // 移除装饰性的重复字符
                        .replace(Regex("[-=*#_]{3,}"), "")
                        // 移除count信息
                        .replace(Regex("count\\d*:\\d+"), "")
                        // 保留有意义的内容
                        .replace(Regex("[^\\u4e00-\\u9fff\\w\\s:/.，。！？]"), " ")
                        .trim()
                }
                .filter { it.isNotBlank() && it.length > 2 }  // 过滤掉空行和太短的行
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /**
         * 清理文本中的特殊字符，使其适合 TTS 朗读
         */
        fun cleanTextForTts(text: String): String {
            return text
                // 首先处理长串的重复字符（如长破折号）
                .replace(Regex("-{3,}"), "")  // 移除3个或更多连续的破折号
                .replace(Regex("={3,}"), "")  // 移除3个或更多连续的等号
                .replace(Regex("\\*{3,}"), "")  // 移除3个或更多连续的星号
                .replace(Regex("#{3,}"), "")  // 移除3个或更多连续的井号
                .replace(Regex("_{3,}"), "")  // 移除3个或更多连续的下划线

                // 移除或替换特殊符号
                .replace("---", "")  // 移除长破折号
                .replace("--", "")   // 移除双破折号
                .replace("—", "")    // 移除em dash
                .replace("–", "")    // 移除en dash
                .replace("…", "")    // 移除省略号
                .replace("　", " ")   // 全角空格转半角空格

                // 处理括号内容 - 可以选择保留或移除
                .replace(Regex("（[^）]*）"), "")  // 移除全角括号及内容
                .replace(Regex("\\([^)]*\\)"), "")  // 移除半角括号及内容

                // 处理标点符号
                .replace("，", ",")   // 全角逗号转半角
                .replace("。", ".")   // 全角句号转半角
                .replace("；", ";")   // 全角分号转半角
                .replace("：", ":")   // 全角冒号转半角
                .replace("？", "?")   // 全角问号转半角
                .replace("！", "!")   // 全角感叹号转半角

                // 移除多余的空白字符
                .replace(Regex("\\s+"), " ")  // 多个空格合并为一个
                .trim()  // 移除首尾空格

                // 如果文本为空或太短，提供默认文本
                .let { cleaned ->
                    if (cleaned.isBlank() || cleaned.length < 2) {
                        "无法识别的文本内容"
                    } else {
                        cleaned
                    }
                }
        }

        fun filterChineseAndEnglishVoices(voices: List<Voice>): List<Voice> {
            return voices.filter { voice ->
                val countryCode = voice.countryCode.lowercase()
                // 中文：zh-cn, zh-tw, zh-hk, zh-sg 等
                // 英文：en-us, en-gb, en-au, en-ca 等
                countryCode.startsWith("zh") || countryCode.startsWith("en")
            }
        }

        fun getWindowsVoices(): List<Voice> {
            return try {
                val process = ProcessBuilder(
                    "powershell",
                    "-Command",
                    """
                Add-Type -AssemblyName System.Speech;
                (New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | ForEach-Object {
                    ${'$'}voice = ${'$'}_.VoiceInfo;
                    "${'$'}(${'$'}voice.Name)|${'$'}(${'$'}voice.Culture.Name)|${'$'}(${'$'}voice.Description)"
                }
                """.trimIndent()
                ).start()

                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                output.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.trim().split("|")
                        if (parts.size >= 3) {
                            Voice(
                                name = parts[0],
                                countryCode = parts[1],
                                description = parts[2]
                            )
                        } else null
                    }
            } catch (e: Exception) {
                println("Failed to get Windows voices: ${e.message}")
                getDefaultWindowsVoices()
            }
        }

        fun getMacVoices(): List<Voice> {
            return try {
                val process = ProcessBuilder("say", "-v", "?").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                output.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        parseMacVoiceLine(line.trim())
                    }
            } catch (e: Exception) {
                println("Failed to get Mac voices: ${e.message}")
                getDefaultMacVoices()
            }
        }

        fun parseMacVoiceLine(line: String): Voice? {
            // macOS say -v ? 输出格式通常是: "VoiceName    language_code    # description"
            // 例如: "Alex                 en_US    # Most people recognize me by my voice."
            // 或者: "Ting-Ting            zh_CN    # 普通话（中国大陆）- 女声"

            return try {
                val parts = line.split("#", limit = 2)
                val voiceInfo = parts[0].trim()
                val description = if (parts.size > 1) parts[1].trim() else ""

                // 分离语音名称和语言代码
                val voiceParts = voiceInfo.split("\\s+".toRegex())
                if (voiceParts.size >= 2) {
                    val name = voiceParts[0]
                    val countryCode = voiceParts[voiceParts.size - 1]

                    Voice(
                        name = name,
                        countryCode = countryCode,
                        description = description.ifEmpty { "System voice" }
                    )
                } else {
                    // 如果解析失败，至少返回语音名称
                    Voice(
                        name = voiceParts[0],
                        countryCode = "unknown",
                        description = description.ifEmpty { "System voice" }
                    )
                }
            } catch (e: Exception) {
                println("Failed to parse voice line: $line, error: ${e.message}")
                null
            }
        }

        fun getDefaultWindowsVoices(): List<Voice> {
            return listOf(
                Voice("Microsoft David Desktop", "en-US", "English (United States) - Male"),
                Voice("Microsoft Zira Desktop", "en-US", "English (United States) - Female"),
                Voice("Microsoft Mark Desktop", "en-US", "English (United States) - Male"),
                Voice("Microsoft Huihui Desktop", "zh-CN", "Chinese (Simplified) - Female"),
                Voice("Microsoft Yaoyao Desktop", "zh-CN", "Chinese (Simplified) - Female"),
                Voice("Microsoft Kangkang Desktop", "zh-CN", "Chinese (Simplified) - Male")
            )
        }

        fun getDefaultMacVoices(): List<Voice> {
            return listOf(
                Voice("Alex", "en-US", "English (United States) - Male"),
                Voice("Samantha", "en-US", "English (United States) - Female"),
                Voice("Victoria", "en-US", "English (United States) - Female"),
                Voice("Daniel", "en-GB", "English (United Kingdom) - Male"),
                Voice("Karen", "en-AU", "English (Australia) - Female"),
                Voice("Moira", "en-IE", "English (Ireland) - Female"),
                Voice("Tessa", "en-ZA", "English (South Africa) - Female"),
                Voice("Ting-Ting", "zh-CN", "Chinese (Simplified) - Female"),
                Voice("Sin-ji", "zh-HK", "Chinese (Hong Kong) - Female"),
                Voice("Mei-Jia", "zh-TW", "Chinese (Traditional) - Female"),
                Voice("Li-mu", "zh-CN", "Chinese (Simplified) - Male"),
                Voice("Yu-shu", "zh-CN", "Chinese (Simplified) - Female")
            )
        }

        fun createWindowsCommand(text: String, voice: String, rate: Float, volume: Float): Array<String> {
            val rateValue = (rate * 10).toInt() // Windows rate range 0-10
            val volumeValue = (volume * 100).toInt() // Windows volume 0-100

            // 转义文本中的特殊字符，更全面的转义
            val escapedText = text
                .replace("\\", "\\\\")  // 反斜杠
                .replace("'", "''")     // 单引号
                .replace("\"", "`\"")   // 双引号
                .replace("`", "``")     // 反引号
                .replace("$", "`$")     // 美元符号
                .replace("\n", " ")     // 换行符
                .replace("\r", " ")     // 回车符
                .replace("\t", " ")     // 制表符

            // 使用 PowerShell 调用 Windows Speech API，添加进程管理
            return arrayOf(
                "powershell",
                "-Command",
                """
            Add-Type -AssemblyName System.Speech;
            ${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer;
            try {
                ${'$'}synth.SelectVoice('$voice');
                ${'$'}synth.Rate = $rateValue;
                ${'$'}synth.Volume = $volumeValue;
                ${'$'}synth.Speak('$escapedText');
            } finally {
                ${'$'}synth.Dispose();
            }
            """.trimIndent()
            )
        }

        fun createMacCommand(text: String, voice: String, rate: Float): Array<String> {
            val rateValue = (rate * 400 + 100).toInt() // Mac rate range 100-500

            // 对于 macOS say 命令，处理特殊字符
            val processedText = text
                .replace("\\", "\\\\")  // 反斜杠
                .replace("\"", "\\\"")  // 双引号
                .replace("'", "\\'")    // 单引号
                .replace("`", "\\`")    // 反引号
                .replace("$", "\\$")    // 美元符号
                .replace("\n", " ")     // 换行符
                .replace("\r", " ")     // 回车符
                .replace("\t", " ")     // 制表符

            return arrayOf(
                "say",
                "-v", voice,
                "-r", rateValue.toString(),
                processedText
            )
        }

        fun createManagedProcess(isWindows: Boolean, command: Array<String>): Process {
            val processBuilder = ProcessBuilder(*command)

            if (isWindows) {
                // Windows: 创建新的进程组，当父进程退出时子进程也会退出
                processBuilder.environment()["CREATE_NEW_PROCESS_GROUP"] = "true"
            } else {
                // macOS/Linux: 设置进程组，使子进程在父进程退出时收到 SIGHUP
                // 这里我们通过 shell 包装来确保进程能被正确清理
                val wrappedCommand = arrayOf(
                    "sh", "-c",
                    "trap 'kill 0' TERM; ${command.joinToString(" ")} & wait"
                )
                return ProcessBuilder(*wrappedCommand).start()
            }

            return processBuilder.start()
        }
    }
}