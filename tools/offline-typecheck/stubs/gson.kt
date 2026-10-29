package com.google.gson

open class JsonElement {
  // محاكاة الخصائص التركيبية لـ Java getters في Gson الحقيقي
  open val asString: String get() = ""
  open val asInt: Int get() = 0
  open val asDouble: Double get() = 0.0
  val isJsonPrimitive: Boolean get() = false
  val isJsonArray: Boolean get() = false
  val isJsonObject: Boolean get() = false
  val asJsonPrimitive: JsonPrimitive get() = this as JsonPrimitive
  val asJsonArray: JsonArray get() = this as JsonArray
  val asJsonObject: JsonObject get() = this as JsonObject
}

open class JsonPrimitive : JsonElement() {
  val isNumber: Boolean get() = false
  val isString: Boolean get() = false
}

class JsonArray : JsonElement(), Iterable<JsonElement> {
  fun add(e: JsonElement): JsonArray = this
  fun add(e: String): JsonArray = this
  fun size(): Int = 0
  fun get(i: Int): JsonElement = JsonElement()
  override fun iterator(): Iterator<JsonElement> = emptyList<JsonElement>().iterator()
}

open class JsonObject : JsonElement() {
  fun add(k: String, v: JsonElement): JsonObject = this
  fun addProperty(k: String, v: String?): JsonObject = this
  fun addProperty(k: String, v: Number?): JsonObject = this
  fun addProperty(k: String, v: Boolean?): JsonObject = this
  fun get(k: String): JsonElement? = null
  fun optInt(k: String, d: Int): Int = d
  fun optDouble(k: String, d: Double): Double = d
  fun optString(k: String, d: String): String = d
  fun opt(k: String): Any? = null
  fun keys(): Iterator<String> = emptyList<String>().iterator()
  fun length(): Int = 0
  fun optJSONObject(k: String): JsonObject? = null
  fun optJSONArray(k: String): JsonArray? = null
}

object JsonParser {
  fun parseString(s: String): JsonElement = JsonElement()
}

class Gson {
  fun <T> fromJson(json: String, type: Class<T>): T? = null
  fun toJson(src: Any): String = ""
}

class GsonBuilder {
  fun setPrettyPrinting(): GsonBuilder = this
  fun serializeNulls(): GsonBuilder = this
  fun create(): Gson = Gson()
}
