package io.legado.app.ui.widget.text

import android.os.Build
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Magnifier
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 给输入框加上和正文（ReadView）一样样式的选区放大镜：
 * 大圆角气泡、160% 放大。
 *
 * 系统的 EditText 放大镜样式没法直接改，所以这里接管左右选区手柄的拖动：
 * 按住手柄时自己更新选区并显示这个放大镜，系统自带的那个就不会出现。
 */
class SelectionMagnifierTouchHandler(private val editText: EditText) {

    private var magnifier: Magnifier? = null
    private var dragging = false
    private var anchorOffset = -1
    private val density = editText.resources.displayMetrics.density
    private val hotspotHorizontal = 22f * density
    private val hotspotAbove = 8f * density
    private val hotspotBelow = 42f * density

    /**
     * @return true 表示这串触摸事件已经被这里处理，调用方不要交给 super。
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!editText.isEnabled || editText.layout == null) return false
                val isStartHandle = findSelectionHandleAt(event.x, event.y) ?: return false
                dragging = true
                anchorOffset = if (isStartHandle) {
                    editText.selectionEnd
                } else {
                    editText.selectionStart
                }
                if (anchorOffset < 0) {
                    dragging = false
                    return false
                }
                showMagnifier(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                dragSelection(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                anchorOffset = -1
                dismissMagnifier()
                editText.invalidate()
                return true
            }
        }
        return false
    }

    fun dismissMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        magnifier?.dismiss()
    }

    /**
     * @return true 表示按在了起点手柄上，false 表示终点手柄，null 表示没按在手柄上。
     */
    private fun findSelectionHandleAt(x: Float, y: Float): Boolean? {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start < 0 || end < 0 || start == end) return null
        val startPoint = handlePoint(start) ?: return null
        val endPoint = handlePoint(end) ?: return null
        val nearStart = nearHandle(startPoint, x, y)
        val nearEnd = nearHandle(endPoint, x, y)
        return when {
            nearStart && nearEnd -> abs(x - startPoint[0]) <= abs(x - endPoint[0])
            nearStart -> true
            nearEnd -> false
            else -> null
        }
    }

    private fun nearHandle(point: FloatArray, x: Float, y: Float): Boolean {
        return abs(x - point[0]) <= hotspotHorizontal &&
            y >= point[1] - hotspotAbove &&
            y <= point[1] + hotspotBelow
    }

    private fun handlePoint(offset: Int): FloatArray? {
        val layout = editText.layout ?: return null
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) +
            editText.totalPaddingLeft - editText.scrollX
        val y = (layout.getLineBottom(line) +
            editText.totalPaddingTop - editText.scrollY).toFloat()
        return floatArrayOf(x, y)
    }

    private fun dragSelection(x: Float, y: Float) {
        val text = editText.text ?: return
        if (anchorOffset < 0) return
        scrollIfNeeded(x, y)
        val offset = editText.getOffsetForPosition(x, y).coerceIn(0, text.length)
        val newStart = min(anchorOffset, offset)
        val newEnd = max(anchorOffset, offset)
        if (newStart != editText.selectionStart || newEnd != editText.selectionEnd) {
            editText.setSelection(newStart, newEnd)
        }
        showMagnifier(x, y)
    }

    private fun scrollIfNeeded(x: Float, y: Float) {
        val top = editText.totalPaddingTop
        val bottom = editText.height - editText.totalPaddingBottom
        val delta = when {
            y > bottom -> (y - bottom).toInt().coerceAtLeast(1)
            y < top -> (y - top).toInt().coerceAtMost(-1)
            else -> 0
        }
        if (delta != 0) {
            editText.scrollBy(0, delta)
        }
    }

    private fun showMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val safeX = x.coerceIn(0f, editText.width.toFloat())
        val safeY = y.coerceIn(0f, editText.height.toFloat())
        val current = magnifier ?: createMagnifier().also { magnifier = it }
        current.show(safeX, safeY)
    }

    private fun createMagnifier(): Magnifier {
        return Magnifier.Builder(editText).apply {
            setSize((168 * density).toInt(), (64 * density).toInt())
            setCornerRadius(20 * density)
            setElevation(8 * density)
            setInitialZoom(1.6f)
        }.build()
    }
}
