package me.rerere.rikkahub.utils

import kotlin.math.PI

fun Double.toRadians(): Double = this * PI / 180.0

fun Double.toDegrees(): Double = this * 180.0 / PI

fun Float.toRadians(): Float = (this * PI / 180.0).toFloat()

fun Float.toDegrees(): Float = (this * 180.0 / PI).toFloat()
