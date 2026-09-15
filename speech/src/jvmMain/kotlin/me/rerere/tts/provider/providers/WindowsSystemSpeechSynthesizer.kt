package me.rerere.tts.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.rerere.tts.provider.TTSProviderSetting
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Windows PowerShell ships with Windows and exposes its installed System.Speech voices. */
internal class WindowsSystemSpeechSynthesizer(
    private val startProcess: (ProcessBuilder) -> Process = { it.start() },
) {
    suspend fun synthesize(setting: TTSProviderSetting.SystemTTS, text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val directory = Files.createTempDirectory("rikkahub-system-tts-")
            val input = directory.resolve("input.xml")
            val output = directory.resolve("output.wav")
            var process: Process? = null
            try {
                Files.writeString(input, windowsSpeechMarkup(setting, text))
                val windowsDirectory = System.getenv("SystemRoot") ?: "C:\\Windows"
                val executable = Path.of(windowsDirectory, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
                val builder = ProcessBuilder(
                    executable.toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                    Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE)),
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                builder.environment()["RIKKAHUB_TTS_INPUT"] = input.toString()
                builder.environment()["RIKKAHUB_TTS_OUTPUT"] = output.toString()
                process = startProcess(builder)
                val exitCode = runInterruptible { process.waitFor() }
                check(exitCode == 0) { "Windows system TTS failed (exit code $exitCode)" }
                Files.readAllBytes(output)
            } finally {
                process?.let {
                    if (it.isAlive) {
                        it.destroyForcibly()
                        it.waitFor()
                    }
                }
                Files.deleteIfExists(input)
                Files.deleteIfExists(output)
                Files.deleteIfExists(directory)
            }
        }

    companion object {
        // Text and paths are passed as data; neither is interpolated into executable PowerShell code.
        private val SCRIPT = """
            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Speech
            ${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
            try {
                ${'$'}content = [IO.File]::ReadAllText(${'$'}env:RIKKAHUB_TTS_INPUT, [Text.Encoding]::UTF8)
                ${'$'}ssml = '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="' + ${'$'}synth.Voice.Culture.Name + '">' + ${'$'}content + '</speak>'
                ${'$'}synth.SetOutputToWaveFile(${'$'}env:RIKKAHUB_TTS_OUTPUT)
                ${'$'}synth.SpeakSsml(${'$'}ssml)
            } finally {
                ${'$'}synth.Dispose()
            }
        """.trimIndent()
    }
}

internal fun windowsSpeechMarkup(setting: TTSProviderSetting.SystemTTS, text: String): String {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    val rate = setting.speechRate * 100
    val pitch = (setting.pitch - 1) * 100
    val pitchChange = if (pitch >= 0) "+$pitch" else pitch.toString()
    return "<prosody rate=\"$rate%\" pitch=\"$pitchChange%\">$escaped</prosody>"
}
