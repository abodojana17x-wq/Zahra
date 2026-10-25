package com.example.zahra.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.GptAccentBlue
import com.example.zahra.voice.VoiceEngine

@Composable
fun VoiceModeDialog(
  voiceEngine: VoiceEngine,
  onDismiss: () -> Unit,
  onVoiceInputCompleted: (String) -> Unit
) {
  val context = LocalContext.current
  var isMuted by remember { mutableStateOf(false) }
  var isListening by remember { mutableStateOf(false) }
  var partialText by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var callbackActive by remember { mutableStateOf(true) }

  fun beginListening() {
    if (isMuted || !callbackActive) return
    errorMessage = null
    partialText = ""
    voiceEngine.startListening("ar-EG", object : VoiceEngine.STTListener {
      override fun onReadyForSpeech() {
        if (callbackActive) isListening = true
      }

      override fun onBeginningOfSpeech() {
        if (callbackActive) {
          isListening = true
          errorMessage = null
        }
      }

      override fun onEndOfSpeech() {
        if (callbackActive) isListening = false
      }

      override fun onResult(text: String, confidence: Float) {
        if (!callbackActive) return
        isListening = false
        val recognized = text.trim()
        if (recognized.isEmpty()) {
          errorMessage = "لم أتمكن من سماع كلام واضح. حاول مرة أخرى."
        } else {
          onVoiceInputCompleted(recognized)
          onDismiss()
        }
      }

      override fun onPartialResult(text: String) {
        if (callbackActive) partialText = text
      }

      override fun onError(errorCode: Int, message: String) {
        if (callbackActive) {
          isListening = false
          errorMessage = message
        }
      }
    })
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      isMuted = false
      beginListening()
    } else {
      isListening = false
      errorMessage = "إذن الميكروفون مطلوب لتحويل كلامك إلى نص. يمكنك تفعيله من إعدادات التطبيق."
    }
  }

  fun requestAndStartListening() {
    val permissionState = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    if (permissionState == PackageManager.PERMISSION_GRANTED) {
      beginListening()
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  DisposableEffect(voiceEngine) {
    callbackActive = true
    voiceEngine.initialize()
    onDispose {
      callbackActive = false
      voiceEngine.cancelListening()
      voiceEngine.stopSpeaking()
    }
  }

  LaunchedEffect(Unit) {
    requestAndStartListening()
  }

  val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")
  val orbScale by infiniteTransition.animateFloat(
    initialValue = 0.92f,
    targetValue = 1.12f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "OrbScale"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.65f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "PulseAlpha"
  )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F0F12))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(24.dp)
        .testTag("voice_mode_dialog")
    ) {
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(44.dp)
          .clip(CircleShape)
          .background(Color(0xFF26262B))
          .testTag("voice_mode_close_button")
      ) {
        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
      }

      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
          Box(
            modifier = Modifier
              .size(190.dp)
              .scale(if (isListening) orbScale else 1f)
              .clip(CircleShape)
              .background(GptAccentBlue.copy(alpha = if (isListening) pulseAlpha * 0.3f else 0.08f))
          )
          Box(
            modifier = Modifier
              .size(120.dp)
              .clip(CircleShape)
              .background(if (isMuted) Color(0xFF4B5563) else GptAccentBlue)
              .clickable {
                if (isListening) {
                  voiceEngine.stopListening()
                  isListening = false
                } else {
                  isMuted = false
                  requestAndStartListening()
                }
              }
              .testTag("voice_mode_toggle_listening"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.GraphicEq,
              contentDescription = if (isListening) "إيقاف الاستماع" else "بدء الاستماع",
              tint = Color.White,
              modifier = Modifier.size(54.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
          text = when {
            isMuted -> "الميكروفون متوقف"
            isListening -> "زهرة تستمع إليك..."
            errorMessage != null -> "الاستماع متوقف"
            else -> "اضغط للتحدث"
          },
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = when {
            errorMessage != null -> errorMessage.orEmpty()
            partialText.isNotBlank() -> partialText
            isMuted -> "اضغط زر الميكروفون لاستئناف الاستماع"
            else -> "تحدث بحرية، وسيظهر النص هنا قبل إرساله إلى زهرة"
          },
          color = if (errorMessage != null) Color(0xFFFCA5A5) else Color(0xFFA1A1AA),
          fontSize = 14.sp,
          lineHeight = 21.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = {
            if (isMuted) {
              isMuted = false
              requestAndStartListening()
            } else {
              isMuted = true
              isListening = false
              partialText = ""
              voiceEngine.cancelListening()
            }
          },
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xFF26262B))
            .testTag("voice_mode_mute_button")
        ) {
          Icon(
            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isMuted) "استئناف الميكروفون" else "إيقاف الميكروفون",
            tint = if (isMuted) Color(0xFFEF4444) else Color.White
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262B)),
          shape = CircleShape,
          modifier = Modifier.testTag("voice_mode_end_button")
        ) {
          Text("إنهاء المحادثة الصوتية", color = Color.White, fontSize = 14.sp)
        }
      }
    }
  }
}
