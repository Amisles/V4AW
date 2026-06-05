package org.amisles.v4aw.domain.usecase

import org.amisles.v4aw.domain.repository.SiteRuleRepository
import org.amisles.v4aw.model.SiteRule
import javax.inject.Inject

class MatchSiteRuleUseCase @Inject constructor(
    private val siteRuleRepository: SiteRuleRepository
) {
    suspend operator fun invoke(url: String): SiteRule? {
        return siteRuleRepository.matchRule(url)
    }
}
