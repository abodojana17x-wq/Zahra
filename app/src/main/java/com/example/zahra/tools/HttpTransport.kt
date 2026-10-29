package com.example.zahra.tools

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** نتيجة طلب HTTP مبسّطة (بدون استثناءات تتسرب لطبقة الأدوات). */
sealed class HttpResult {
  data class Success(val body: String, val statusCode: Int) : HttpResult()
  data class Failure(val reason: String) : HttpResult()
}

/**
 * ناقل HTTP قابل للحقن — يسمح باختبار الأدوات كاملة **بلا شبكة**
 * (اختبار بمُجيب وهمي يعيد نفس شكل استجابة Open-Meteo/ويكيبيديا).
 */
interface HttpTransport {
  fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult
}

/**
 * تنفيذ حقيقي عبر `HttpURLConnection` (موجود في JDK وأندرويد، بلا اعتمادية جديدة).
 *
 * ⚠️ يجب استدعاؤه من خيط خلفي على أندرويد؛ `ZahraRouter` لا يستدعيه في مسار الواجهة.
 */
class HttpUrlConnectionTransport(
  private val connectTimeoutMs: Int = 8_000,
  private val readTimeoutMs: Int = 8_000,
  private val userAgent: String = "Zahra/2.0 (offline-first assistant; opt-in network)"
) : HttpTransport {

  override fun get(url: String, headers: Map<String, String>): HttpResult {
    var connection: HttpURLConnection? = null
    return try {
      connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        setRequestProperty("User-Agent", userAgent)
        for ((key, value) in headers) setRequestProperty(key, value)
      }
      val status = connection.responseCode
      if (status !in 200..299) {
        return HttpResult.Failure("استجابة غير ناجحة من الخادم (HTTP $status)")
      }
      val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
      HttpResult.Success(body, status)
    } catch (e: Exception) {
      HttpResult.Failure("تعذّر إكمال الطلب الشبكي: ${e.message ?: e::class.simpleName}")
    } finally {
      connection?.disconnect()
    }
  }
}

object UrlEncoding {
  /** ترميز معامل استعلام (مسافة ⇒ %20 لا +). */
  fun encode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
