package com.example.util

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.util.UUID

object PdfSecurityUtil {

    private const val TAG = "PdfSecurityUtil"

    fun initPdfBox(context: Context) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init PDFBox", e)
        }
    }

    /**
     * Encrypts and locks [sourcePdf] with [password].
     * The resulting PDF strictly requires [password] to open in any PDF viewer.
     */
    fun protectPdf(
        context: Context,
        sourcePdf: File,
        password: String,
        outputFile: File
    ): Boolean {
        if (!sourcePdf.exists() || password.isBlank()) return false

        return try {
            initPdfBox(context)
            val document = PDDocument.load(sourcePdf)
            val accessPermission = AccessPermission()
            
            // Standard PDF Security Specification:
            // Owner password must NOT be equal to user password.
            // When owner password is distinct and user password is the chosen password,
            // opening the document in ANY PDF reader strictly prompts for user password.
            val ownerPassword = "admin_${UUID.randomUUID()}"
            val protectionPolicy = StandardProtectionPolicy(ownerPassword, password, accessPermission)
            protectionPolicy.encryptionKeyLength = 128
            protectionPolicy.permissions = accessPermission

            document.protect(protectionPolicy)
            document.save(outputFile)
            document.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting PDF with password", e)
            false
        }
    }

    /**
     * Checks if a PDF is currently encrypted/password-protected.
     */
    fun isPdfEncrypted(context: Context, sourcePdf: File): Boolean {
        if (!sourcePdf.exists()) return false
        return try {
            initPdfBox(context)
            val doc = PDDocument.load(sourcePdf)
            val isEncrypted = doc.isEncrypted
            doc.close()
            isEncrypted
        } catch (e: Exception) {
            // Loading an encrypted PDF without password may throw InvalidPasswordException, meaning it is encrypted!
            true
        }
    }

    /**
     * Unlocks an encrypted PDF using [password] and writes unencrypted PDF to [outputFile].
     */
    fun unlockPdf(
        context: Context,
        sourcePdf: File,
        password: String,
        outputFile: File
    ): Boolean {
        if (!sourcePdf.exists()) return false
        return try {
            initPdfBox(context)
            val document = PDDocument.load(sourcePdf, password)
            document.isAllSecurityToBeRemoved = true
            document.save(outputFile)
            document.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking PDF", e)
            false
        }
    }

    /**
     * Verifies if [password] can successfully open the encrypted [sourcePdf].
     */
    fun verifyPassword(
        context: Context,
        sourcePdf: File,
        password: String
    ): Boolean {
        if (!sourcePdf.exists()) return false
        return try {
            initPdfBox(context)
            val document = PDDocument.load(sourcePdf, password)
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}

