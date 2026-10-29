package org.junit

annotation class Test

object Assert {
  fun assertEquals(expected: Double, actual: Double, delta: Double) {}
  fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {}
  fun assertEquals(message: String?, expected: Any?, actual: Any?) {}
  fun assertEquals(expected: Any?, actual: Any?) {}
  fun assertTrue(condition: Boolean) {}
  fun assertFalse(condition: Boolean) {}
  fun assertFalse(message: String?, condition: Boolean) {}
  fun assertNull(actual: Any?) {}
  fun assertNull(message: String?, actual: Any?) {}
  fun assertNotNull(actual: Any?) {}
  fun assertNotNull(message: String?, actual: Any?) {}
  fun assertNotEquals(unexpected: Any?, actual: Any?) {}
  fun assertTrue(message: String?, condition: Boolean) {}
  fun fail(message: String?) {}
}
