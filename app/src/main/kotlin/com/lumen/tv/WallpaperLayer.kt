package com.lumen.tv

import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import java.io.File

/** Animierte GIFs (auf schwachen Geräten eher sparsam einsetzen). */
@Composable
fun AnimatedWall(file: File, blur: Dp) {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.04).build() }.build()
    }
    androidx.compose.runtime.DisposableEffect(loader) { onDispose { loader.shutdown() } }
    AsyncImage(
        model = file, imageLoader = loader, contentDescription = null,
        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(blur),
    )
}

/** Stummes Endlos-Video als Hintergrund, pausiert automatisch, wenn Lumen im Hintergrund ist. */
@Composable
fun VideoWall(file: File) {
    val ctx = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { player.play() }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c -> (LayoutInflater.from(c).inflate(R.layout.wall_player, null) as PlayerView).also { it.player = player } },
    )
}
