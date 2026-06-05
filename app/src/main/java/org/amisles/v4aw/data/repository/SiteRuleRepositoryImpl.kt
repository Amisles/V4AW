package org.amisles.v4aw.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.amisles.v4aw.data.local.dao.SiteRuleDao
import org.amisles.v4aw.domain.repository.SiteRuleRepository
import org.amisles.v4aw.model.SiteRule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteRuleRepositoryImpl @Inject constructor(
    private val siteRuleDao: SiteRuleDao
) : SiteRuleRepository {

    override fun getAllRules(): Flow<List<SiteRule>> {
        return siteRuleDao.getAllRules()
    }

    override fun getEnabledRules(): Flow<List<SiteRule>> {
        return siteRuleDao.getEnabledRules()
    }

    override suspend fun getRuleById(id: String): SiteRule? {
        return siteRuleDao.getRuleById(id)
    }

    override suspend fun saveRule(rule: SiteRule) {
        val existing = siteRuleDao.getRuleById(rule.id)
        if (existing != null) {
            siteRuleDao.updateRule(rule.copy(updatedAt = System.currentTimeMillis()))
        } else {
            siteRuleDao.insertRule(rule)
        }
    }

    override suspend fun deleteRule(rule: SiteRule) {
        siteRuleDao.deleteRule(rule)
    }

    override suspend fun matchRule(url: String): SiteRule? {
        val enabledRules = siteRuleDao.getEnabledRules().firstOrNull() ?: return null
        return matchRuleFromList(url, enabledRules)
    }

    private fun matchRuleFromList(url: String, rules: List<SiteRule>): SiteRule? {
        return rules
            .filter { rule ->
                try {
                    Regex(rule.urlPattern).containsMatchIn(url)
                } catch (e: Exception) {
                    url.contains(rule.urlPattern)
                }
            }
            .maxByOrNull { it.priority }
    }
}
