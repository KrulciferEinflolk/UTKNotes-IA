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
     * Inicializa datos predeterminados esenciales si el usuario no tiene ninguno guardado.
     */
    suspend fun ensureDefaultData(userEmail: String) {
        val existing = labDao.getAllLabItemsFlow(userEmail).first()
        if (existing.isEmpty()) {
            // Categorías canónicas oficiales de Vyianóra
            val categories = VyianoraMasterData.canonicalCategories
            categories.forEach { cat ->
                labDao.insertLabItem(
                    LabItemEntity(
                        userEmail = userEmail,
                        itemType = LabItemType.CATEGORY,
                        categoryOrType = cat,
                        termSpanish = "Dominio conceptual de $cat"
                    )
                )
            }

            // 9 Raíces fundamentales canónicas oficiales de Vyianóra
            VyianoraMasterData.canonicalRoots.forEach { r ->
                labDao.insertLabItem(
                    LabItemEntity(
                        userEmail = userEmail,
                        itemType = LabItemType.NUCLEUS,
                        termLanguage = r.root,
                        termSpanish = r.meaningSpanish,
                        categoryOrType = r.category
                    )
                )
            }

            // 11 Modificadores oficiales (Prefijos y Sufijos) de Vyianóra
            VyianoraMasterData.canonicalModifiers.forEach { m ->
                labDao.insertLabItem(
                    LabItemEntity(
                        userEmail = userEmail,
                        itemType = LabItemType.MORPHOLOGY,
                        termLanguage = m.affix,
                        categoryOrType = m.type,
                        meaningLanguage = m.semanticImpact,
                        meaningSpanish = m.meaningSpanish
                    )
                )
            }
        }
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
