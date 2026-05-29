package org.amisles.v4aw.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@UnstableApi
class PictureInPictureManager @Inject constructor() {
    
    companion object {
        private const val DEFAULT_ASPECT_RATIO_WIDTH = 16
        private const val DEFAULT_ASPECT_RATIO_HEIGHT = 9
    }
    
    private var isInPictureInPictureMode = false
    private var player: Player? = null
    
    fun setPlayer(player: Player) {
        this.player = player
    }
    
    fun isPictureInPictureSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureInPictureMode(
        activity: Activity,
        aspectRatioWidth: Int = DEFAULT_ASPECT_RATIO_WIDTH,
        aspectRatioHeight: Int = DEFAULT_ASPECT_RATIO_HEIGHT
    ): Boolean {
        if (!isPictureInPictureSupported()) {
            return false
        }
        
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false
        }
        
        return try {
            val rational = Rational(aspectRatioWidth, aspectRatioHeight)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            
            activity.enterPictureInPictureMode(params)
            isInPictureInPictureMode = true
            true
        } catch (e: Exception) {
            isInPictureInPictureMode = false
            false
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePictureInPictureParams(
        activity: Activity,
        aspectRatioWidth: Int = DEFAULT_ASPECT_RATIO_WIDTH,
        aspectRatioHeight: Int = DEFAULT_ASPECT_RATIO_HEIGHT
    ) {
        if (!isPictureInPictureSupported() || !isInPictureInPictureMode) {
            return
        }
        
        try {
            val rational = Rational(aspectRatioWidth, aspectRatioHeight)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            
            activity.setPictureInPictureParams(params)
        } catch (e: Exception) {
        }
    }
    
    fun onPictureInPictureModeChanged(isInPictureInPicture: Boolean) {
        isInPictureInPictureMode = isInPictureInPicture
        
        if (isInPictureInPicture) {
            player?.playWhenReady = true
        }
    }
    
    fun isInPictureInPicture(): Boolean = isInPictureInPictureMode
    
    fun clearPlayer() {
        player = null
    }
}
