package com.example.zahra.advanced

import com.example.zahra.security.ZahraCrypto
import java.io.File
import java.security.MessageDigest

/**
 * تسليم أوزان النموذج — المرحلة 7 · المسار A.3 (D11).
 *
 * السياسة (D11): الخيار المفضّل = تنزيل عند الطلب من رابط ثابت + تحقق sha256 + تخزين مشفّر + زر حذف +
 * صفحة خصوصية؛ أي تنزيل = Opt-in صريح. لا يُضمّن وزن كبير داخل APK الأساسي (D11)، والنواة (0–6) لا تُمس.
 *
 * ⚠️ الخلفية الافتراضية هنا [OfflineModelDelivery] **معطّلة بأمان**: النواة أوفلاين افتراضيًا (D1)،
 * فلا يحدث أي تنزيل فعلي إلا بعد تفعيل «الوضع المتصل» صراحةً وربط واجهة تنزيل حقيقية (HTTP) بقرار مالك.
 * ما يُنفَّذ هنا: عقد التسليم + تحقق sha256 الحقيقي + تخزين مشفّر لبيانات النموذج (manifest).
 */

/** خطة تنزيل ثابتة لنموذج. */
data class DownloadPlan(
  val entry: SlmModelEntry,
  val url: String,
  val expectedSha256: String,
  val sizeMB: Int
)

/** عقد تسليم النموذج. */
interface ModelDelivery {
  /**
   * يبني خطة تنزيل لنموذج. يُرجع null في الوضع الأوفلاين الافتراضي (لا اتصال صامت).
   * أي تنفيذ حقيقي يجب أن يشترط Opt-in صريحًا + [com.example.zahra.tools.NetworkPolicy] في الوضع المتصل.
   */
  fun planDownload(entry: SlmModelEntry): DownloadPlan?

  /** حذف ملفات النموذج (زر الحذف في الإعدادات/الخصوصية). */
  fun delete(entry: SlmModelEntry): Boolean
}

/** تحقق sha256 حقيقي (نقي، بلا أندرويد) — يُستخدم بعد أي تنزيل وقبل التحميل. */
object Sha256Verifier {
  fun hash(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun verify(file: File, expectedHex: String): Boolean {
    if (!file.exists() || expectedHex.isBlank()) return false
    return try {
      hash(file.readBytes()).equals(expectedHex.lowercase(), ignoreCase = true)
    } catch (_: Throwable) {
      false
    }
  }
}

/**
 * مخزن بيانات النموذج المشفّر.
 *
 * يُشفّر manifest صغير (المعرّف + sha256 المتوقّع + الرابط) عبر [ZahraCrypto] (AES-256-GCM،
 * fail-closed). أوزان النموذج نفسها (مئات MB–GB) تُحفظ كملف عادي مُحقَّق بـ sha256 — التشفير الكامل
 * لأوزان بهذا الحجم مقيد بالأداء ويُوثَّق كخطوة لاحقة بقرار مالك؛ لا ندّعي تشفير الأوزان هنا.
 */
class EncryptedModelStore(private val crypto: ZahraCrypto = ZahraCrypto) {
  data class Manifest(val id: String, val expectedSha256: String, val sourceUrl: String)

  fun saveManifest(entry: SlmModelEntry, expectedSha256: String, dir: File): File {
    val manifest = Manifest(entry.id, expectedSha256, entry.sourceUrl)
    val plain = buildString {
      append("id=${manifest.id}\n")
      append("sha256=${manifest.expectedSha256}\n")
      append("url=${manifest.sourceUrl}\n")
    }
    val encrypted = crypto.encrypt(plain)
    val file = File(dir, "${entry.id}.manifest.enc")
    file.writeText(encrypted, Charsets.UTF_8)
    return file
  }

  fun loadManifest(file: File): Manifest? {
    if (!file.exists()) return null
    return try {
      val plain = crypto.decrypt(file.readText(Charsets.UTF_8))
      var id = ""; var sha = ""; var url = ""
      plain.lineSequence().forEach { line ->
        when {
          line.startsWith("id=") -> id = line.removePrefix("id=")
          line.startsWith("sha256=") -> sha = line.removePrefix("sha256=")
          line.startsWith("url=") -> url = line.removePrefix("url=")
        }
      }
      if (id.isBlank() || sha.isBlank()) null else Manifest(id, sha, url)
    } catch (_: Throwable) {
      null
    }
  }

  fun deleteManifest(file: File): Boolean = try { file.delete() } catch (_: Throwable) { false }
}

/**
 * خلفية التسليم الافتراضية: أوفلاين تمامًا (D1). لا تُجري أي اتصال شبكة صامت؛ أي تنزيل يتطلب
 * Opt-in صريحًا + ربط HTTP بقرار مالك. تُعيد [planDownload] دومًا null في هذه الخلفية.
 */
object OfflineModelDelivery : ModelDelivery {
  override fun planDownload(entry: SlmModelEntry): DownloadPlan? = null

  override fun delete(entry: SlmModelEntry): Boolean = true
}
