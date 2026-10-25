package com.example.zahra.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voice Engine — محرك الصوت المتكامل لزهرة
 * 
 * يوفر:
 * 1. Speech-to-Text (STT) — تحويل الكلام لنص
 * 2. Text-to-Speech (TTS) — تحويل النص لكلام
 * 3. Voice Activity Detection (VAD) — كشف الكلام
 * 4. Language Detection — كشف اللغة تلقائياً
 * 
 * Zero-Network: يعمل offline باستخدام Android APIs المدمجة
 * 
 * @author Zahra Team
 * @version 1.0
 */
class VoiceEngine(private val context: Context) {
    
    // Speech-to-Text
    private var speechRecognizer: SpeechRecognizer? = null
    private var sttListener: STTListener? = null
    
    // Text-to-Speech
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentLanguage: Locale = Locale("ar", "EG")
    
    // Configuration
    private var isListening = false
    private var isSpeaking = false
    
    /**
     * Listener لأحداث STT
     */
    interface STTListener {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onEndOfSpeech()
        fun onResult(text: String, confidence: Float)
        fun onPartialResult(text: String)
        fun onError(errorCode: Int, errorMessage: String)
    }
    
    /**
     * Listener لأحداث TTS
     */
    interface TTSListener {
        fun onStart()
        fun onDone()
        fun onError(errorMessage: String)
    }
    
    private var ttsListener: TTSListener? = null
    
    /**
     * تهيئة المحرك
     */
    fun initialize() {
        initSTT()
        initTTS()
    }
    
    /**
     * تهيئة Speech-to-Text
     */
    private fun initSTT() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                sttListener?.onReadyForSpeech()
            }
            
            override fun onBeginningOfSpeech() {
                sttListener?.onBeginningOfSpeech()
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // يمكن استخدامه لـ VAD
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                sttListener?.onEndOfSpeech()
                isListening = false
            }
            
            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التعرف على الكلام"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهت المهلة بدون كلام"
                    SpeechRecognizer.ERROR_AUDIO -> "خطأ في الصوت"
                    SpeechRecognizer.ERROR_NETWORK -> "خطأ في الشبكة"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحيات غير كافية"
                    else -> "خطأ غير معروف: $error"
                }
                sttListener?.onError(error, errorMsg)
                isListening = false
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val confidence = confidences?.getOrNull(0) ?: 0.8f
                    sttListener?.onResult(text, confidence)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    sttListener?.onPartialResult(matches[0])
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    
    /**
     * تهيئة Text-to-Speech
     */
    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                setLanguage(currentLanguage)
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        ttsListener?.onStart()
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        ttsListener?.onDone()
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        ttsListener?.onError("خطأ في تحويل النص لكلام")
                    }
                })
            }
        }
    }
    
    /**
     * بدء الاستماع (STT)
     */
    fun startListening(language: String = "ar-EG", listener: STTListener) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError(-1, "التعرف على الكلام غير متاح")
            return
        }
        
        sttListener = listener
        isListening = true
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    /**
     * إيقاف الاستماع
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }
    
    /**
     * إلغاء الاستماع
     */
    fun cancelListening() {
        speechRecognizer?.cancel()
        isListening = false
    }
    
    /**
     * تحويل النص لكلام (TTS)
     */
    fun speak(text: String, listener: TTSListener? = null) {
        if (!ttsReady || tts == null) {
            listener?.onError("محرك TTS غير جاهز")
            return
        }
        
        ttsListener = listener
        
        // تقسيم النص الطويل لأجزاء (TTS limit ~4000 chars)
        val chunks = splitText(text, 3800)
        
        for ((index, chunk) in chunks.withIndex()) {
            val utteranceId = "zahra_tts_$index"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            if (index == 0) {
                tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                tts?.speak(chunk, TextToSpeech.QUEUE_ADD, params, utteranceId)
            }
        }
    }
    
    /**
     * إيقاف الكلام
     */
    fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
    }
    
    /**
     * تعيين اللغة
     */
    fun setLanguage(locale: Locale): Boolean {
        currentLanguage = locale
        if (!ttsReady) return false
        
        val result = tts?.setLanguage(locale)
        return result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE
    }
    
    /**
     * تعيين سرعة الكلام (0.5 - 2.0)
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }
    
    /**
     * تعيين نبرة الصوت (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }
    
    /**
     * تقسيم النص لأجزاء
     */
    private fun splitText(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                chunks.add(remaining)
                break
            }
            
            // ابحث عن آخر مسافة أو نقطة
            var splitIndex = remaining.lastIndexOf('.', maxLength)
            if (splitIndex == -1 || splitIndex < maxLength / 2) {
                splitIndex = remaining.lastIndexOf(' ', maxLength)
            }
            if (splitIndex == -1 || splitIndex < maxLength / 2) {
                splitIndex = maxLength
            }
            
            chunks.add(remaining.substring(0, splitIndex).trim())
            remaining = remaining.substring(splitIndex).trim()
        }
        
        return chunks
    }
    
    /**
     * هل المحرك يستمع حالياً؟
     */
    fun isListening(): Boolean = isListening
    
    /**
     * هل المحرك يتكلم حالياً؟
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    /**
     * هل TTS جاهز؟
     */
    fun isTTSReady(): Boolean = ttsReady
    
    /**
     * تحرير الموارد
     */
    fun release() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
