package io.legado.app.ui.widget.recycler.scroller

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getCompatColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@Suppress("SameParameterValue")
class FastScroller : LinearLayout {
    @ColorInt
    private var mBubbleColor: Int = 0

    @ColorInt
    private var mHandleColor: Int = 0
    private var mBubbleHeight: Int = 0
    private var mHandleHeight: Int = 0
    private var mViewHeight: Int = 0
    private var mFadeScrollbar: Boolean = false
    private var mShowBubble: Boolean = false
    private var mSectionIndexer: SectionIndexer? = null
    private var mScrollbarAnimator: ViewPropertyAnimator? = null
    private var mBubbleAnimator: ViewPropertyAnimator? = null
    private var mRecyclerView: RecyclerView? = null
    private lateinit var mBubbleView: FastScrollBubble
    private lateinit var mHandleView: ImageView
    private lateinit var mTrackView: ImageView
    private lateinit var mScrollbar: View
    private var mBubbleImage: Drawable? = null
    private var mHandleImage: Drawable? = null
    private var mTrackImage: Drawable? = null
    private var mFastScrollStateChangeListener: FastScrollStateChangeListener? = null
    private var mDragFromBubble = false
    private var mLastTouchY = 0f
    private var mDragCenterY = 0f
    private val mTouchExpandPx = 12f * resources.displayMetrics.density
    // 滚动条和气泡分开淡出：气泡要比滚动条多留一会儿，方便松手后去抓
    private val mScrollbarHider = Runnable { hideScrollbar() }
    private val mBubbleHider = Runnable { hideBubble() }
    private var mGestureDistancePx = 0f
    private var mVelocityPxPerSecond = 0f
    private var mLastScrollTimeNanos = 0L
    private var mBubbleShownInGesture = false

