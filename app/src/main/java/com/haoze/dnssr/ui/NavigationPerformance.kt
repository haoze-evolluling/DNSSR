package com.haoze.dnssr.ui

import kotlinx.coroutines.delay

internal const val NAVIGATION_ANIMATION_DURATION_MS = 250

internal suspend fun awaitNavigationAnimation() {
    delay(NAVIGATION_ANIMATION_DURATION_MS.toLong())
}
