package com.example.zahra.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zahra.security.ZahraCrypto
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * المرحلة 4 — معالجة الذاكرة المعطوبة في الواجهة.
 *
 * البيانات التالفة لم تعد تُبتلع في صمت: تُسجَّل كـ`LoadIssue` وتظهر كتحذير،
 * بينما يبقى فشل فك التشفير fail-closed (اختبار منفصل في DialogueIntegrationTest).
 * هذه الاختبارات Robolectric؛ لم تُشغَّل في بيئة التنفيذ الحالية (لا Android SDK).
 */
@RunWith(RobolectricTestRunner::class)
class MemoryCorruptionReportingTest {
    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("zahra_unified_memory", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        ZahraCrypto.setTestKeyForTestingOnly(ZahraCrypto.generateTestKey())
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        ZahraCrypto.setTestKeyForTestingOnly(null)
    }

    @Test fun corruptedTopicsAreReportedAndLeaveNoHalfLoadedState() {
        prefs.edit().putString("topics_v3_encrypted", ZahraCrypto.encrypt("هذا ليس JSON")).commit()
        val memory = UnifiedMemoryManager(context)
        memory.loadAll()
        assertTrue(memory.topicMemory.getAllTopics().isEmpty())
        assertTrue(memory.lastLoadIssues.any { it.area == "المواضيع" })
    }

    @Test fun corruptedProfileIsReportedAndDoesNotEraseStoredBytes() {
        prefs.edit().putString("user_profile_v3_encrypted", ZahraCrypto.encrypt("{ليس json}")).commit()
        val ciphertext = prefs.getString("user_profile_v3_encrypted", null)
        val memory = UnifiedMemoryManager(context)
        memory.loadAll()
        assertNull(memory.getUserName())
        assertTrue(memory.lastLoadIssues.any { it.area == "الملف الشخصي" })
        // لم نمحُ البيانات التالفة تلقائيًا: القرار للمستخدم عبر زر المسح.
        assertEquals(ciphertext, prefs.getString("user_profile_v3_encrypted", null))
    }

    @Test fun successfulLoadReportsNoIssueAndClearResetsTheReport() {
        val memory = UnifiedMemoryManager(context)
        memory.recordTurn("أنا اسمي أحمد", "أهلاً", "remember_fact")
        memory.saveAll()
        memory.loadAll()
        assertTrue(memory.lastLoadIssues.isEmpty())
        assertEquals("أحمد", memory.getUserName())
        assertTrue(memory.clearAll())
        assertTrue(memory.lastLoadIssues.isEmpty())
        assertNull(memory.getUserName())
    }

    @Test fun topicLoadFailureResetsPreviousTopicsInsteadOfMixingThem() {
        val memory = UnifiedMemoryManager(context)
        memory.topicMemory.detectTopics("أحب البرمجة")
        assertFalse(memory.topicMemory.getAllTopics().isEmpty())
        assertFalse(memory.topicMemory.loadFromJson("{ليس json}"))
        assertTrue(memory.topicMemory.getAllTopics().isEmpty())
    }
}
