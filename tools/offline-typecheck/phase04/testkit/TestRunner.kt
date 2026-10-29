import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * مشغّل اختبارات صغير: ينفّذ كل دالة معلّمة بـ`org.junit.Test` (ويشغّل `@Before`/`@After`).
 * لا يدّعي تكافؤًا مع JUnit؛ يطبع ملخصًا ويخرج برمز فشل عند أول اختبار فاشل.
 */
object TestRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        var passed = 0
        val failures = mutableListOf<String>()
        args.forEach { className ->
            val type = Class.forName(className)
            val instance = type.getDeclaredConstructor().newInstance()
            val methods = type.declaredMethods.sortedBy { it.name }
            val before = methods.filter { it.isAnnotationPresent(org.junit.Before::class.java) }
            val after = methods.filter { it.isAnnotationPresent(org.junit.After::class.java) }
            val tests = methods.filter {
                it.isAnnotationPresent(org.junit.Test::class.java) && !Modifier.isStatic(it.modifiers)
            }
            check(tests.isNotEmpty()) { "لا توجد دوال اختبار في $className" }
            tests.forEach { method ->
                val label = "${type.simpleName}.${method.name}"
                try {
                    method.isAccessible = true
                    before.forEach { it.isAccessible = true; it.invoke(instance) }
                    method.invoke(instance)
                    after.forEach { it.isAccessible = true; it.invoke(instance) }
                    passed++
                    println("PASS $label")
                } catch (failure: InvocationTargetException) {
                    val cause = failure.targetException
                    failures += "$label: ${cause.javaClass.simpleName}: ${cause.message}"
                    println("FAIL $label")
                    cause.printStackTrace()
                }
            }
        }
        println("—")
        println("نجح $passed، فشل ${failures.size}")
        failures.forEach { println("✗ $it") }
        if (failures.isNotEmpty()) kotlin.system.exitProcess(1)
    }
}
