package uz.selerown.fevapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

/** App entry point: sets up Hilt and the (offline) image loader. */
@HiltAndroidApp
class FevApplication : Application(), SingletonImageLoader.Factory {

    /** Coil loads photos and video frames (timeline/Home thumbnails) from local URIs only. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
