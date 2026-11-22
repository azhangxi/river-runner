package com.riverrunner.engine

import kotlin.js.Date
import kotlin.random.Random

internal fun seedNow(): Long = Random.Default.nextLong()
internal fun timestampNow(): Long = Date.now().toLong()
