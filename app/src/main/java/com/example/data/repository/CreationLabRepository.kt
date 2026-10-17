package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.LabDao
import com.example.data.model.LabItemEntity
import com.example.data.model.LabItemType
import com.example.ui.creationlab.VyianoraMasterData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CreationLabRepository(private val labDao: LabDao) {

    fun getAllLabItemsFlow(userEmail: String): Flow<List<LabItemEntity>> =
        labDao.getAllLabItemsFlow(userEmail)

    fun getLabItemsByTypeFlow(userEmail: String, type: LabItemType): Flow<List<LabItemEntity>> =
        labDao.getLabItemsByTypeFlow(userEmail, type)

    fun getLabItemsByTypeAlphabeticalFlow(userEmail: String, type: LabItemType): Flow<List<LabItemEntity>> =
        labDao.getLabItemsByTypeAlphabeticalFlow(userEmail, type)

    suspend fun insertLabItem(item: LabItemEntity) =
        labDao.insertLabItem(item)

    suspend fun deleteLabItem(item: LabItemEntity) =
        labDao.deleteLabItem(item)

    suspend fun deleteLabItemById(id: String) =
        labDao.deleteLabItemById(id)

    /**
     * Purga los datos y ejemplos de prueba precargados por defecto (raíces, modificadores y categorías canónicas de Vyianóra)
     * para que el usuario comience con un léxico completamente propio y limpio.
     */
    suspend fun purgeDefaultExamples(userEmail: String) {
        val existing = labDao.getAllLabItemsFlow(userEmail).first()
        val defaultCategories = VyianoraMasterData.canonicalCategories.toSet()
        val defaultRoots = VyianoraMasterData.canonicalRoots.map { it.root }.toSet()
        val defaultModifiers = VyianoraMasterData.canonicalModifiers.map { it.affix }.toSet()
        existing.forEach { item ->
            val isDefault = when (item.itemType) {
                LabItemType.CATEGORY -> defaultCategories.contains(item.categoryOrType)
                LabItemType.NUCLEUS -> defaultRoots.contains(item.termLanguage)
                LabItemType.MORPHOLOGY -> defaultModifiers.contains(item.termLanguage)
                else -> false
            }
            if (isDefault) {
                labDao.deleteLabItem(item)
            }
        }
    }

    suspend fun ensureDefaultData(userEmail: String) {
        // En lugar de precargar datos de ejemplo, aseguramos la purga de cualquier dato canónico previo
        purgeDefaultExamples(userEmail)
    }

    companion object {
        @Volatile
        private var INSTANCE: CreationLabRepository? = null

        fun getInstance(context: Context): CreationLabRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = CreationLabRepository(db.labDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
