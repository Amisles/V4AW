package org.amisles.v4aw.domain.repository

import kotlinx.coroutines.flow.Flow
import org.amisles.v4aw.model.SiteRule

interface SiteRuleRepository {
    fun getAllRules(): Flow<List<SiteRule>>
    fun getEnabledRules(): Flow<List<SiteRule>>
    suspend fun getRuleById(id: String): SiteRule?
    suspend fun saveRule(rule: SiteRule)
    suspend fun deleteRule(rule: SiteRule)
    suspend fun matchRule(url: String): SiteRule?
}
