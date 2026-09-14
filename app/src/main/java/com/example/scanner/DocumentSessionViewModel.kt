package com.example.scanner

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ScannedDocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.scanner.model.ScannedPage
import com.example.scanner.processor.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DocumentSessionViewModel : ViewModel() {

    private val _pages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val pages: StateFlow<List<ScannedPage>> = _pages.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _lastCreatedDocument = MutableStateFlow<ScannedDocumentEntity?>(null)
    val lastCreatedDocument: StateFlow<ScannedDocumentEntity?> = _lastCreatedDocument.asStateFlow()

    fun addPage(enhancedImagePath: String, correctedImagePath: String) {
        val currentList = _pages.value.toMutableList()
        val newPage = ScannedPage(
            id = UUID.randomUUID().toString(),
            correctedImagePath = correctedImagePath,
            enhancedImagePath = enhancedImagePath,
            pageOrder = currentList.size + 1
        )
        currentList.add(newPage)
        _pages.value = currentList
    }

    fun movePageUp(index: Int) {
        val list = _pages.value.toMutableList()
        if (index > 0 && index < list.size) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            // Update page order values
            list.forEachIndexed { i, p -> p.pageOrder = i + 1 }
            _pages.value = list
        }
    }

    fun movePageDown(index: Int) {
        val list = _pages.value.toMutableList()
        if (index >= 0 && index < list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            list.forEachIndexed { i, p -> p.pageOrder = i + 1 }
            _pages.value = list
        }
    }

    fun deletePage(pageId: String) {
        val list = _pages.value.filter { it.id != pageId }.toMutableList()
        list.forEachIndexed { i, p -> p.pageOrder = i + 1 }
        _pages.value = list
    }

    fun rotatePage(pageId: String) {
        val list = _pages.value.map { page ->
            if (page.id == pageId) {
                val newRot = (page.rotationDegrees + 90) % 360
                page.copy(rotationDegrees = newRot)
            } else {
                page
            }
        }
        _pages.value = list
    }

    fun createPdf(
        context: Context,
        title: String,
        pageSizeOption: String,
        targetSizeKb: Int? = null,
        onSuccess: (ScannedDocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            onError("No pages in session")
            return
        }

        _isGeneratingPdf.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfResult = PdfGenerator.generatePdf(
                    context = context,
                    rawDocumentTitle = title,
                    pages = currentPages,
                    pageSizeOption = pageSizeOption,
                    targetSizeKb = targetSizeKb
                )

                val docEntity = ScannedDocumentEntity(
                    id = UUID.randomUUID().toString(),
                    title = if (title.isBlank()) pdfResult.pdfFile.nameWithoutExtension else title,
                    pdfPath = pdfResult.pdfFile.absolutePath,
                    thumbnailPath = pdfResult.thumbnailFile.absolutePath,
                    pageCount = pdfResult.pageCount,
                    fileSizeBytes = pdfResult.fileSizeBytes,
                    createdAt = System.currentTimeMillis()
                )

                // Insert into local Room Database
                val db = AppDatabase.getDatabase(context)
                val repository = DocumentRepository(db.scannedDocumentDao())
                repository.insertDocument(docEntity)

                _lastCreatedDocument.value = docEntity

                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                    onSuccess(docEntity)
                }
            } catch (e: Exception) {
                Log.e("DocumentSessionViewModel", "Failed generating PDF", e)
                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                    onError(e.localizedMessage ?: "Couldn't create PDF.")
                }
            }
        }
    }

    fun clearSession() {
        _pages.value = emptyList()
        _lastCreatedDocument.value = null
        _isGeneratingPdf.value = false
    }
}
