package com.example.data.local

import androidx.room.*
import com.example.data.model.LabItemEntity
import com.example.data.model.LabItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {

    @Query("SELECT * FROM lab_items WHERE (userEmail = :userEmail OR userEmail = '') ORDER BY createdAt DESC")
    fun getAllLabItemsFlow(userEmail: String): Flow<List<LabItemEntity>>

    @Query("SELECT * FROM lab_items WHERE (userEmail = :userEmail OR userEmail = '') AND itemType = :type ORDER BY createdAt DESC")
    fun getLabItemsByTypeFlow(userEmail: String, type: LabItemType): Flow<List<LabItemEntity>>

    @Query("SELECT * FROM lab_items WHERE (userEmail = :userEmail OR userEmail = '') AND itemType = :type ORDER BY termLanguage ASC")
    fun getLabItemsByTypeAlphabeticalFlow(userEmail: String, type: LabItemType): Flow<List<LabItemEntity>>

    @Query("SELECT * FROM lab_items WHERE id = :id LIMIT 1")
    suspend fun getLabItemById(id: String): LabItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabItem(item: LabItemEntity)

    @Update
    suspend fun updateLabItem(item: LabItemEntity)

    @Delete
    suspend fun deleteLabItem(item: LabItemEntity)

    @Query("DELETE FROM lab_items WHERE id = :id")
    suspend fun deleteLabItemById(id: String)
}
