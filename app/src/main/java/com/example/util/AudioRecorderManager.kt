package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordStartTime: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _recordingTimer = MutableStateFlow(0)
    val recordingTimer: StateFlow<Int> = _recordingTimer.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startRecording(outputFile: File): Boolean {
        try {
            stopAudio()
            stopRecording()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordStartTime = System.currentTimeMillis()
            _isRecording.value = true
            _recordingTimer.value = 0

            timerJob?.cancel()
            timerJob = scope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingTimer.value += 1
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error starting recording: ${e.message}", e)
            _isRecording.value = false
            return false
        }
    }

    fun stopRecording(): Int {
        if (!_isRecording.value) return 0
        val duration = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping recording: ${e.message}", e)
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            timerJob?.cancel()
        }
        return duration
    }

    fun playAudio(filePath: String, onComplete: () -> Unit = {}) {
        stopAudio()
        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPlayingPath.value = null
                    onComplete()
                }
                start()
            }
            mediaPlayer = player
            _isPlaying.value = true
            _currentPlayingPath.value = filePath
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error playing audio: ${e.message}", e)
            _isPlaying.value = false
            _currentPlayingPath.value = null
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping player: ${e.message}", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPlayingPath.value = null
        }
    }

    fun release() {
        stopRecording()
        stopAudio()
        scope.cancel()
    }
}
