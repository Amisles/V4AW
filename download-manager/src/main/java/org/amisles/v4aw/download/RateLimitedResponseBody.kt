package org.amisles.v4aw.download

import okhttp3.ResponseBody
import okio.*

class RateLimitedResponseBody(
    private val delegate: ResponseBody,
    private val bytesPerSecond: Long
) : ResponseBody() {

    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun source(): BufferedSource {
        return RateLimitedSource(delegate.source(), bytesPerSecond).buffer()
    }

    private class RateLimitedSource(
        private val source: Source,
        private val bytesPerSecond: Long
    ) : Source {

        private var bytesThisSecond = 0L
        private var secondStartMs = System.currentTimeMillis()

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (bytesPerSecond <= 0) {
                return source.read(sink, byteCount)
            }

            val now = System.currentTimeMillis()
            val elapsed = now - secondStartMs

            if (elapsed >= 1000) {
                secondStartMs = now
                bytesThisSecond = 0L
            } else if (bytesThisSecond >= bytesPerSecond) {
                val sleepMs = 1000 - elapsed
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs)
                }
                secondStartMs = System.currentTimeMillis()
                bytesThisSecond = 0L
            }

            val remaining = bytesPerSecond - bytesThisSecond
            val toRead = minOf(byteCount, remaining)

            val read = source.read(sink, toRead)
            if (read > 0) {
                bytesThisSecond += read
            }
            return read
        }

        override fun close() = source.close()

        override fun timeout() = source.timeout()
    }
}
