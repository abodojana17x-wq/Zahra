package com.example.zahra.dialogue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.engines.ZahraRouter
import com.example.zahra.memory.UnifiedMemoryManager
import com.example.zahra.model.OutcomeState
import com.example.zahra.security.ZahraCrypto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogueIntegrationTest {
    private lateinit var context: Context
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE).edit().clear().commit()
        ZahraCrypto.setTestKeyForTestingOnly(ZahraCrypto.generateTestKey())
    }
    @After fun tearDown() { ZahraCrypto.setTestKeyForTestingOnly(null) }

    @Test fun legacyProfileMigratesEncryptedAndClearDoesNotResurrectTopics() {
        val prefs = context.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE)
        prefs.edit().putString("user_profile_v3", "{\"name\":\"علي\"}").commit()
        val memory = UnifiedMemoryManager(context)
        memory.loadAll()
        assertEquals("علي", memory.getUserName())
        assertFalse(prefs.contains("user_profile_v3"))
        assertFalse(prefs.getString("user_profile_v3_encrypted", "")!!.contains("علي"))
        memory.topicMemory.detectTopics("أحب البرمجة")
        memory.saveAll()
        assertTrue(memory.clearAll())
        assertTrue(memory.topicMemory.getAllTopics().isEmpty())
        val reopened = UnifiedMemoryManager(context)
        reopened.loadAll()
        assertNull(reopened.getUserName())
        assertTrue(reopened.topicMemory.getAllTopics().isEmpty())
    }

    @Test fun actualRouterRecordsEarlyToolReturnsAndRefusesFutureWeather() {
        val router = ZahraRouter(context)
        router.process("الطقس في القاهرة")
        assertEquals(1, router.dialogueState.turns.size)
        val future = router.process("وبكرة؟")
        assertEquals(OutcomeState.UNSUPPORTED, future.outcomeState)
        assertTrue(future.text.contains("الغد"))
        assertNotNull(future.dialogueTraceId)
        assertEquals(listOf("ABSTAIN"), future.dialogueBlockSources)
        router.clearConversation()
        assertTrue(router.dialogueState.turns.isEmpty())
        router.release()
    }
    @Test fun earlyReturnsSynchronizeLegacyViewsAndSessionsDoNotShareCity() {
        val router = ZahraRouter(context)
        router.processForSession("A", "الطقس في القاهرة")
        assertEquals("الطقس في القاهرة", router.contextTracker.getLastUserQuery())
        assertEquals(1, router.unifiedMemory.getConversationLength())
        router.processForSession("B", "مرحبا")
        assertNull(router.dialogueState.slots[Slot.CITY])
        assertEquals("مرحبا", router.contextTracker.getLastUserQuery())
        assertEquals(1, router.unifiedMemory.getConversationLength())
        router.release()
    }

    @Test fun negatedFeelingsDoNotCreateNameAndUndoDoesNotClaimSuccess() {
        val router = ZahraRouter(context)
        router.process("مش حاسس اني وحيد")
        assertFalse(router.memoryEngine.getFacts().any { it.key == "الاسم" })
        val undo = router.process("تراجع")
        assertEquals(OutcomeState.UNSUPPORTED, undo.outcomeState)
        assertTrue(undo.text.contains("لم أغيّر"))
        router.release()
    }

    @Test fun temporaryFactsAreClearedAtSessionBoundary() {
        val router = ZahraRouter(context)
        router.memoryEngine.store.addTemporaryFact(com.example.zahra.model.FactCategory.INTERESTS, "اهتمام", "قراءة")
        router.clearConversation()
        assertTrue(router.memoryEngine.getTemporaryFacts().isEmpty())
        router.release()
    }

    @Test fun interruptedMigrationRemovesLegacyOnlyAfterAuthenticatedRead() {
        val prefs = context.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE)
        val json = "{\"name\":\"علي\"}"
        prefs.edit().putString("user_profile_v3", json)
            .putString("user_profile_v3_encrypted", ZahraCrypto.encrypt(json)).commit()
        val memory = UnifiedMemoryManager(context)
        memory.loadAll()
        assertEquals("علي", memory.getUserName())
        assertFalse(prefs.contains("user_profile_v3"))
    }

    @Test fun corruptCiphertextDoesNotFallBackToOrErasePlaintext() {
        val prefs = context.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE)
        prefs.edit().putString("user_profile_v3", "{\"name\":\"علي\"}")
            .putString("user_profile_v3_encrypted", "invalid").commit()
        try {
            UnifiedMemoryManager(context).loadAll()
            fail("Corrupt ciphertext must fail closed")
        } catch (_: SecurityException) {
            assertTrue(prefs.contains("user_profile_v3"))
            assertEquals("invalid", prefs.getString("user_profile_v3_encrypted", null))
        }
    }

    @Test fun emotionalUtteranceIsNotStoredAsAName() {
        val memory = UnifiedMemoryManager(context)
        memory.recordTurn("أنا فرحان النهاردة", "جميل", "emotional_support")
        assertNull(memory.getUserName())
        memory.recordTurn("أنا اسمي أحمد", "أهلاً", "remember_fact")
        assertEquals("أحمد", memory.getUserName())
    }

}
