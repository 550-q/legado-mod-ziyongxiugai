package io.legado.app.help.config

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import splitties.init.appCtx

/**
 * 快速滚动条配置的 Compose 可观察副本。
 *
 * [AppConfig] 的属性是直接读 SharedPreferences 的普通属性，在 Compose 里读不到变化，
 * 改设置后要重启才生效。这里注册偏好变化监听，把值同步到 snapshot state，
 * 界面（书架开关、气泡大小）改完立即重组生效。
 */
object FastScrollerConfig {

    /** 气泡大小（dp），0~4 对应 [AppConfig.FAST_SCROLLER_BUBBLE_SIZE_DP]。 */
    var bubbleSizeDp by mutableIntStateOf(AppConfig.fastScrollerBubbleSizeDp)
        private set

    /** 书架是否显示快速滚动条。 */
    var bookshelfScrollerEnabled by mutableStateOf(AppConfig.showBookshelfFastScroller)
        private set

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PreferKey.fastScrollerBubbleSize ->
                    bubbleSizeDp = AppConfig.fastScrollerBubbleSizeDp

                PreferKey.showBookshelfFastScroller ->
                    bookshelfScrollerEnabled = AppConfig.showBookshelfFastScroller
            }
        }

    init {
        appCtx.defaultSharedPreferences
            .registerOnSharedPreferenceChangeListener(preferenceListener)
    }
}
