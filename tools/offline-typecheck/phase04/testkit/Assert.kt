package org.junit

/**
 * بديل مصغّر عن JUnit لتشغيل اختبارات Kotlin الخالصة في بيئة بلا Gradle/Android.
 *
 * لماذا دوال عليا لا `object Assert`؟ لأن الاختبارات القائمة تستخدم
 * `import org.junit.Assert.*` (استيراد ثابت بأسلوب Java). هذا الملف يُستخدم
 * عبر `run-tests.sh` الذي يستبدل في نسخة مؤقتة السطر `import org.junit.Assert.*`
 * بـ`import org.junit.*` — دون تعديل ملفات الاختبار في المستودع.
 *
 * **ليس** JUnit: لا Rules ولا Lifecycle ولا Parameterized. assertions هنا تنفّذ فعلًا.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test

fun fail(message: String?): Nothing = throw AssertionError(message ?: "فشل غير محدد")

fun assertTrue(condition: Boolean) {
    if (!condition) fail("المتوقع true")
}

fun assertTrue(message: String?, condition: Boolean) {
    if (!condition) fail(message)
}

fun assertFalse(condition: Boolean) {
    if (condition) fail("المتوقع false")
}

fun assertFalse(message: String?, condition: Boolean) {
    if (condition) fail(message)
}

fun assertNull(actual: Any?) {
    if (actual != null) fail("المتوقع null لكن كانت القيمة: $actual")
}

fun assertNull(message: String?, actual: Any?) {
    if (actual != null) fail("$message — كانت القيمة: $actual")
}

fun assertNotNull(actual: Any?) {
    if (actual == null) fail("المتوقع قيمة غير null")
}

fun assertNotNull(message: String?, actual: Any?) {
    if (actual == null) fail(message)
}

fun assertEquals(expected: Any?, actual: Any?) {
    if (expected != actual) fail("المتوقع <$expected> لكن كانت <$actual>")
}

fun assertEquals(message: String?, expected: Any?, actual: Any?) {
    if (expected != actual) fail("$message — المتوقع <$expected> لكن كانت <$actual>")
}

fun assertEquals(expected: Double, actual: Double, delta: Double) {
    if (kotlin.math.abs(expected - actual) > delta) fail("المتوقع $expected ± $delta لكن كانت $actual")
}

fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {
    if (kotlin.math.abs(expected - actual) > delta) fail("$message — المتوقع $expected ± $delta لكن كانت $actual")
}

fun assertNotEquals(unexpected: Any?, actual: Any?) {
    if (unexpected == actual) fail("يجب ألا يساوي <$unexpected>")
}

/** لا يوجد After/Before حقيقي؛ الاختبارات التي تحتاجه تُدار بخطوة صريحة داخل الدالة. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class After

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Before
