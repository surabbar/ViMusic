package it.vfsfitvnm.vimusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import it.vfsfitvnm.vimusic.enums.ColorPaletteMode
import it.vfsfitvnm.vimusic.enums.ThumbnailRoundness
import it.vfsfitvnm.vimusic.service.PlayerService
import it.vfsfitvnm.vimusic.ui.components.BottomSheetMenu
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.rememberBottomSheetState
import it.vfsfitvnm.vimusic.ui.components.rememberMenuState
import it.vfsfitvnm.vimusic.ui.screens.HomeScreen
import it.vfsfitvnm.vimusic.ui.screens.IntentUriScreen
import it.vfsfitvnm.vimusic.ui.styling.Appearance
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.views.PlayerView
import it.vfsfitvnm.vimusic.utils.colorPaletteModeKey
import it.vfsfitvnm.vimusic.utils.getEnum
import it.vfsfitvnm.vimusic.utils.intent
import it.vfsfitvnm.vimusic.utils.listener
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.rememberHapticFeedback
import it.vfsfitvnm.vimusic.utils.thumbnailRoundnessKey

class MainActivity : ComponentActivity() {
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) {
                this@MainActivity.binder = service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    private var binder by mutableStateOf<PlayerService.Binder?>(null)
    private var uri by mutableStateOf<Uri?>(null, neverEqualPolicy())

    override fun onStart() {
        super.onStart()
        bindService(intent<PlayerService>(), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expandPlayerBottomSheet =
            intent?.extras?.getBoolean("expandPlayerBottomSheet", false) ?: false

        uri = intent?.data

        setContent {
            val isSystemInDarkTheme = isSystemInDarkTheme()

            var appearance by remember(isSystemInDarkTheme) {
                with(preferences) {
                    val colorPaletteMode = getEnum(colorPaletteModeKey, ColorPaletteMode.System)
                    val thumbnailRoundness =
                        getEnum(thumbnailRoundnessKey, ThumbnailRoundness.Light)

                    mutableStateOf(
                        Appearance(
                            colorPalette = colorPaletteMode.palette(isSystemInDarkTheme),
                            typography = colorPaletteMode.typography(isSystemInDarkTheme),
                            thumbnailShape = thumbnailRoundness.shape()
                        )
                    )
                }
            }

            DisposableEffect(isSystemInDarkTheme) {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                        when (key) {
                            colorPaletteModeKey -> {
                                val colorPaletteMode =
                                    sharedPreferences.getEnum(key, ColorPaletteMode.System)

                                appearance = appearance.copy(
                                    colorPalette = colorPaletteMode.palette(isSystemInDarkTheme),
                                    typography = colorPaletteMode.typography(isSystemInDarkTheme),
                                )
                            }
                            thumbnailRoundnessKey -> {
                                val thumbnailRoundness =
                                    sharedPreferences.getEnum(key, ThumbnailRoundness.Light)

                                appearance = appearance.copy(
                                    thumbnailShape = thumbnailRoundness.shape()
                                )
                            }
                        }
                    }

                with(preferences) {
                    registerOnSharedPreferenceChangeListener(listener)

                    onDispose {
                        unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }
            }

            val systemUiController = rememberSystemUiController()

            val rippleTheme =
                remember(appearance.colorPalette.text, appearance.colorPalette.isDark) {
                    object : RippleTheme {
                        @Composable
                        override fun defaultColor(): Color = RippleTheme.defaultRippleColor(
                            contentColor = appearance.colorPalette.text,
                            lightTheme = !appearance.colorPalette.isDark
                        )

                        @Composable
                        override fun rippleAlpha(): RippleAlpha = RippleTheme.defaultRippleAlpha(
                            contentColor = appearance.colorPalette.text,
                            lightTheme = !appearance.colorPalette.isDark
                        )
                    }
                }

            val shimmerTheme = remember {
                defaultShimmerTheme.copy(
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 800,
                            easing = LinearEasing,
                            delayMillis = 250,
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    shaderColors = listOf(
                        Color.Unspecified.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.50f),
                        Color.Unspecified.copy(alpha = 0.25f),
                    ),
                )
            }

            SideEffect {
                systemUiController.setSystemBarsColor(
                    appearance.colorPalette.background,
                    !appearance.colorPalette.isDark
                )
            }

            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalOverscrollConfiguration provides null,
                LocalIndication provides rememberRipple(bounded = false),
                LocalRippleTheme provides rippleTheme,
                LocalShimmerTheme provides shimmerTheme,
                LocalPlayerServiceBinder provides binder,
                LocalMenuState provides rememberMenuState(),
                LocalHapticFeedback provides rememberHapticFeedback()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appearance.colorPalette.background)
                ) {
                    when (val uri = uri) {
                        null -> {
                            val playerBottomSheetState = rememberBottomSheetState(
                                dismissedBound = 0.dp,
                                collapsedBound = Dimensions.collapsedPlayer,
                                expandedBound = maxHeight,
                                isExpanded = expandPlayerBottomSheet
                            )

                            HomeScreen()

                            PlayerView(
                                layoutState = playerBottomSheetState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                            )

                            binder?.player?.let { player ->
                                ExpandPlayerOnPlaylistChange(
                                    player = player,
                                    expand = {
                                        playerBottomSheetState.expand(tween(500))
                                    }
                                )
                            }
                        }
                        else -> IntentUriScreen(uri = uri)
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        uri = intent?.data
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }

@Composable
fun ExpandPlayerOnPlaylistChange(player: Player, expand: () -> Unit) {
    DisposableEffect(player, expand) {
        player.listener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                    expand()
                }
            }
        })
    }
}
