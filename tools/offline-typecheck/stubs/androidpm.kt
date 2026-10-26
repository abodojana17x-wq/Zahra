package android.content.pm

class PackageInfo {
  var requestedPermissions: Array<String>? = null
  var versionName: String? = null
  var versionCode: Int = 0
  var longVersionCode: Long = 0
}

open class PackageManager {
  fun getPackageInfo(name: String, flags: Int): PackageInfo = PackageInfo()
  companion object { const val GET_PERMISSIONS = 4096 }
}
