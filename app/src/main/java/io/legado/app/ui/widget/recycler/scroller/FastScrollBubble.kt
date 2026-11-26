package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import io.legado.app.utils.ColorUtils

/**
 * 快速滚动条的气泡手柄。
 *
 * 胶囊形底色 + 三条抓握横线，大小由「我的-其他设置-快速滚动条气泡大小」控制。
 */
class FastScrollBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var bubbleColor: Int = Color.GRAY
    private var gripColor: Int = Color.WHITE

    fun setBubbleColor(color: Int) {
        bubbleColor = color
        gripColor = if (ColorUtils.isColorLight(color)) Color.BLACK else Color.WHITE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) {
            return
        }
        paint.color = bubbleColor
        val radius = height / 2f
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, paint)

        paint.color = gripColor
        val barWidth = width * 0.42f
        val barHeight = (height * 0.06f).coerceAtLeast(1f)
        val barGap = height * 0.17f
        val centerX = width / 2f
        val centerY = height / 2f
        for (index in -1..1) {
            val barCenterY = centerY + index * barGap
            canvas.drawRoundRect(
                centerX - barWidth / 2f,
                barCenterY - barHeight / 2f,
                centerX + barWidth / 2f,
                barCenterY + barHeight / 2f,
                barHeight / 2f,
                barHeight / 2f,
                paint
            )
        }
    }
}
