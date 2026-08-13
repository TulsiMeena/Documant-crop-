package com.example.scanner.model

enum class EnhanceMode(val label: String) {
    ORIGINAL("Original"),
    AUTO("Auto"),
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W")
}

data class AdjustParams(
    val brightness: Float = 0.0f, // Range -1.0 to +1.0
    val contrast: Float = 0.0f,   // Range -1.0 to +1.0
    val sharpness: Float = 0.0f   // Range 0.0 to 1.0
) {
    val isDefault: Boolean
        get() = brightness == 0.0f && contrast == 0.0f && sharpness == 0.0f
}
