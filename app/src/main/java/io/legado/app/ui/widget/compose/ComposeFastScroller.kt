package io.legado.app.ui.widget.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.help.config.FastScrollerConfig
import io.legado.app.ui.widget.recycler.scroller.FastScrollerBubblePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 快速滚动条（列表）。
 *
 * 默认只显示一根细滑块；滚动列表或按住滑块时，滑块左侧弹出一个胶囊气泡手柄，
 * 气泡大小由「我的-其他设置-快速滚动条气泡大小」控制（5 档预设）。
 * 拖动滑块/气泡即可快速定位，气泡中心与滑块中心对齐，按下时不跳位置。
 */
@Composable
fun ComposeLazyListFastScroller(
    state: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    if (!enabled) return
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    // 内容不足两屏的短列表不需要定位气泡，直接不画
    if (!FastScrollerBubblePolicy.hasEnoughContentByCount(visibleItems.size, totalItems)) return
    val isDragged by state.interactionSource.collectIsDraggedAsState()
    val firstItem = visibleItems.first()
    val firstItemSize = firstItem.size
    val scrolled = firstItem.index + if (firstItemSize > 0) {
        state.firstVisibleItemScrollOffset.toFloat() / firstItemSize
    } else {
        0f
    }
    FastScrollerOverlay(
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        totalItems = totalItems,
        visibleCount = visibleItems.size,
        scrollPosition = scrolled,
        scrolling = state.isScrollInProgress,
        isUserDragging = isDragged,
        onSeekItem = { index -> state.requestScrollToItem(index) }
    )
}

/**
 * 快速滚动条（网格）。
 */
@Composable
fun ComposeLazyGridFastScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    if (!enabled) return
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    if (!FastScrollerBubblePolicy.hasEnoughContentByCount(visibleItems.size, totalItems)) return
    val isDragged by state.interactionSource.collectIsDraggedAsState()
    val firstItem = visibleItems.first()
    val firstItemSize = firstItem.size.height
    val scrolled = firstItem.index + if (firstItemSize > 0) {
        state.firstVisibleItemScrollOffset.toFloat() / firstItemSize
    } else {
        0f
    }
    FastScrollerOverlay(
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        totalItems = totalItems,
        visibleCount = visibleItems.size,
        scrollPosition = scrolled,
        scrolling = state.isScrollInProgress,
        isUserDragging = isDragged,
        onSeekItem = { index -> state.requestScrollToItem(index) }
    )
}

