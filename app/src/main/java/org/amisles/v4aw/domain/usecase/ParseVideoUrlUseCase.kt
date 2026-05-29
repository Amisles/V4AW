package org.amisles.v4aw.domain.usecase

import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.domain.repository.VideoRepository
import javax.inject.Inject

class ParseVideoUrlUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke(url: String): ParseResult {
        return videoRepository.parseVideoUrl(url)
    }
}