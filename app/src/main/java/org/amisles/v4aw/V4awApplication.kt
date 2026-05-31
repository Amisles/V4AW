package org.amisles.v4aw

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import org.amisles.v4aw.webview.WebViewManager
import javax.inject.Inject

@HiltAndroidApp
class V4awApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var webViewManager: WebViewManager

    override fun onCreate() {
        super.onCreate()
        prewarmWebView()
    }

    private fun prewarmWebView() {
        Thread {
            try {
                webViewManager.prewarm()
                Log.d("V4awApp", "WebView prewarm initiated")
            } catch (e: Exception) {
                Log.w("V4awApp", "WebView prewarm failed: ${e.message}")
            }
        }.start()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}
