package org.amisles.v4aw.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.amisles.v4aw.model.SiteRule

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rules ORDER BY priority DESC, updatedAt DESC")
    fun getAllRules(): Flow<List<SiteRule>>

    @Query("SELECT * FROM site_rules WHERE enabled = 1 ORDER BY priority DESC, updatedAt DESC")
    fun getEnabledRules(): Flow<List<SiteRule>>

    @Query("SELECT * FROM site_rules WHERE id = :id")
    suspend fun getRuleById(id: String): SiteRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SiteRule)

    @Update
    suspend fun updateRule(rule: SiteRule)

    @Delete
    suspend fun deleteRule(rule: SiteRule)

    @Query("DELETE FROM site_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
}
