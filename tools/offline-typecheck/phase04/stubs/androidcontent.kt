// Stubs لأغراض التحقق النوعي المحلي فقط.
package android.content

open class Context {
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()
    open val packageName: String get() = ""
    companion object { const val MODE_PRIVATE = 0 }
}

class SharedPreferences {
    fun edit(): Editor = Editor()
    fun getString(key: String, def: String?): String? = null
    fun contains(key: String): Boolean = false
    class Editor {
        fun clear(): Editor = this
        fun remove(key: String): Editor = this
        fun putString(k: String, v: String): Editor = this
        fun commit(): Boolean = true
        fun apply() {}
    }
}
