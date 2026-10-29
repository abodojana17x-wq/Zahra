package android.content

open class Context {
  open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = TODO()
  open val assets: android.content.res.AssetManager get() = TODO()
  open val packageManager: android.content.pm.PackageManager get() = TODO()
  open val packageName: String get() = ""
  companion object { const val MODE_PRIVATE = 0 }
}

class SharedPreferences {
  fun edit(): Editor = Editor()
  fun getString(key: String, def: String?): String? = null
  class Editor {
    fun clear(): Editor = this
    fun putString(k: String, v: String): Editor = this
    fun commit(): Boolean = true
    fun apply() {}
  }
}
