package com.example.zahra.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * بطاقة مصدر تُرفق بكل رد حقائقي (طقس/ويكيبيديا/ويكيداتا).
 *
 * القاعدة: لا نص بلا مصدر، ولا مصدر بلا رخصة، ولا معلومة حيّة بلا وقت جلب.
 * `cached=true` يعني أن القيمة من الكاش المحلي وليست جلبًا لحظيًا — تُعرض للمستخدم.
 */
data class SourceEnvelope(
  val text: String,
  val sourceName: String,
  val sourceUrl: String?,
  val license: String,
  val fetchedAtMs: Long,
  val cached: Boolean,
  val confidence: Double,
  val notes: List<String> = emptyList()
) {
  val fetchedAtDisplay: String
    get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(fetchedAtMs))

  /** نص الشريحة القابلة للضغط في `ChatScreen`. */
  fun chipText(): String = buildString {
    append("المصدر: ").append(sourceName)
    if (sourceUrl != null) append(" · ").append(sourceUrl)
    append(" · ").append(license)
    append(if (cached) " · من الكاش" else " · حي")
    append(" · ").append(fetchedAtDisplay)
  }
}

/** تعريف مصدر بيانات معلن: الاسم والرابط والرخصة والالتزام بالنسب. */
data class SourceCitation(
  val id: String,
  val name: String,
  val url: String,
  val license: String,
  val attributionNote: String,
  val requiresOptIn: Boolean = true
)

/**
 * سجلّ المصادر الخارجية وتراخيصها.
 *
 * كل القيم منقولة من صفحات المشروع الرسمية بتاريخ البحث الموثّق في
 * `evidence/phase-02/research.md`، ولا يُستخدم مصدر بلا رخصة معلنة.
 */
object ZahraSources {
  val OPEN_METEO = SourceCitation(
    id = "open-meteo",
    name = "Open-Meteo",
    url = "https://open-meteo.com/",
    license = "CC BY 4.0",
    attributionNote = "بيانات الطقس من Open-Meteo (CC BY 4.0) — يجب ذكر المصدر بجانب أي عرض للبيانات."
  )

  val WIKIPEDIA = SourceCitation(
    id = "wikipedia",
    name = "ويكيبيديا (Action API)",
    url = "https://ar.wikipedia.org/w/api.php",
    license = "CC BY-SA 4.0",
    attributionNote = "محتوى ويكيبيديا بترخيص CC BY-SA 4.0 — يُنسب للمقالة ورابطها، وتُذكر الرخصة في الشريحة."
  )

  val WIKIDATA = SourceCitation(
    id = "wikidata",
    name = "Wikidata (SPARQL)",
    url = "https://query.wikidata.org/sparql",
    license = "CC0 1.0",
    attributionNote = "بيانات ويكي‌داتا في الملك العام (CC0) — تُذكر مع ذلك كمرجع للتحقق."
  )

  val all: List<SourceCitation> = listOf(OPEN_METEO, WIKIPEDIA, WIKIDATA)

  fun byId(id: String): SourceCitation? = all.firstOrNull { it.id == id }
}
