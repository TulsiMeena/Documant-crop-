package com.example.scanner.model

import java.util.UUID

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val correctedImagePath: String,
    val enhancedImagePath: String,
    var pageOrder: Int,
    val rotationDegrees: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
