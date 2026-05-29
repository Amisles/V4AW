# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# --- Hilt ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ActivityContextWrapper
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.activity.ComponentActivity { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.util.CursorUtil
-keep class * extends androidx.room.util.TableInfo
-keep class * extends androidx.room.RoomMasterTable

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Required <fields>;
}

# --- OkHttp ---
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }

# --- Media3 (ExoPlayer) ---
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil ---
-dontwarn coil.**
-keep class coil.** { *; }
-keep interface coil.** { *; }

# --- Jsoup ---
-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }
-dontwarn org.jsoup.**

# --- DataStore ---
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# --- Keep data models ---
-keep class org.amisles.v4aw.model.** { *; }
-keep class org.amisles.v4aw.parser.VideoParser$ParseResult { *; }
-keep class org.amisles.v4aw.parser.VideoParser$ParseResult$* { *; }

# --- Keep view models ---
-keep class * extends androidx.lifecycle.ViewModel { *; }

# --- Keep Compose related ---
-keep class * extends androidx.compose.runtime.Composable
-keep @androidx.compose.runtime.Composable class *
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# --- Keep navigation ---
-keep class * extends androidx.navigation.NavDestination
-keepnames class * implements androidx.navigation.NavArgs

# --- GWT repackaging ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gwt.** { *; }
-dontwarn com.google.gwt.**

# --- Keep Parcelable ---
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# --- Keep Serializable ---
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- General ---
-dontwarn javax.annotation.**
-keepnames class * implements java.io.Serializable
-keep class * extends java.lang.Exception