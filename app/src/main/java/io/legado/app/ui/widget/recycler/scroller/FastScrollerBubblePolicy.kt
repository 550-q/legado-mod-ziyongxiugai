package io.legado.app.ui.widget.recycler.scroller

/**
 * 快速滚动条气泡手柄的触发策略（View 与 Compose 两套实现共用）。
 *
 * 设计要点：
 * 1. 内容不足 3 屏的列表永远不出气泡——短列表用不上定位，弹出来只会挡内容；
 * 2. 同一次手势里累计滑过 1/3 屏才算「想快速定位」，轻划一下不弹；
 * 3. fling 期间不新弹气泡（手指不在屏上，抓不到），等它停下来再保留 1.5 秒；
 * 4. 一次手势只弹一次，结束后延迟淡出，避免来回慢滑时反复闪烁。
 */
object FastScrollerBubblePolicy {

    /** 内容至少要有 3 屏（可见区域 + 可滚动距离），否则永不显示气泡。 */
    const val MIN_CONTENT_SCREENS = 3f

    /** 拖动阶段：累计滑过 1/3 屏就显示气泡。 */
    const val DRAG_TRIGGER_SCREENS = 1f / 3f

    /** 拖动阶段：速度达到 2 屏/秒也算（快速连甩），作为辅助条件。 */
    const val VELOCITY_TRIGGER_SCREENS_PER_SEC = 2f

    /** fling 停下来后：整段手势累计滑过 1 屏才补弹气泡。 */
    const val SETTLE_TRIGGER_SCREENS = 1f

    /** 手势结束后气泡的保留时长（毫秒）。 */
    const val SETTLE_HOLD_MS = 1500L

    /** 速度判据生效前至少要滑过的距离，避免抖一下就触发。 */
    const val MIN_FLICK_DISTANCE_DP = 24f

    /** 距离判据的下限 / 上限（dp），由 1/3 屏换算后再夹紧。 */
    const val MIN_DRAG_TRIGGER_DP = 48f
    const val MAX_DRAG_TRIGGER_DP = 160f

    /**
     * 像素口径：内容是否够长。
     * [scrollablePx] 为可滚动距离（内容高度 - 视口高度）。
     */
    fun hasEnoughContent(scrollablePx: Int, viewportPx: Int): Boolean =
        viewportPx > 0 && scrollablePx >= viewportPx * (MIN_CONTENT_SCREENS - 1f)

    /**
     * 条目口径：Compose 列表拿不到内容总高度，用「总条目 >= 2 屏可见条目」近似。
     */
    fun hasEnoughContentByCount(visibleCount: Int, totalCount: Int): Boolean =
        visibleCount > 0 && totalCount >= visibleCount * MIN_CONTENT_SCREENS

    /** 拖动距离阈值：1/3 屏，夹在 48~160dp 之间。 */
    fun dragTriggerPx(viewportPx: Int, density: Float): Float =
        (viewportPx * DRAG_TRIGGER_SCREENS)
            .coerceIn(MIN_DRAG_TRIGGER_DP * density, MAX_DRAG_TRIGGER_DP * density)

    fun minFlickDistancePx(density: Float): Float = MIN_FLICK_DISTANCE_DP * density
}
