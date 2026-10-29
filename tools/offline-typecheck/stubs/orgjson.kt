package org.json

open class JSONException(message: String) : Exception(message)

open class JSONObject {
  constructor()
  constructor(json: String)
  fun keys(): Iterator<String> = emptyList<String>().iterator()
  fun length(): Int = 0
  fun opt(key: String): Any? = null
  fun optJSONObject(key: String): JSONObject? = null
  fun optJSONArray(key: String): JSONArray? = null
  fun optInt(key: String, def: Int): Int = def
  fun optDouble(key: String, def: Double): Double = def
  fun optString(key: String, def: String): String = def
  companion object { val NULL: Any = Any() }
}

open class JSONArray {
  fun length(): Int = 0
  fun opt(i: Int): Any? = null
  fun getString(i: Int): String = ""
}
