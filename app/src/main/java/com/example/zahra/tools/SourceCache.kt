package com.example.zahra.tools

/** نوع البيانات المحفوظة، وبالتالي مدة صلاحيتها (TTL). */
enum class CacheKind(val ttlMs: Long) {
  /** الطقس يتغيّر بسرعة ⇒ 30 دقيقة. */
  WEATHER(30L * 60L * 1000L),

  /** معلومات موسوعية ⇒ 30 يومًا. */
  FACT(30L * 24L * 60L * 60L * 1000L),

  /** افتراضي متحفظ ⇒ 6 ساعات. */
  GENERIC(6L * 60L * 60L * 1000L)
}

/**
 * كاش مصادر محلي بسيط قابل للاختبار (ساعة قابلة للحقن).
 *
 * - TTL لكل نوع، وحد أقصى للعناصر (إزالة الأقدم عند التجاوز).
 * - لا يُخزَّن أي شيء إلا استجابة جلب حقيقية؛ ولا يُعاد عنصر منتهٍ أبدًا.
 * - `serialize`/`restore` بصيغة نصية بسيطة (بدون اعتماد على Room أو JSON خارجي)،
 *   ويمكن لاحقًا تخزينها مشفّرة عبر `ZahraCrypto` — الربط بالتفضيلات مسؤولية طبقة التخزين.
 */
class SourceCache(
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val maxEntries: Int = 120
) {
  data class Entry(
    val key: String,
    val payload: String,
    val storedAtMs: Long,
    val ttlMs: Long,
    val kind: CacheKind
  ) {
    fun isExpired(nowMs: Long): Boolean = nowMs - storedAtMs >= ttlMs
  }

  private val entries = LinkedHashMap<String, Entry>()

  fun put(key: String, payload: String, kind: CacheKind, nowMs: Long = clock()): Entry {
    val entry = Entry(key, payload, nowMs, kind.ttlMs, kind)
    entries.remove(key)
    entries[key] = entry
    while (entries.size > maxEntries) {
      val oldestKey = entries.keys.firstOrNull() ?: break
      entries.remove(oldestKey)
    }
    return entry
  }

  fun get(key: String, nowMs: Long = clock()): Entry? {
    val entry = entries[key] ?: return null
    if (entry.isExpired(nowMs)) {
      entries.remove(key)
      return null
    }
    // إعادة الترتيب لتعكس آخر استخدام (LRU)
    entries.remove(key)
    entries[key] = entry
    return entry
  }

  fun clear() = entries.clear()

  fun size(): Int = entries.size

  fun purgeExpired(nowMs: Long = clock()): Int {
    val expired = entries.values.filter { it.isExpired(nowMs) }.map { it.key }
    expired.forEach { entries.remove(it) }
    return expired.size
  }

  /** ترميز نصي: `key|storedAt|ttl|kind|payload` لكل سطر مع هروب للفواصل. */
  fun serialize(): String = entries.values.joinToString("\n") { entry ->
    listOf(
      escape(entry.key),
      entry.storedAtMs.toString(),
      entry.ttlMs.toString(),
      entry.kind.name,
      escape(entry.payload)
    ).joinToString("|")
  }

  fun restore(serialized: String) {
    entries.clear()
    if (serialized.isBlank()) return
    for (line in serialized.split("\n")) {
      val parts = line.split("|")
      if (parts.size != 5) continue
      val storedAt = parts[1].toLongOrNull() ?: continue
      val ttl = parts[2].toLongOrNull() ?: continue
      val kind = runCatching { CacheKind.valueOf(parts[3]) }.getOrNull() ?: continue
      val entry = Entry(unescape(parts[0]), unescape(parts[4]), storedAt, ttl, kind)
      entries[entry.key] = entry
    }
  }

  private fun escape(value: String): String =
    value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

  private fun unescape(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
      val c = value[i]
      if (c == '\\' && i + 1 < value.length) {
        when (value[i + 1]) {
          '\\' -> { append('\\'); i += 2 }
          'p' -> { append('|'); i += 2 }
          'n' -> { append('\n'); i += 2 }
          else -> { append(c); i++ }
        }
      } else {
        append(c)
        i++
      }
    }
  }
}
