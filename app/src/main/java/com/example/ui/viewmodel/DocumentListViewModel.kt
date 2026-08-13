package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.scanner.processor.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DocumentListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    val searchQuery = MutableStateFlow("")

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DocumentRepository(db.scannedDocumentDao())
    }

    val filteredDocuments: StateFlow<List<ScannedDocumentEntity>> = combine(
        repository.allDocuments,
        searchQuery
    ) { docs, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            docs
        } else {
            docs.filter { doc ->
                doc.title.contains(trimmed, ignoreCase = true) ||
                doc.ocrText.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveOcrText(doc: ScannedDocumentEntity, ocrText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedDoc = doc.copy(ocrText = ocrText)
            repository.updateDocument(updatedDoc)
        }
    }

    fun deleteDocument(doc: ScannedDocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(doc)
        }
    }

    fun renameDocument(doc: ScannedDocumentEntity, newTitle: String) {
        val sanitized = PdfGenerator.sanitizeFilename(newTitle)
        if (sanitized.isBlank() || sanitized == doc.title) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldFile = File(doc.pdfPath)
                if (oldFile.exists()) {
                    val newFile = File(oldFile.parentFile, "$sanitized.pdf")
                    if (oldFile.renameTo(newFile)) {
                        val updatedDoc = doc.copy(
                            title = sanitized,
                            pdfPath = newFile.absolutePath
                        )
                        repository.updateDocument(updatedDoc)
                        return@launch
                    }
                }
                // Fallback update title only
                val updatedDoc = doc.copy(title = sanitized)
                repository.updateDocument(updatedDoc)
            } catch (e: Exception) {
                Log.e("DocumentListViewModel", "Error renaming document", e)
            }
        }
    }
}
