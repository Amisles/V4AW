package org.amisles.v4aw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.amisles.v4aw.data.cache.ParseResultCache
import org.amisles.v4aw.i18n.LanguageProvider
import org.amisles.v4aw.ui.MainScreen
import org.amisles.v4aw.ui.theme.V4awTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var parseResultCache: ParseResultCache
    
    private var pipModeChangedListener: ((Boolean) -> Unit)? = null
    
    fun setPictureInPictureModeChangedListener(listener: (Boolean) -> Unit) {
        pipModeChangedListener = listener
    }
    
    fun clearPictureInPictureModeChangedListener() {
        pipModeChangedListener = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            V4awTheme {
                LanguageProvider {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        MainScreen(
                            navController = navController,
                            parseResultCache = parseResultCache
                        )
                    }
                }
            }
        }
    }
    
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipModeChangedListener?.invoke(isInPictureInPictureMode)
    }
}
