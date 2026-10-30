package com.example.zahra.diagnostics

import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات المرحلة 6 لمعلومات الإصدار.
 *
 * تقرأ شكلاً اصطناعيًا مطابقًا للأصل المولّد، ولا تعتمد على Android/Robolectric.
 * التطابق الفعلي مع `app/src/main/assets/release_info.json` يفحصه
 * `tools/phase06/release_audit.py` (لأن بيئة الاختبار المعزولة قد لا تملك الأصل).
 */
class ReleaseInfoTest {

    private val valid = """
        {
          "schema": 1,
          "app": "زهرة",
          "versionName": "3.1",
          "versionCode": 2,
          "channel": "source-only",
          "channelAr": "مصدر فقط — لا يوجد APK منشور",
          "measurementStatus": "not-run",
          "measurementAr": "لم يُشغَّل قياس كامل بعد",
          "updatedAt": "2026-10-29",
          "limitations": [
            {
              "id": "LIM-002",
              "categoryAr": "التوزيع والإصدار",
              "severity": "critical",
              "severityAr": "حاسم",
              "titleAr": "لا يوجد APK",
              "detailAr": "لا يوجد ملف تثبيت منشور لهذا الإصدار."
            },
            {
              "id": "LIM-010",
              "categoryAr": "القدرات",
              "severity": "minor",
              "severityAr": "محدود",
              "titleAr": "الصوت",
              "detailAr": "يعتمد على محرك TTS في الجهاز."
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesGeneratedShapeAndKeepsHonestStatus() {
        val info = ReleaseInfoCatalog.parse(valid)
        assertEquals("زهرة", info.app)
        assertEquals("3.1", info.versionName)
        assertEquals(2, info.versionCode)
        assertEquals("source-only", info.channel)
        assertEquals("not-run", info.measurementStatus)
        assertEquals(2, info.limitations.size)
        assertEquals(1, info.criticalCount)
    }

    @Test
    fun groupsCategoriesAndSortsCriticalFirst() {
        val info = ReleaseInfoCatalog.parse(valid)
        val grouped = info.groupedByCategory()
        assertEquals(2, grouped.size)
        val distribution = grouped.first { it.first == "التوزيع والإصدار" }.second
        assertEquals("LIM-002", distribution.single().id)
        val capability = grouped.first { it.first == "القدرات" }.second
        assertEquals("LIM-010", capability.single().id)
    }

    @Test
    fun rejectsDuplicateLimitationIds() {
        val duplicated = valid.replace("\"LIM-010\"", "\"LIM-002\"")
        val error = runCatching { ReleaseInfoCatalog.parse(duplicated) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("مكررة"))
    }

    @Test
    fun rejectsUnknownSchemaAndEmptyLimitations() {
        val unknown = valid.replace("\"schema\": 1", "\"schema\": 99")
        assertTrue(runCatching { ReleaseInfoCatalog.parse(unknown) }.isFailure)

        val empty = valid.replace(
            Regex("\"limitations\"\\s*:\\s*\\[.*?\\]", RegexOption.DOT_MATCHES_ALL),
            "\"limitations\": []"
        )
        assertTrue(runCatching { ReleaseInfoCatalog.parse(empty) }.isFailure)
    }

    @Test
    fun rejectsMissingFieldsInsteadOfShowingEmptyList() {
        val missing = valid.replace("\"measurementAr\": \"لم يُشغَّل قياس كامل بعد\",", "")
        val error = runCatching { ReleaseInfoCatalog.parse(missing) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("measurementAr"))
    }

    @Test
    fun rejectsUnknownSeverity() {
        val unknown = valid.replace("\"severity\": \"minor\"", "\"severity\": \"apocalyptic\"")
        val error = runCatching { ReleaseInfoCatalog.parse(unknown) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("خطورة"))
    }

    @Test
    fun allLimitationsHaveReadableText() {
        val info = ReleaseInfoCatalog.parse(valid)
        assertTrue(info.limitations.all { it.id.isNotBlank() && it.titleAr.length >= 4 && it.detailAr.length >= 8 })
        assertTrue(info.limitations.all { it.severity in setOf("critical", "major", "minor", "info") })
    }
}
