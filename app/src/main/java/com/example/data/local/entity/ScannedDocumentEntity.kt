package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
