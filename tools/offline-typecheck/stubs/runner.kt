package org.junit.runner

import kotlin.reflect.KClass

annotation class RunWith(val value: KClass<*>)
