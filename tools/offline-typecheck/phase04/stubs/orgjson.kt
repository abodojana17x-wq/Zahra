// Stubs لأغراض التحقق النوعي المحلي فقط (لا تُستورد في بناء Gradle).
package org.json

open class JSONException(message: String) : Exception(message)

open class JSONObject {
    private val map = linkedMapOf<String, Any?>()
    constructor()
    constructor(json: String) {
        if (!json.trim().startsWith("{")) throw JSONException("not an object")
    }

    fun put(key: String, value: Any?): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Int): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Long): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Boolean): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Double): JSONObject { map[key] = value; return this }
    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key] == null
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length(): Int = map.size
    fun opt(key: String): Any? = map[key]
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun getJSONObject(key: String): JSONObject = map[key] as? JSONObject ?: throw JSONException(key)
    fun optInt(key: String, def: Int): Int = map[key] as? Int ?: def
    fun getInt(key: String): Int = map[key] as? Int ?: throw JSONException(key)
    fun optLong(key: String, def: Long): Long = map[key] as? Long ?: def
    fun getLong(key: String): Long = map[key] as? Long ?: throw JSONException(key)
    fun optDouble(key: String, def: Double): Double = map[key] as? Double ?: def
    fun optBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
    // T يُستنتج من سياق الاستدعاء، فيعمل مع String وString? معًا (كما في org.json الحقيقي).
    @Suppress("UNCHECKED_CAST")
    fun <T> optString(key: String, def: T): T = (map[key]?.toString() ?: def) as T
    fun optString(key: String): String = map[key]?.toString() ?: ""
    fun getString(key: String): String = map[key]?.toString() ?: throw JSONException(key)
    override fun toString(): String = map.toString()
    companion object { val NULL: Any = Any() }
}

open class JSONArray {
    private val list = mutableListOf<Any?>()
    constructor()
    constructor(values: Collection<*>) { list.addAll(values) }
    fun length(): Int = list.size
    fun put(value: Any?): JSONArray { list.add(value); return this }
    fun opt(i: Int): Any? = list.getOrNull(i)
    fun getString(i: Int): String = list.getOrNull(i)?.toString() ?: throw JSONException("index $i")
    fun getJSONObject(i: Int): JSONObject = list.getOrNull(i) as? JSONObject ?: throw JSONException("index $i")
    fun add(value: Any?): JSONArray { list.add(value); return this }
    override fun toString(): String = list.toString()
}
