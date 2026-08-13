package com.example.data.repository

import com.example.data.local.dao.ScannedDocumentDao
import com.example.data.local.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class DocumentRepository(private val dao: ScannedDocumentDao) {

    val allDocuments: Flow<List<ScannedDocumentEntity>> = dao.getAllDocuments()

    suspend fun insertDocument(doc: ScannedDocumentEntity) {
        dao.insertDocument(doc)
    }

    suspend fun updateDocument(doc: ScannedDocumentEntity) {
        dao.updateDocument(doc)
    }

    suspend fun deleteDocument(doc: ScannedDocumentEntity) {
        // Delete PDF file safely
        try {
            val pdfFile = File(doc.pdfPath)
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Delete thumbnail file safely
        try {
            val thumbFile = File(doc.thumbnailPath)
            if (thumbFile.exists()) {
                thumbFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dao.deleteDocumentById(doc.id)
    }
}