    private val mScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isEnabled) {
                return
            }
            if (mHandleView.isSelected) {
                return
            }
            setViewPositions(getScrollProportion(recyclerView))
            if (!isViewVisible(mScrollbar)) {
                showScrollbar()
            }
            trackGesture(recyclerView, dy)
            handler.removeCallbacks(mScrollbarHider)
            if (mFadeScrollbar) {
                handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (isEnabled) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        handler.removeCallbacks(mScrollbarHider)
                        handler.removeCallbacks(mBubbleHider)
                        cancelAnimation(mScrollbarAnimator)
                        if (!isViewVisible(mScrollbar)) {
                            showScrollbar()
                        }
                        // 停留期内手指一碰列表，气泡立即淡出；不去碰则等 1.5 秒再淡出
                        if (!mHandleView.isSelected && isViewVisible(mBubbleView)) {
                            hideBubble()
                        }
                        startGestureTracking()
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> if (!mHandleView.isSelected) {
                        if (mFadeScrollbar) {
                            handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
                        }
                        settleGesture()
                    }
                }
            }
        }
    }

    constructor(context: Context) : super(context) {
        layout(context, null)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        layout(context, attrs)
        layoutParams = generateLayoutParams(attrs)
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams) {
        params.width = LayoutParams.WRAP_CONTENT
        super.setLayoutParams(params)
    }

    fun setLayoutParams(viewGroup: ViewGroup) {
        @IdRes val recyclerViewId = mRecyclerView?.id ?: View.NO_ID
        val marginTop = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_top)
        val marginBottom =
            resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_bottom)
        require(recyclerViewId != View.NO_ID) { "RecyclerView must have a view ID" }
        when (viewGroup) {
            is ConstraintLayout -> {
                val constraintSet = ConstraintSet()
                @IdRes val layoutId = id
                constraintSet.clone(viewGroup)
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.TOP,
                    recyclerViewId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.BOTTOM,
                    recyclerViewId,
                    ConstraintSet.BOTTOM
                )
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.END,
                    recyclerViewId,
                    ConstraintSet.END
                )
                constraintSet.applyTo(viewGroup)
                val layoutParams = layoutParams as ConstraintLayout.LayoutParams
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is CoordinatorLayout -> {
                val layoutParams = layoutParams as CoordinatorLayout.LayoutParams
                layoutParams.anchorId = recyclerViewId
                layoutParams.anchorGravity = GravityCompat.END
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is FrameLayout -> {
                val layoutParams = layoutParams as FrameLayout.LayoutParams
                layoutParams.gravity = GravityCompat.END
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is RelativeLayout -> {
                val layoutParams = layoutParams as RelativeLayout.LayoutParams
                val endRule = RelativeLayout.ALIGN_END
                layoutParams.addRule(RelativeLayout.ALIGN_TOP, recyclerViewId)
                layoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, recyclerViewId)
                layoutParams.addRule(endRule, recyclerViewId)
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            else -> throw IllegalArgumentException("Parent ViewGroup must be a ConstraintLayout, CoordinatorLayout, FrameLayout, or RelativeLayout")
        }
        applyBubbleMetrics()
        updateViewHeights()
    }

    fun setSectionIndexer(sectionIndexer: SectionIndexer?) {
        mSectionIndexer = sectionIndexer
    }

    fun attachRecyclerView(recyclerView: RecyclerView) {
        mRecyclerView = recyclerView
        mRecyclerView!!.addOnScrollListener(mScrollListener)
        post {
            // set initial positions for bubble and handle
            setViewPositions(getScrollProportion(mRecyclerView))
        }
    }

    fun detachRecyclerView() {
        if (mRecyclerView != null) {
            mRecyclerView!!.removeOnScrollListener(mScrollListener)
            mRecyclerView = null
        }
    }

    /**
     * Hide the scrollbar when not scrolling.
     *
     * @param fadeScrollbar True to hide the scrollbar, false to show
     */
    fun setFadeScrollbar(fadeScrollbar: Boolean) {
        mFadeScrollbar = fadeScrollbar
        mScrollbar.visibility = if (fadeScrollbar) View.INVISIBLE else View.VISIBLE
    }

    /**
     * Show the section bubble while scrolling.
     *
     * @param visible True to show the bubble, false to hide
     */
    fun setBubbleVisible(visible: Boolean) {
        mShowBubble = visible
    }

    /**
     * Display a scroll track while scrolling.
     *
     * @param visible True to show scroll track, false to hide
     */
    fun setTrackVisible(visible: Boolean) {
        mTrackView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Set the color of the scroll track.
     *
     * @param color The color for the scroll track
     */
    fun setTrackColor(@ColorInt color: Int) {
        if (mTrackImage == null) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.fastscroll_track)
            if (drawable != null) {
                mTrackImage = DrawableCompat.wrap(drawable)
            }
        }
        DrawableCompat.setTint(mTrackImage!!, color)
        mTrackView.setImageDrawable(mTrackImage)
    }

    /**
     * Set the color for the scroll handle.
     *
     * @param color The color for the scroll handle
     */
    fun setHandleColor(@ColorInt color: Int) {
        mHandleColor = color
        if (mHandleImage == null) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.fastscroll_handle)
            if (drawable != null) {
                mHandleImage = DrawableCompat.wrap(drawable)
            }
        }
        DrawableCompat.setTint(mHandleImage!!, mHandleColor)
        mHandleView.setImageDrawable(mHandleImage)
    }

    /**
     * Set the background color of the drag bubble.
     *
     * @param color The background color for the drag bubble
     */
    fun setBubbleColor(@ColorInt color: Int) {
        mBubbleColor = color
        mBubbleView.setBubbleColor(color)
    }

    /**
     * 气泡已改为抓握手柄，不再显示文字；保留接口避免调用方编译失败。
     */
    @Suppress("UNUSED_PARAMETER")
    fun setBubbleTextColor(@ColorInt color: Int) = Unit

    /**
     * Set the fast scroll state change listener.
     *
     * @param fastScrollStateChangeListener The interface that will listen to fastscroll state change events
     */
    fun setFastScrollStateChangeListener(fastScrollStateChangeListener: FastScrollStateChangeListener) {
        mFastScrollStateChangeListener = fastScrollStateChangeListener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!mScrollbar.isVisible) {
                    return false
                }
                if (!isHitTarget(event.x, event.y)) {
                    return false
                }
                requestDisallowInterceptTouchEvent(true)
                setHandleSelected(true)
                handler.removeCallbacks(mScrollbarHider)
                handler.removeCallbacks(mBubbleHider)
                mBubbleShownInGesture = true
                cancelAnimation(mScrollbarAnimator)
                cancelAnimation(mBubbleAnimator)
                showBubble()
                if (mFastScrollStateChangeListener != null) {
                    mFastScrollStateChangeListener!!.onFastScrollStart(this)
                }
                mDragFromBubble = event.x < mScrollbar.x
                mLastTouchY = event.y
                if (mDragFromBubble) {
                    mDragCenterY = mScrollbar.y + mHandleView.y + mHandleHeight / 2f
                } else {
                    setViewPositions(event.y)
                    setRecyclerViewPosition(event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                if (mDragFromBubble) {
                    mDragCenterY += y - mLastTouchY
                    mLastTouchY = y
                    val centerY = mDragCenterY.coerceIn(0f, mViewHeight.toFloat())
                    setViewPositions(centerY)
                    setRecyclerViewPosition(centerY)
                } else {
                    setViewPositions(y)
                    setRecyclerViewPosition(y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                requestDisallowInterceptTouchEvent(false)
                setHandleSelected(false)
                mDragFromBubble = false
                if (mFadeScrollbar) {
                    handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
                }
                if (isViewVisible(mBubbleView)) {
                    handler.removeCallbacks(mBubbleHider)
                    handler.postDelayed(
                        mBubbleHider,
                        FastScrollerBubblePolicy.SETTLE_HOLD_MS
                    )
                }
                if (mFastScrollStateChangeListener != null) {
                    mFastScrollStateChangeListener!!.onFastScrollStop(this)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isHitTarget(x: Float, y: Float): Boolean {
        if (isViewVisible(mBubbleView)) {
            val bubbleX = mBubbleView.x
            val bubbleY = mBubbleView.y
            if (x >= bubbleX && x <= bubbleX + mBubbleView.width &&
                y >= bubbleY && y <= bubbleY + mBubbleView.height
            ) {
                return true
            }
        }
        val expand = mTouchExpandPx
        val handleLeft = mScrollbar.x + mHandleView.x
        val handleTop = mScrollbar.y + mHandleView.y
        return x >= handleLeft - expand &&
                x <= handleLeft + mHandleView.width + expand &&
                y >= handleTop - expand &&
                y <= handleTop + mHandleHeight + expand
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mViewHeight = h
    }

    private fun startGestureTracking() {
        mGestureDistancePx = 0f
        mVelocityPxPerSecond = 0f
        mLastScrollTimeNanos = 0L
        mBubbleShownInGesture = false
    }

    /**
     * 按「累积距离 + 瞬时速度」决定要不要弹气泡，只在拖动阶段判，
     * fling 阶段手指不在屏上，弹出来也抓不到，留给 [settleGesture] 处理。
     */
    private fun trackGesture(recyclerView: RecyclerView, dy: Int) {
        if (!mShowBubble || mBubbleShownInGesture) {
            return
        }
        val viewport = mViewHeight
        if (viewport <= 0) {
            return
        }
        val now = System.nanoTime()
        val deltaSeconds = if (mLastScrollTimeNanos == 0L) {
            0f
        } else {
            (now - mLastScrollTimeNanos) / 1_000_000_000f
        }
        mLastScrollTimeNanos = now
        mGestureDistancePx += abs(dy)
        if (deltaSeconds in 0.008f..0.1f) {
            val instant = abs(dy) / deltaSeconds
            mVelocityPxPerSecond = if (mVelocityPxPerSecond <= 0f) {
                instant
            } else {
                mVelocityPxPerSecond * 0.7f + instant * 0.3f
            }
        }
        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_DRAGGING) {
            return
        }
        if (!hasEnoughContentForBubble(recyclerView)) {
            return
        }
        val density = resources.displayMetrics.density
        val distanceTriggerPx = FastScrollerBubblePolicy.dragTriggerPx(viewport, density)
        val flickTriggerPx = FastScrollerBubblePolicy.minFlickDistancePx(density)
        val fastFlick = mVelocityPxPerSecond >=
                viewport * FastScrollerBubblePolicy.VELOCITY_TRIGGER_SCREENS_PER_SEC
        if (mGestureDistancePx >= distanceTriggerPx ||
            (fastFlick && mGestureDistancePx >= flickTriggerPx)
        ) {
            mBubbleShownInGesture = true
            handler.removeCallbacks(mBubbleHider)
            showBubble()
        }
    }

    /**
     * 手指离开后：整段手势（拖动 + fling）滑过一屏才补弹气泡，
     * 弹出后保留 [FastScrollerBubblePolicy.SETTLE_HOLD_MS] 再淡出，方便顺手去抓。
     */
    private fun settleGesture() {
        if (!mShowBubble) {
            return
        }
        val viewport = mViewHeight
        if (!mBubbleShownInGesture && viewport > 0 &&
            mGestureDistancePx >= viewport * FastScrollerBubblePolicy.SETTLE_TRIGGER_SCREENS &&
            hasEnoughContentForBubble(mRecyclerView)
        ) {
            mBubbleShownInGesture = true
            showBubble()
        }
        if (isViewVisible(mBubbleView)) {
            handler.removeCallbacks(mBubbleHider)
            handler.postDelayed(mBubbleHider, FastScrollerBubblePolicy.SETTLE_HOLD_MS)
        }
    }

    /**
     * 短列表用不上定位气泡：至少要有 3 屏内容。
     */
    private fun hasEnoughContentForBubble(recyclerView: RecyclerView?): Boolean {
        recyclerView ?: return false
        val visibleCount = recyclerView.childCount
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (visibleCount > 0 && itemCount > 0) {
            return FastScrollerBubblePolicy.hasEnoughContentByCount(visibleCount, itemCount)
        }
        val viewport = recyclerView.height
        return FastScrollerBubblePolicy.hasEnoughContent(
            recyclerView.computeVerticalScrollRange() - viewport,
            viewport
        )
    }

    private fun setRecyclerViewPosition(y: Float) {
        mRecyclerView?.adapter?.let { adapter ->
            val itemCount = adapter.itemCount
            val proportion: Float = when {
                mHandleView.y == 0f -> 0f
                mHandleView.y + mHandleHeight >= mViewHeight - sTrackSnapRange -> 1f
                else -> y / mViewHeight.toFloat()
            }
            var scrolledItemCount = (proportion * itemCount).roundToInt()
            if (isLayoutReversed(mRecyclerView?.layoutManager)) {
                scrolledItemCount = itemCount - scrolledItemCount
            }
            val targetPos = getValueInRange(0, itemCount - 1, scrolledItemCount)
            mRecyclerView?.layoutManager?.scrollToPosition(targetPos)
        }
    }

    private fun getScrollProportion(recyclerView: RecyclerView?): Float {
        recyclerView ?: return 0f
        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
        val verticalScrollRange = recyclerView.computeVerticalScrollRange()
        val rangeDiff = (verticalScrollRange - mViewHeight).toFloat()
        val proportion = verticalScrollOffset.toFloat() / if (rangeDiff > 0) rangeDiff else 1f
        return mViewHeight * proportion
    }

    private fun getValueInRange(min: Int, max: Int, value: Int): Int {
        val minimum = max(min, value)
        return min(minimum, max)
    }

    private fun setViewPositions(y: Float) {
        mBubbleHeight = mBubbleView.height
        mHandleHeight = mHandleView.height
        val handleY =
            getValueInRange(0, mViewHeight - mHandleHeight, (y - mHandleHeight / 2).toInt())
        if (mShowBubble) {
            val bubbleY = getValueInRange(
                0,
                (mViewHeight - mBubbleHeight).coerceAtLeast(0),
                (y - mBubbleHeight / 2).toInt()
            )
            mBubbleView.y = bubbleY.toFloat()
        }
        mHandleView.y = handleY.toFloat()
    }

    private fun updateViewHeights() {
        val measureSpec =
            MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        mBubbleView.measure(measureSpec, measureSpec)
        mBubbleHeight = mBubbleView.measuredHeight
        mHandleView.measure(measureSpec, measureSpec)
        mHandleHeight = mHandleView.measuredHeight
    }

    private fun isLayoutReversed(layoutManager: RecyclerView.LayoutManager?): Boolean {
        if (layoutManager is LinearLayoutManager) {
            return layoutManager.reverseLayout
        } else if (layoutManager is StaggeredGridLayoutManager) {
            return layoutManager.reverseLayout
        }
        return false
    }

    private fun isViewVisible(view: View?): Boolean {
        return view != null && view.visibility == View.VISIBLE
    }

    private fun cancelAnimation(animator: ViewPropertyAnimator?) {
        animator?.cancel()
    }

    /** 气泡从滚动条里「挤出来」的初始横向偏移。 */
    private fun bubblePopOffset(): Float = (mBubbleHeight * 0.9f).coerceAtLeast(1f)

    private fun showBubble() {
        if (!mShowBubble) {
            return
        }
        applyBubbleMetrics()
        // 取消还没跑完的淡出，避免它的 onAnimationCancel 把气泡改成 GONE
        mBubbleAnimator?.setListener(null)
        mBubbleAnimator?.cancel()
        mBubbleAnimator = null
        if (!isViewVisible(mBubbleView)) {
            mBubbleView.visibility = View.VISIBLE
            mBubbleView.alpha = 0f
            // 从滚动条那一侧滑出来
            mBubbleView.translationX = bubblePopOffset()
            val bubbleY = (mHandleView.y + mHandleHeight / 2f - mBubbleHeight / 2f)
                .coerceIn(0f, (mViewHeight - mBubbleHeight).coerceAtLeast(0).toFloat())
            mBubbleView.y = bubbleY
            mBubbleAnimator = mBubbleView.animate().alpha(1f).translationX(0f)
                .setDuration(sBubblePopDuration.toLong())
                .setInterpolator(OvershootInterpolator(1.3f))
                .setListener(object : AnimatorListenerAdapter() {

                    // adapter required for new alpha value to stick
                })
            repositionAfterLayout()
        } else if (mBubbleView.alpha < 1f || mBubbleView.translationX != 0f) {
            mBubbleAnimator?.setListener(null)
            mBubbleAnimator?.cancel()
            mBubbleAnimator = mBubbleView.animate().alpha(1f).translationX(0f)
                .setDuration(sBubbleAnimDuration.toLong())
        }
    }

    /**
     * 气泡显示/隐藏会触发重新布局，FrameLayout 会把 handle 的 y 重置为 0，
     * 这里在下一帧按滚动比例重新摆一次，避免滑块跳到顶部。
     */
    private fun repositionAfterLayout() {
        post {
            if (mHandleView.isSelected) {
                return@post
            }
            mBubbleHeight = mBubbleView.height
            mHandleHeight = mHandleView.height
            setViewPositions(getScrollProportion(mRecyclerView))
        }
    }

    private fun hideBubble() {
        if (isViewVisible(mBubbleView)) {
            mBubbleAnimator?.setListener(null)
            mBubbleAnimator?.cancel()
            mBubbleAnimator = mBubbleView.animate().alpha(0f)
                .translationX(bubblePopOffset())
                .setDuration(sBubbleHideDuration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        mBubbleView.visibility = View.GONE
                        mBubbleAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        mBubbleView.visibility = View.GONE
                        mBubbleAnimator = null
                    }
                })
        }
    }

    private fun showScrollbar() {
        mRecyclerView?.let { mRecyclerView ->
            if (mRecyclerView.computeVerticalScrollRange() - mViewHeight > 0) {
                applyBubbleMetrics()
                val transX =
                    resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
                        .toFloat()
                mScrollbar.translationX = transX
                mScrollbar.visibility = View.VISIBLE
                mScrollbarAnimator = mScrollbar.animate().translationX(0f).alpha(1f)
                    .setDuration(sScrollbarAnimDuration.toLong())
                    .setListener(object : AnimatorListenerAdapter() {

                        // adapter required for new alpha value to stick
                    })
            }
        }
    }

    private fun hideScrollbar() {
        val transX =
            resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end).toFloat()
        mScrollbarAnimator = mScrollbar.animate().translationX(transX).alpha(0f)
            .setDuration(sScrollbarAnimDuration.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    mScrollbar.visibility = View.INVISIBLE
                    mScrollbarAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    super.onAnimationCancel(animation)
                    mScrollbar.visibility = View.INVISIBLE
                    mScrollbarAnimator = null
                }
            })
    }

    private fun setHandleSelected(selected: Boolean) {
        mHandleView.isSelected = selected
        DrawableCompat.setTint(mHandleImage!!, if (selected) mBubbleColor else mHandleColor)
    }

    private fun layout(context: Context, attrs: AttributeSet?) {
        View.inflate(context, R.layout.view_fastscroller, this)
        clipChildren = false
        orientation = HORIZONTAL
        mBubbleView = findViewById(R.id.fastscroll_bubble)
        mHandleView = findViewById(R.id.fastscroll_handle)
        mTrackView = findViewById(R.id.fastscroll_track)
        mScrollbar = findViewById(R.id.fastscroll_scrollbar)
        @ColorInt var bubbleColor = ColorUtils.adjustAlpha(context.accentColor, 0.8f)
        @ColorInt var handleColor = context.accentColor
        @ColorInt var trackColor = context.getCompatColor(R.color.transparent30)
        var fadeScrollbar = true
        var showBubble = true
        var showTrack = true
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FastScroller, 0, 0)
            try {
                bubbleColor = typedArray.getColor(R.styleable.FastScroller_bubbleColor, bubbleColor)
                handleColor = typedArray.getColor(R.styleable.FastScroller_handleColor, handleColor)
                trackColor = typedArray.getColor(R.styleable.FastScroller_trackColor, trackColor)
                fadeScrollbar =
                    typedArray.getBoolean(R.styleable.FastScroller_fadeScrollbar, fadeScrollbar)
                showBubble = typedArray.getBoolean(R.styleable.FastScroller_showBubble, showBubble)
                showTrack = typedArray.getBoolean(R.styleable.FastScroller_showTrack, showTrack)
            } finally {
                typedArray.recycle()
            }
        }
        setTrackColor(trackColor)
        setHandleColor(handleColor)
        setBubbleColor(bubbleColor)
        setFadeScrollbar(fadeScrollbar)
        setBubbleVisible(showBubble)
        setTrackVisible(showTrack)
        applyBubbleMetrics()
    }

    /**
     * 气泡手柄大小，跟随「我的-其他设置-快速滚动条气泡大小」的 5 档预设。
     */
    private fun applyBubbleMetrics() {
        val bubbleHeight = (AppConfig.fastScrollerBubbleSizeDp * resources.displayMetrics.density)
            .roundToInt()
        val bubbleWidth = (bubbleHeight * 1.5f).roundToInt()
        val layoutParams = mBubbleView.layoutParams
        if (layoutParams == null) {
            mBubbleView.layoutParams = LayoutParams(bubbleWidth, bubbleHeight).apply {
                gravity = Gravity.END
            }
        } else if (layoutParams.width != bubbleWidth || layoutParams.height != bubbleHeight) {
            layoutParams.width = bubbleWidth
            layoutParams.height = bubbleHeight
            mBubbleView.layoutParams = layoutParams
        }
        mBubbleHeight = bubbleHeight
        val stripWidth = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_start) +
                resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end) +
                resources.getDimensionPixelSize(R.dimen.fastscroll_handle_width)
        mScrollbar.minimumWidth = stripWidth
    }

    interface SectionIndexer {
        fun getSectionText(position: Int): String
    }

    companion object {
        private const val sBubbleAnimDuration = 120
        private const val sBubblePopDuration = 220
        private const val sBubbleHideDuration = 140
        private const val sScrollbarAnimDuration = 300
        private const val sScrollbarHideDelay = 1000
        private const val sTrackSnapRange = 5
    }

}