@Composable
private fun FastScrollerOverlay(
    modifier: Modifier,
    topInset: Dp,
    bottomInset: Dp,
    totalItems: Int,
    visibleCount: Int,
    scrollPosition: Float,
    scrolling: Boolean,
    isUserDragging: Boolean,
    onSeekItem: (Int) -> Unit
) {
    val palette = rememberAppManagementPalette()
    val density = LocalDensity.current

    val bubbleSize = FastScrollerConfig.bubbleSizeDp.dp
    val bubbleWidth = bubbleSize * 1.5f
    val thumbWidth = 4.dp
    val bubbleGap = 6.dp
    val edgePadding = 2.dp
    val stripWidth = bubbleWidth + bubbleGap + thumbWidth + edgePadding * 2

    val bubblePx = with(density) { bubbleSize.toPx() }
    val bubbleWidthPx = with(density) { bubbleWidth.toPx() }
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val gapPx = with(density) { bubbleGap.toPx() }
    val edgePaddingPx = with(density) { edgePadding.toPx() }
    val topInsetPx = with(density) { topInset.toPx() }
    val bottomInsetPx = with(density) { bottomInset.toPx() }
    val minThumbPx = with(density) { 44.dp.toPx() }
    val touchExpandPx = with(density) { 12.dp.toPx() }

    var viewHeight by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    var dragGrabTop by remember { mutableFloatStateOf(-1f) }
    var barVisible by remember { mutableStateOf(false) }
    var bubbleShown by remember { mutableStateOf(false) }

    val active = scrolling || dragging
    LaunchedEffect(active) {
        if (active) {
            barVisible = true
        } else {
            delay(900)
            if (!dragging) barVisible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(160),
        label = "fastScrollerAlpha"
    )

    // 气泡停留期：手势结束（或松开滚动条）后保留 1.5 秒；
    // 停留期内手指一碰列表就立即收起，不去碰就等计时结束再收起。
    var holdActive by remember { mutableStateOf(false) }
    var holdTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(holdTick) {
        if (!holdActive) return@LaunchedEffect
        val token = holdTick
        delay(FastScrollerBubblePolicy.SETTLE_HOLD_MS)
        if (token == holdTick) {
            holdActive = false
            bubbleShown = false
        }
    }

    // 手势累计滑过的「屏数」，决定气泡什么时候弹（拖动中 1/3 屏，松手后补判 1 屏）
    val positionState = rememberUpdatedState(scrollPosition)
    val scrollingState = rememberUpdatedState(scrolling)
    val draggingState = rememberUpdatedState(isUserDragging)
    val countState = rememberUpdatedState(visibleCount)
    LaunchedEffect(Unit) {
        var previous = positionState.value
        var distance = 0f
        var inGesture = false
        var shown = false
        snapshotFlow {
            Triple(positionState.value, scrollingState.value, draggingState.value)
        }.collect { (position, isScrolling, isBarDragging) ->
            val delta = abs(position - previous)
            previous = position
            if (isScrolling && !inGesture) {
                inGesture = true
                distance = 0f
                shown = false
                // 新手势开始，停留计时作废
                holdActive = false
                holdTick++
                // 停留期的气泡：手指一碰列表就立即收起
                if (isBarDragging && bubbleShown) {
                    bubbleShown = false
                }
            }
            if (!inGesture) return@collect
            distance += delta
            val screens = distance / countState.value.coerceAtLeast(1)
            if (isScrolling) {
                if (isBarDragging && !shown &&
                    screens >= FastScrollerBubblePolicy.DRAG_TRIGGER_SCREENS
                ) {
                    shown = true
                    bubbleShown = true
                }
            } else {
                inGesture = false
                if (!shown && screens >= FastScrollerBubblePolicy.SETTLE_TRIGGER_SCREENS) {
                    shown = true
                    bubbleShown = true
                }
                // 手势结束，进入停留期
                if (bubbleShown) {
                    holdActive = true
                    holdTick++
                }
            }
        }
    }
    val bubbleVisible = bubbleShown || dragging
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0f,
        animationSpec = tween(160),
        label = "fastScrollerBubbleAlpha"
    )
    // 从滚动条那一侧「弹出」的位移进度（带一点回弹）
    val bubblePop by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fastScrollerBubblePop"
    )

    // scrollPosition 是「第几个条目」，这里换算成 0~1 的比例。
    // 之前直接把条目序号当比例用，滑动一点就超过 1 被截断，滑块会瞬间跳到底部。
    val scrollFraction = if (totalItems > 1) {
        (scrollPosition / (totalItems - 1)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction = (if (dragFraction >= 0f) dragFraction else scrollFraction).coerceIn(0f, 1f)
    val trackHeight = (viewHeight - topInsetPx - bottomInsetPx).coerceAtLeast(0f)
    val thumbHeight = if (trackHeight <= 0f) {
        0f
    } else {
        max(minThumbPx, trackHeight * visibleCount / totalItems).coerceAtMost(trackHeight)
    }
    val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0f)
    val thumbTop = topInsetPx + fraction * thumbTravel
    val thumbLeft = with(density) { stripWidth.toPx() } - edgePaddingPx - thumbWidthPx
    val bubbleLeft = thumbLeft - gapPx - bubbleWidthPx
    val bubbleTop = if (trackHeight <= 0f || bubblePx >= trackHeight) {
        topInsetPx
    } else {
        (thumbTop + thumbHeight / 2f - bubblePx / 2f)
            .coerceIn(topInsetPx, topInsetPx + trackHeight - bubblePx)
    }

    // 手势只覆盖滑块 / 气泡这一小块区域，避免挡住列表右侧条目的点击。
    val grabTop = if (bubbleVisible) {
        minOf(bubbleTop, thumbTop - touchExpandPx)
    } else {
        thumbTop - touchExpandPx
    }.coerceAtLeast(0f)
    val grabBottom = if (bubbleVisible) {
        maxOf(bubbleTop + bubblePx, thumbTop + thumbHeight + touchExpandPx)
    } else {
        thumbTop + thumbHeight + touchExpandPx
    }.coerceAtMost(viewHeight.toFloat())
    val grabHeight = (grabBottom - grabTop).coerceAtLeast(0f)

    val geometry = ScrollbarGeometry(
        top = topInsetPx,
        thumbTop = thumbTop,
        thumbHeight = thumbHeight,
        thumbTravel = thumbTravel,
        thumbLeft = thumbLeft,
        thumbWidth = thumbWidthPx,
        bubbleLeft = bubbleLeft,
        bubbleTop = bubbleTop,
        bubbleWidth = bubbleWidthPx,
        bubbleHeight = bubblePx,
        touchExpand = touchExpandPx
    )
    val gestureInfo = rememberUpdatedState(
        ScrollbarGestureInfo(
            geometry = geometry,
            grabTop = grabTop,
            totalItems = totalItems,
            onSeekItem = onSeekItem,
            onDragStart = {
                dragGrabTop = grabTop
                dragging = true
                holdActive = false
                holdTick++
            },
            onDraggingChange = { isDragging ->
                dragging = isDragging
                if (!isDragging) {
                    // 松开滚动条后气泡留在原地 1.5 秒，方便顺手抓；碰列表则立即收起
                    bubbleShown = true
                    holdActive = true
                    holdTick++
                }
            },
            onFractionChange = { dragFraction = it }
        )
    )
    // 拖动期间手势区域固定在按下的位置，否则节点随滑块移动会让本地坐标一直变，拖动会抖。
    val gestureTop = if (dragging && dragGrabTop >= 0f) dragGrabTop else grabTop

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(stripWidth)
            .onSizeChanged { viewHeight = it.height }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (thumbHeight <= 0f) return@Canvas
            // 滑块（气泡可能比滑块留得更久，两条独立淡出）
            if (alpha > 0.01f) {
                drawRoundRect(
                    color = palette.settings.secondaryText.copy(alpha = 0.55f * alpha),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeight),
                    cornerRadius = CornerRadius(thumbWidthPx / 2f)
                )
            }
            if (bubbleAlpha <= 0.01f || bubbleWidthPx <= 0f || bubblePx <= 0f) return@Canvas
            // 气泡从滚动条那一侧滑出来
            val popShift = (1f - bubblePop) * bubbleWidthPx * 0.65f
            val drawBubbleLeft = bubbleLeft + popShift
            // 气泡手柄
            drawRoundRect(
                color = palette.settings.accent.copy(alpha = 0.86f * bubbleAlpha),
                topLeft = Offset(drawBubbleLeft, bubbleTop),
                size = Size(bubbleWidthPx, bubblePx),
                cornerRadius = CornerRadius(bubblePx / 2f)
            )
            // 气泡里的抓握纹（三条横线）
            val gripColor = palette.settings.onAccent.copy(alpha = 0.92f * bubbleAlpha)
            val barWidth = bubbleWidthPx * 0.42f
            val barHeight = (bubblePx * 0.055f).coerceAtLeast(1f)
            val barGap = bubblePx * 0.17f
            val gripCenterX = drawBubbleLeft + bubbleWidthPx / 2f
            val gripCenterY = bubbleTop + bubblePx / 2f
            for (i in -1..1) {
                val barCenterY = gripCenterY + i * barGap
                drawRoundRect(
                    color = gripColor,
                    topLeft = Offset(gripCenterX - barWidth / 2f, barCenterY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )
            }
        }
        if (grabHeight > 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, gestureTop.roundToInt()) }
                    .width(stripWidth)
                    .height(with(density) { grabHeight.toDp() })
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val info = gestureInfo.value
                            val downY = info.grabTop + down.position.y
                            if (!info.geometry.hit(down.position.x, downY)) {
                                return@awaitEachGesture
                            }
                            down.consume()
                            info.onDragStart()
                            var lastIndex = -1
                            fun seek(y: Float) {
                                val f = info.geometry.fractionFor(y)
                                info.onFractionChange(f)
                                val index = (f * (info.totalItems - 1)).roundToInt()
                                    .coerceIn(0, info.totalItems - 1)
                                if (index != lastIndex) {
                                    lastIndex = index
                                    info.onSeekItem(index)
                                }
                            }
                            seek(downY)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChanged()) {
                                    change.consume()
                                    seek(info.grabTop + change.position.y)
                                }
                            }
                            info.onDraggingChange(false)
                            info.onFractionChange(-1f)
                            dragGrabTop = -1f
                        }
                    }
            )
        }
    }
}

