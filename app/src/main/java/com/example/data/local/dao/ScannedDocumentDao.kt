package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao {

    @Query("SELECT * FROM scanned_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): ScannedDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: ScannedDocumentEntity)

    @Update
    suspend fun updateDocument(doc: ScannedDocumentEntity)

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)
}
