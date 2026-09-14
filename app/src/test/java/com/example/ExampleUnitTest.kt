package com.example

import com.example.ui.components.PdfTargetSizePreset
import com.example.util.PdfCompressor
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPdfCompressor_formatFileSize() {
    assertEquals("500 B", PdfCompressor.formatFileSize(500L))
    assertEquals("500 KB", PdfCompressor.formatFileSize(500 * 1024L))
    assertEquals("1.50 MB", PdfCompressor.formatFileSize((1.5 * 1024 * 1024).toLong()))
    assertEquals("2.00 MB", PdfCompressor.formatFileSize(2 * 1024 * 1024L))
  }

  @Test
  fun testPdfTargetSizePresets() {
    assertEquals(500, PdfTargetSizePreset.FIVE_HUNDRED_KB.kb)
    assertEquals(1024, PdfTargetSizePreset.ONE_MB.kb)
    assertEquals(2048, PdfTargetSizePreset.TWO_MB.kb)
    assertNull(PdfTargetSizePreset.ORIGINAL.kb)
  }
}
