package org.amisles.v4aw.domain.usecase

import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.domain.repository.VideoRepository
import javax.inject.Inject

class GetVideoSourceUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke(videoInfo: VideoInfo): String? {
        return videoRepository.getVideoSource(videoInfo)
    }
}