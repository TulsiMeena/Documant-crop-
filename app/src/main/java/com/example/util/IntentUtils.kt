package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object IntentUtils {

    /**
     * Opens a PDF file using an installed external PDF viewer app on Android.
     */
    fun openPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer installed.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares a PDF file using the native Android system share sheet.
     */
    fun sharePdf(context: Context, pdfFile: File, title: String) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Document via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
