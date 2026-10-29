package org.junit

annotation class Test

object Assert {
  fun assertEquals(expected: Double, actual: Double, delta: Double) {}
  fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {}
  fun assertEquals(message: String?, expected: Any?, actual: Any?) {}
  fun assertEquals(expected: Any?, actual: Any?) {}
  fun assertTrue(condition: Boolean) {}
  fun assertTrue(message: String?, condition: Boolean) {}
  fun fail(message: String?) {}
}
