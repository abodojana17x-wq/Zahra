package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.NoteEngine
import com.example.zahra.model.Sender
import com.example.zahra.model.ZahraMessage
import com.example.zahra.model.ZahraSession
import com.example.zahra.model.ZahraTask
import com.example.zahra.model.ZahraToolPayload
import com.example.zahra.security.ZahraCrypto
import com.example.zahra.storage.ChatArchiveSnapshot
import com.example.zahra.storage.ChatArchiveStore
import com.example.zahra.storage.ZahraPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZahraIntegrationRepairTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    ZahraCrypto.setTestKeyForTestingOnly(ZahraCrypto.generateTestKey())
    context.getSharedPreferences("zahra_chat_archive", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("zahra_ui_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("zahra_notes_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After
  fun tearDown() {
    ZahraCrypto.setTestKeyForTestingOnly(null)
  }

  @Test
  fun chatArchive_restoresEncryptedSessionsAndInteractivePayloads() {
    val task = ZahraTask(id = "task-1", title = "مراجعة المشروع", category = "عمل")
    val session = ZahraSession(
      id = "session-1",
      title = "خطة العمل",
      messages = listOf(
        ZahraMessage(id = "m-1", sender = Sender.USER, text = "أضف مهمة مراجعة المشروع"),
        ZahraMessage(
          id = "m-2",
          sender = Sender.ZAHRA,
          text = "تمت إضافة المهمة",
          toolPayload = ZahraToolPayload.TaskPayload(task)
        )
      )
    )
    val snapshot = ChatArchiveSnapshot(
      sessions = listOf(session),
      activeSessionId = session.id,
      pinnedSessionIds = setOf(session.id)
    )

    assertTrue(ChatArchiveStore(context).save(snapshot))
    val encrypted = context.getSharedPreferences("zahra_chat_archive", Context.MODE_PRIVATE)
      .getString("archive_encrypted_v1", null)
    assertNotNull(encrypted)
    assertFalse(encrypted!!.contains("مراجعة المشروع"))

    val restored = ChatArchiveStore(context).load()
    assertNotNull(restored)
    assertEquals(session.id, restored!!.activeSessionId)
    assertEquals(setOf(session.id), restored.pinnedSessionIds)
    assertEquals(2, restored.sessions.single().messages.size)
    val payload = restored.sessions.single().messages.last().toolPayload
    assertTrue(payload is ZahraToolPayload.TaskPayload)
    assertEquals(task, (payload as ZahraToolPayload.TaskPayload).task)
  }

  @Test
  fun chatArchive_corruptionFailsClosedAndKeepsRecoveryCiphertext() {
    val prefs = context.getSharedPreferences("zahra_chat_archive", Context.MODE_PRIVATE)
    prefs.edit().putString("archive_encrypted_v1", "damaged:ciphertext").commit()

    assertNull(ChatArchiveStore(context).load())
    assertEquals("damaged:ciphertext", prefs.getString("archive_recovery_copy_v1", null))
  }

  @Test
  fun preferences_surviveRecreationAndRejectUnknownAccent() {
    val preferences = ZahraPreferences(context)
    preferences.accentColorName = "بنفسجي"
    preferences.isGlassmorphismEnabled = true
    preferences.isDeepThinkingEnabled = true

    val restored = ZahraPreferences(context)
    assertEquals("بنفسجي", restored.accentColorName)
    assertTrue(restored.isGlassmorphismEnabled)
    assertTrue(restored.isDeepThinkingEnabled)

    restored.accentColorName = "لون غير معروف"
    assertEquals("بنفسجي", restored.accentColorName)
  }

  @Test
  fun notes_keepSeparateTitleAndContentAndMigrateToEncryptedStorage() {
    val engine = NoteEngine(context)
    val created = engine.createNote("تفاصيل خطة السفر غداً", "خطة السفر")

    assertEquals("خطة السفر", created.title)
    assertEquals("تفاصيل خطة السفر غداً", created.content)

    val prefs = context.getSharedPreferences("zahra_notes_prefs", Context.MODE_PRIVATE)
    assertNull(prefs.getString("notes_data", null))
    val encrypted = prefs.getString("notes_data_encrypted_v1", null)
    assertNotNull(encrypted)
    assertFalse(encrypted!!.contains("تفاصيل خطة السفر"))

    val restored = NoteEngine(context).getNotes().single()
    assertEquals(created, restored)
  }
}
