// Stubs لأغراض التحقق النوعي المحلي فقط.
package android.util

object Base64 {
    const val NO_WRAP = 2
    fun encodeToString(bytes: ByteArray, flags: Int): String = ""
    fun decode(str: String, flags: Int): ByteArray = ByteArray(0)
}
