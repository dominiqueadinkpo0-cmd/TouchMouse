package com.touchmouse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import android.media.MediaRecorder
import android.os.Build

/**
 * Whisper Flow like : appui maintenu -> enregistre, relâche -> transcrit -> injecte
 * Deux modes :
 * - offline_google : SpeechRecognizer Android (gratuit, offline si pack langue installé, rapide)
 * - whisper_api : enregistre audio puis envoie à OpenAI Whisper API (plus précis, multilingue)
 */
class WhisperHelper(
    private val ctx: Context,
    private val onResult: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Pour mode whisper_api
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun isListening() = isListening

    fun startListening(mode: String, language: String, apiKey: String) {
        if (isListening) return
        isListening = true
        if (mode == "whisper_api" && apiKey.isNotBlank()) {
            startRecording()
        } else {
            startGoogleSTT(language)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try { speechRecognizer?.stopListening() } catch(_:Exception){}
        if (mediaRecorder != null) {
            stopRecordingAndTranscribe()
        }
    }

    private fun startGoogleSTT(language: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Log.e("WhisperHelper", "STT not available")
            onResult("STT non disponible sur cet appareil")
            isListening = false
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object: RecognitionListener{
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Log.e("WhisperHelper", "STT error $error")
                    isListening = false
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) onResult(text)
                    isListening = false
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val lang = when(language){
            "en" -> "en-US"
            "fr" -> "fr-FR"
            else -> if(language=="auto") "" else "fr-FR"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if(lang.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // offline si possible
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try { speechRecognizer?.startListening(intent) } catch(e:Exception){ Log.e("WhisperHelper","start failed",e); isListening=false }
    }

    // ===== Whisper API mode =====
    private fun startRecording(){
        try{
            audioFile = File(ctx.cacheDir, "whisper_${System.currentTimeMillis()}.m4a")
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(16000)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch(e:Exception){
            Log.e("WhisperHelper","recorder failed",e)
            // fallback Google
            startGoogleSTT("fr")
        }
    }

    private fun stopRecordingAndTranscribe(){
        try{
            mediaRecorder?.apply { stop(); release() }
        } catch(_:Exception){}
        mediaRecorder=null
        val f = audioFile ?: return
        if(!f.exists() || f.length() < 800){
            Log.w("WhisperHelper","audio too short")
            return
        }
        scope.launch(Dispatchers.IO){
            try{
                val prefs = ctx.dataStore.data.let { kotlinx.coroutines.flow.first(it).let { map -> map } } // dummy to get apiKey already passed? We'll capture via closure
                // On a déjà apiKey en param, mais après stop on l'a perdu ; on récupère depuis datastore
                val apiKey = ctx.prefsFlow().let { kotlinx.coroutines.flow.first(it).whisperApiKey }
                val lang = ctx.prefsFlow().let { kotlinx.coroutines.flow.first(it).whisperLanguage }
                val text = transcribeWhisperAPI(f, apiKey, lang)
                withContext(Dispatchers.Main){
                    if(text.isNotBlank()) onResult(text)
                }
            } catch(e:Exception){
                Log.e("WhisperHelper","transcribe failed",e)
                withContext(Dispatchers.Main){
                    // fallback
                    onResult("Erreur transcription: ${e.message}")
                }
            } finally {
                try{ f.delete() }catch(_:Exception){}
            }
        }
    }

    private suspend fun transcribeWhisperAPI(file: File, apiKey: String, language: String): String = withContext(Dispatchers.IO){
        if(apiKey.isBlank()) throw IllegalStateException("Clé API manquante")
        val boundary = "----TouchMouse${System.currentTimeMillis()}"
        val url = URL("https://api.openai.com/v1/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connectTimeout = 25000; readTimeout = 25000
        }
        DataOutputStream(conn.outputStream).use { out ->
            fun writeField(name:String, value:String){
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.writeBytes("$value\r\n")
            }
            writeField("model","whisper-1")
            if(language!="auto") writeField("language", language)
            // file
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n")
            out.writeBytes("Content-Type: audio/m4a\r\n\r\n")
            FileInputStream(file).use { it.copyTo(out) }
            out.writeBytes("\r\n")
            out.writeBytes("--$boundary--\r\n")
            out.flush()
        }
        val code = conn.responseCode
        val resp = (if(code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if(code !in 200..299) throw IOException("Whisper API $code: $resp")
        // {"text":"..."}
        val json = JSONObject(resp)
        json.optString("text","").trim()
    }

    fun destroy(){
        try{ speechRecognizer?.destroy() }catch(_:Exception){}
        try{ mediaRecorder?.release() }catch(_:Exception){}
        scope.cancel()
    }
}
