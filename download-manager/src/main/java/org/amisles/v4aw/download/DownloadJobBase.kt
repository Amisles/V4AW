package org.amisles.v4aw.download

internal interface DownloadJobBase {
    suspend fun start()
    fun pause()
    fun cancel()
}