private class ScrollbarGeometry(
    val top: Float,
    val thumbTop: Float,
    val thumbHeight: Float,
    val thumbTravel: Float,
    val thumbLeft: Float,
    val thumbWidth: Float,
    val bubbleLeft: Float,
    val bubbleTop: Float,
    val bubbleWidth: Float,
    val bubbleHeight: Float,
    val touchExpand: Float
) {
    fun fractionFor(y: Float): Float {
        if (thumbTravel <= 0f) return 0f
        return ((y - top - thumbHeight / 2f) / thumbTravel).coerceIn(0f, 1f)
    }

    fun hit(x: Float, y: Float): Boolean {
        if (x >= bubbleLeft && x <= bubbleLeft + bubbleWidth &&
            y >= bubbleTop && y <= bubbleTop + bubbleHeight
        ) {
            return true
        }
        return x >= thumbLeft - touchExpand &&
                x <= thumbLeft + thumbWidth + touchExpand &&
                y >= thumbTop - touchExpand &&
                y <= thumbTop + thumbHeight + touchExpand
    }
}

private class ScrollbarGestureInfo(
    val geometry: ScrollbarGeometry,
    val grabTop: Float,
    val totalItems: Int,
    val onSeekItem: (Int) -> Unit,
    val onDragStart: () -> Unit,
    val onDraggingChange: (Boolean) -> Unit,
    val onFractionChange: (Float) -> Unit
)
