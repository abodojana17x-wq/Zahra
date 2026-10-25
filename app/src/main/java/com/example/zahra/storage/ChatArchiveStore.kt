package com.example.zahra.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.zahra.model.ZahraSession
import com.example.zahra.model.ZahraToolPayload
import com.example.zahra.security.ZahraCrypto
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/** The durable portion of the chat UI state. */
data class ChatArchiveSnapshot(
  val schemaVersion: Int = ChatArchiveStore.CURRENT_SCHEMA_VERSION,
  val sessions: List<ZahraSession> = emptyList(),
  val activeSessionId: String = "",
  val pinnedSessionIds: Set<String> = emptySet()
)

/**
 * Encrypted local archive for conversations, including their interactive tool payloads.
 *
 * Writes happen off the main thread in [com.example.zahra.ui.ZahraViewModel]. A synchronous
 * commit plus read-back check prevents a response from being reported as saved when Android
 * failed to persist it. Damaged payloads are preserved separately for diagnosis/recovery and
 * are never exposed as plaintext conversation content.
 */
class ChatArchiveStore(
  context: Context,
  prefName: String = PREFS_NAME
) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

  private val payloadGson = Gson()
  private val gson: Gson = GsonBuilder()
    .registerTypeAdapter(ZahraToolPayload::class.java, ToolPayloadJsonAdapter(payloadGson))
    .create()

  @Synchronized
  fun load(): ChatArchiveSnapshot? {
    val encrypted = prefs.getString(KEY_ARCHIVE, null) ?: return null
    return try {
      val json = ZahraCrypto.decrypt(encrypted)
      val snapshot = gson.fromJson(json, ChatArchiveSnapshot::class.java)
        ?: throw JsonParseException("Empty chat archive")
      require(snapshot.schemaVersion <= CURRENT_SCHEMA_VERSION) {
        "Unsupported chat archive schema: ${snapshot.schemaVersion}"
      }
      val validSessions = snapshot.sessions.filter { session ->
        session.id.isNotBlank() && session.title.isNotBlank()
      }
      val validIds = validSessions.mapTo(mutableSetOf()) { it.id }
      snapshot.copy(
        sessions = validSessions,
        activeSessionId = snapshot.activeSessionId.takeIf { it in validIds }.orEmpty(),
        pinnedSessionIds = snapshot.pinnedSessionIds.intersect(validIds)
      )
    } catch (_: Exception) {
      // Keep the original ciphertext instead of silently destroying the only recoverable copy.
      prefs.edit().putString(KEY_CORRUPTED_ARCHIVE, encrypted).apply()
      null
    }
  }

  @Synchronized
  fun save(snapshot: ChatArchiveSnapshot): Boolean {
    return try {
      val encrypted = ZahraCrypto.encrypt(gson.toJson(snapshot))
      val committed = prefs.edit().putString(KEY_ARCHIVE, encrypted).commit()
      committed && prefs.getString(KEY_ARCHIVE, null) == encrypted
    } catch (_: Exception) {
      false
    }
  }

  @Synchronized
  fun clear(): Boolean = prefs.edit().remove(KEY_ARCHIVE).remove(KEY_CORRUPTED_ARCHIVE).commit()

  private class ToolPayloadJsonAdapter(
    private val delegate: Gson
  ) : JsonSerializer<ZahraToolPayload>, JsonDeserializer<ZahraToolPayload> {

    override fun serialize(
      src: ZahraToolPayload,
      typeOfSrc: Type,
      context: JsonSerializationContext
    ): JsonElement = JsonObject().apply {
      addProperty(KIND, src.payloadKind())
      add(DATA, delegate.toJsonTree(src))
    }

    override fun deserialize(
      json: JsonElement,
      typeOfT: Type,
      context: JsonDeserializationContext
    ): ZahraToolPayload {
      val wrapper = json.asJsonObject
      val kind = wrapper.get(KIND)?.asString
        ?: throw JsonParseException("Missing tool payload kind")
      val data = wrapper.get(DATA)
        ?: throw JsonParseException("Missing tool payload data")
      return delegate.fromJson(data, payloadClass(kind))
    }

    private fun ZahraToolPayload.payloadKind(): String = when (this) {
      is ZahraToolPayload.TaskPayload -> "task"
      is ZahraToolPayload.NotePayload -> "note"
      is ZahraToolPayload.TranslatePayload -> "translate"
      is ZahraToolPayload.MathPayload -> "math"
      is ZahraToolPayload.ReligionPayload -> "religion"
      is ZahraToolPayload.SummarizePayload -> "summarize"
      is ZahraToolPayload.MemoryFactPayload -> "memory_fact"
      is ZahraToolPayload.ImageGenerationPayload -> "image"
      is ZahraToolPayload.SchedulePayload -> "schedule"
      is ZahraToolPayload.KnowledgePayload -> "knowledge"
      is ZahraToolPayload.ClarificationToolPayload -> "clarification"
      is ZahraToolPayload.EvidenceTracePayload -> "evidence"
      is ZahraToolPayload.PlanExecutionPayload -> "plan"
      is com.example.zahra.model.DeepThinkingPayload -> "deep_thinking"
    }

    private fun payloadClass(kind: String): Class<out ZahraToolPayload> = when (kind) {
      "task" -> ZahraToolPayload.TaskPayload::class.java
      "note" -> ZahraToolPayload.NotePayload::class.java
      "translate" -> ZahraToolPayload.TranslatePayload::class.java
      "math" -> ZahraToolPayload.MathPayload::class.java
      "religion" -> ZahraToolPayload.ReligionPayload::class.java
      "summarize" -> ZahraToolPayload.SummarizePayload::class.java
      "memory_fact" -> ZahraToolPayload.MemoryFactPayload::class.java
      "image" -> ZahraToolPayload.ImageGenerationPayload::class.java
      "schedule" -> ZahraToolPayload.SchedulePayload::class.java
      "knowledge" -> ZahraToolPayload.KnowledgePayload::class.java
      "clarification" -> ZahraToolPayload.ClarificationToolPayload::class.java
      "evidence" -> ZahraToolPayload.EvidenceTracePayload::class.java
      "plan" -> ZahraToolPayload.PlanExecutionPayload::class.java
      "deep_thinking" -> com.example.zahra.model.DeepThinkingPayload::class.java
      else -> throw JsonParseException("Unknown tool payload kind: $kind")
    }

    // DeepThinkingPayload is supported for historical compatibility, although messages normally
    // store it in their dedicated deepThinking property.

    private companion object {
      const val KIND = "kind"
      const val DATA = "data"
    }
  }

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
    private const val PREFS_NAME = "zahra_chat_archive"
    private const val KEY_ARCHIVE = "archive_encrypted_v1"
    private const val KEY_CORRUPTED_ARCHIVE = "archive_recovery_copy_v1"
  }
}
