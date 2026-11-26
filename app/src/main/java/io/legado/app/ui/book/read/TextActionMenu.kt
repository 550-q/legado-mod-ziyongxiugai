package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.text.TextPaint
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.ui.widget.compose.installViewTreeOwnersFrom
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlin.math.ceil

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val composeView = ComposeView(context)
    private val allMenuItems: List<MenuItemImpl>
    private var processTextMenuItems: List<MenuItemImpl> = emptyList()
    private var menuPages by mutableStateOf<List<MenuPage>>(emptyList())
    private var popupWidthPx by mutableIntStateOf(1)
    private var popupHeightPx by mutableIntStateOf(1)
    private var rowCount by mutableIntStateOf(1)
    private var lastMaxMainWidth = 0
    private var overlayParent: ViewGroup? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val itemTextPaint = TextPaint().apply {
        textSize = 12f * context.resources.displayMetrics.scaledDensity
        typeface = context.uiTypeface()
    }

    private val configuredActionIds: Set<String>
        get() = ContentSelectConfig.selectedActionIds(context)

    private val defaultOpenActionId: String
        get() = context.getPrefString(PreferKey.contentSelectDefaultOpen, "").orEmpty()

    init {
        contentView = composeView
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = 0f
        }

        val myMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        allMenuItems = myMenu.visibleItems

        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setContent {
            LegadoComposeTheme {
                TextActionMenuContent(
                    pages = menuPages,
                    popupWidthPx = popupWidthPx,
                    popupHeightPx = popupHeightPx,
                    rowCount = rowCount,
                    onActionClick = { action ->
                        runMenuAction { handleActionClick(action) }
                    },
                    onActionLongClick = { action ->
                        when (action.itemId) {
                            R.id.menu_aloud -> {
                                toggleSpeakMode()
                                refreshMenuTitles()
                            }
                            R.id.menu_web_search -> runMenuAction {
                                if (!callBack.onMenuItemSelected(R.id.menu_web_search)) {
                                    onMenuItemSelected(action)
                                }
                            }
                        }
                    }
                )
            }
        }
        upMenu()
    }

    private fun menuItemToActionId(itemId: Int): String? = when (itemId) {
        R.id.menu_web_search -> ContentSelectConfig.ACTION_WEB_SEARCH
        R.id.menu_replace -> ContentSelectConfig.ACTION_REPLACE
        R.id.menu_copy -> ContentSelectConfig.ACTION_COPY
        R.id.menu_bookmark -> ContentSelectConfig.ACTION_BOOKMARK
        R.id.menu_aloud -> ContentSelectConfig.ACTION_ALOUD
        R.id.menu_dict -> ContentSelectConfig.ACTION_DICT
        R.id.menu_ask_ai -> ContentSelectConfig.ACTION_ASK_AI
        R.id.menu_generate_image -> ContentSelectConfig.ACTION_GENERATE_IMAGE
        R.id.menu_share_image -> ContentSelectConfig.ACTION_SHARE_IMAGE
        else -> null
    }

    private fun filteredMenuActions(): List<TextMenuAction> {
        val actions = (allMenuItems + processTextMenuItems).mapNotNull { item ->
            val processTextActionId = ContentSelectConfig.processTextActionId(item.intent)
            if (processTextActionId != null) {
                if (!configuredActionIds.contains(processTextActionId)) return@mapNotNull null
                return@mapNotNull TextMenuAction(
                    itemId = item.itemId,
                    actionId = processTextActionId,
                    title = item.title?.toString().orEmpty(),
                    intent = item.intent
                )
            }
            val actionId = menuItemToActionId(item.itemId) ?: return@mapNotNull null
            if (!configuredActionIds.contains(actionId)) return@mapNotNull null
            if (actionId == ContentSelectConfig.ACTION_REPLACE && !callBack.supportsReplaceRules()) {
                return@mapNotNull null
            }
            TextMenuAction(
                itemId = item.itemId,
                actionId = actionId,
                title = if (item.itemId == R.id.menu_aloud) {
                    if (BaseReadAloudService.isRun || AppConfig.contentSelectSpeakMod == 1) {
                        context.getString(R.string.read_aloud_from_here)
                    } else {
                        context.getString(R.string.read_aloud_selected)
                    }
                } else {
                    item.title?.toString().orEmpty()
                },
                intent = item.intent
            )
        }
        // 用户可在“正文长按条编辑”里长按勾选框拖动排序，这里按保存的顺序显示。
        val order = ContentSelectConfig.actionOrder(context)
        if (order.isEmpty()) return actions
        val index = order.withIndex().associate { it.value to it.index }
        return actions.sortedBy { index[it.actionId] ?: Int.MAX_VALUE }
    }

    /**
     * 「搜索」短按 = 当前书籍全文搜索（和阅读菜单里的搜索同一入口），
     * 网页搜索改为长按触发。
     */
    private fun handleActionClick(action: TextMenuAction) {
        val itemId = if (action.itemId == R.id.menu_web_search &&
            callBack.supportsContentSearch()
        ) {
            R.id.menu_search_content
        } else {
            action.itemId
        }
        if (!callBack.onMenuItemSelected(itemId)) {
            onMenuItemSelected(action)
        }
    }

    private fun runMenuAction(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            // Multi-page selections can exceed an external app's Binder or
            // clipboard limit. The reader must remain usable if it refuses.
            AppLog.putDebug("Reader selection action failed", error)
            context.toastOnUi("文字操作失败，请缩小选区后重试")
        } finally {
            // 长按回调还在 Compose 手势分发过程中，此时同步 removeView 会让
            // 手势协程被再次 resume，抛 "Already resumed" 直接崩溃。
            // 延后到下一轮主线程消息再收起菜单。
            mainHandler.post {
                if (overlayParent != null || isShowing) {
                    callBack.onMenuActionFinally()
                }
            }
        }
    }

    fun upMenu() {
        refreshProcessTextMenuItems()
        upMenuForWidth(lastMaxMainWidth.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels)
    }

    /**
     * 重新枚举系统「文字处理」动作，这样新装的翻译 / 记事本等应用
     * 在保存长按条配置后无需重启阅读页即可出现。
     */
    private fun refreshProcessTextMenuItems() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            processTextMenuItems = emptyList()
            return
        }
        val otherMenu = MenuBuilder(context)
        onInitializeMenu(otherMenu)
        processTextMenuItems = otherMenu.visibleItems
    }

    /**
     * 长按「朗读」切换模式后只刷新标题，不动菜单尺寸和位置。
     */
    private fun refreshMenuTitles() {
        val titles = filteredMenuActions().associateBy { it.actionId }
        menuPages = menuPages.map { page ->
            MenuPage(
                page.rows.map { row ->
                    MenuRow(
                        row.cells.map { cell ->
                            cell.copy(action = titles[cell.action.actionId] ?: cell.action)
                        }
                    )
                }
            )
        }
    }

    private fun upMenuForWidth(maxMainWidth: Int) {
        lastMaxMainWidth = maxMainWidth
        val actions = filteredMenuActions()
        val maxWidth = (maxMainWidth - 16.dpToPx()).coerceAtLeast(140.dpToPx())
        val layout = calculateLayout(actions, maxWidth)
        rowCount = layout.rows
        popupWidthPx = layout.width
        popupHeightPx = layout.height
        width = layout.width
        height = layout.height
        composeView.layoutParams = ViewGroup.LayoutParams(layout.width, layout.height)
        menuPages = layout.pages
    }

    private fun calculateLayout(actions: List<TextMenuAction>, maxWidth: Int): MenuLayout {
        val hPadding = horizontalPaddingPx()
        val rowAreaLimit = (maxWidth - hPadding).coerceAtLeast(minItemWidthPx())
        // 每格宽度跟随标题长度，长名字（如“问问 DeepSeek”）不再被压成“问…”。
        val chipWidths = actions.associateWith {
            estimateItemWidth(it).coerceIn(minItemWidthPx(), rowAreaLimit)
        }
        val widestItem = chipWidths.values.maxOrNull() ?: minItemWidthPx()
        // 主框大小沿用上一版（按最长标题 × 列数），只是格子不再统一宽度。
        val maxColumns = when {
            actions.size <= 2 -> actions.size.coerceAtLeast(1)
            actions.size <= 4 -> actions.size
            else -> 4
        }
        val fitColumns = (rowAreaLimit / widestItem).coerceAtLeast(1)
        val columns = maxColumns.coerceAtMost(fitColumns)
        val menuWidth = (widestItem * columns + hPadding)
            .coerceAtMost(maxWidth)
            .coerceAtLeast(minItemWidthPx() + hPadding)
        val pages = buildPages(actions, chipWidths, menuWidth - hPadding)
        val rows = pages.maxOfOrNull { it.rows.size }?.coerceIn(1, 2) ?: 1
        val menuHeight = rows * rowHeightPx() + verticalPaddingPx() * 2 +
            if (pages.size > 1) indicatorHeightPx() + indicatorSpacingPx() else 0
        return MenuLayout(
            rows = rows,
            width = menuWidth,
            height = menuHeight,
            pages = pages
        )
    }

    /**
     * 把各格按实际宽度排进最多两行一页，宽度不够就换行、换页。
     */
    private fun buildPages(
        actions: List<TextMenuAction>,
        chipWidths: Map<TextMenuAction, Int>,
        rowAreaWidth: Int
    ): List<MenuPage> {
        val gap = chipGapPx()
        val pages = mutableListOf<MenuPage>()
        var rows = mutableListOf<MenuRow>()
        var cells = mutableListOf<MenuCell>()
        var used = 0
        fun flushRow() {
            if (cells.isNotEmpty()) {
                rows.add(MenuRow(cells))
                cells = mutableListOf()
                used = 0
            }
        }
        fun flushPage() {
            flushRow()
            if (rows.isNotEmpty()) {
                pages.add(MenuPage(rows))
                rows = mutableListOf()
            }
        }
        for (action in actions) {
            val width = chipWidths[action] ?: minItemWidthPx()
            if (rows.size >= 2) flushPage()
            if (cells.isNotEmpty() && used + gap + width > rowAreaWidth) {
                flushRow()
            }
            if (rows.size >= 2) flushPage()
            used += if (cells.isEmpty()) width else width + gap
            cells.add(MenuCell(action, width))
        }
        flushPage()
        if (pages.isEmpty()) {
            pages.add(MenuPage(emptyList()))
        }
        return pages
    }

    private fun estimateItemWidth(action: TextMenuAction): Int {
        val textWidth = ceil(itemTextPaint.measureText(action.title)).toInt()
        return textWidth + 30.dpToPx()
    }

    private fun minItemWidthPx(): Int = 64.dpToPx()

    private fun itemHeightPx(): Int = 36.dpToPx()

    private fun rowHeightPx(): Int = itemHeightPx() + chipGapPx()

    private fun chipGapPx(): Int = 6.dpToPx()

    private fun horizontalPaddingPx(): Int = 12.dpToPx()

    private fun verticalPaddingPx(): Int = 8.dpToPx()

    private fun indicatorHeightPx(): Int = 6.dpToPx()

    private fun indicatorSpacingPx(): Int = 4.dpToPx()

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int,
        reservedBottom: Int = 0
    ) {
        val defaultActionId = defaultOpenActionId
        if (defaultActionId.isNotEmpty() && configuredActionIds.contains(defaultActionId)) {
            val defaultItem = filteredMenuActions().firstOrNull { it.actionId == defaultActionId }
            if (defaultItem != null) {
                runMenuAction { handleActionClick(defaultItem) }
                return
            }
        }
        upMenuForWidth(view.width)
        val popupWidth = popupWidthPx
        val popupHeight = popupHeightPx
        val margin = 8.dpToPx()
        val safeWindowHeight = (windowHeight - reservedBottom).coerceAtLeast(margin * 2)
        val spaceAbove = startTopY
        val spaceBelow = safeWindowHeight - endBottomY
        val showAbove = spaceAbove >= popupHeight + margin || spaceAbove > spaceBelow
        val preferredX = ((startX + endX) / 2f - popupWidth / 2f).toInt()
        val maxX = (view.width - popupWidth - margin).coerceAtLeast(margin)
        val x = preferredX.coerceIn(margin, maxX)
        val y = if (showAbove) {
            (startTopY - popupHeight - margin).coerceAtLeast(margin)
        } else {
            (endBottomY + margin).coerceAtMost((safeWindowHeight - popupHeight - margin).coerceAtLeast(margin))
        }
        showInHostView(view, x, y)
    }

    private fun showInHostView(anchor: View, x: Int, y: Int) {
        dismiss()
        composeView.installViewTreeOwnersFrom(anchor, context)
        val parent = anchor.rootView as? ViewGroup
        if (parent == null) {
            showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
            return
        }
        (composeView.parent as? ViewGroup)?.removeView(composeView)
        parent.addView(
            composeView,
            FrameLayout.LayoutParams(popupWidthPx, popupHeightPx).apply {
                leftMargin = x
                topMargin = y
            }
        )
        overlayParent = parent
    }

    override fun dismiss() {
        overlayParent?.let {
            (composeView.parent as? ViewGroup)?.removeView(composeView)
            overlayParent = null
            return
        }
        super.dismiss()
    }

    private fun toggleSpeakMode() {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            context.toastOnUi(R.string.content_select_speak_from_selection)
        } else {
            AppConfig.contentSelectSpeakMod = 0
            context.toastOnUi(R.string.content_select_speak_selected)
        }
    }

    private fun onMenuItemSelected(action: TextMenuAction) {
        when (action.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> action.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("Text action menu error\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
            }
        }.onFailure {
            context.toastOnUi("Text action menu init error:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun supportsReplaceRules(): Boolean = true

        /**
         * 当前书籍是否支持全文搜索（EPUB 直读模式不支持，此时「搜索」仍是网页搜索）
         */
        fun supportsContentSearch(): Boolean = true

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }

    private data class MenuLayout(
        val rows: Int,
        val width: Int,
        val height: Int,
        val pages: List<MenuPage>
    )
}

private data class TextMenuAction(
    val itemId: Int,
    val actionId: String,
    val title: String,
    val intent: Intent?
)

private data class MenuCell(
    val action: TextMenuAction,
    val width: Int
)

private data class MenuRow(
    val cells: List<MenuCell>
)

private data class MenuPage(
    val rows: List<MenuRow>
)

@Composable
private fun TextActionMenuContent(
    pages: List<MenuPage>,
    popupWidthPx: Int,
    popupHeightPx: Int,
    rowCount: Int,
    onActionClick: (TextMenuAction) -> Unit,
    onActionLongClick: (TextMenuAction) -> Unit
) {
    val style = rememberAppDialogStyle()
    val density = LocalDensity.current
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(pageCount) {
        val target = pagerState.currentPage.coerceAtMost(pageCount - 1)
        if (target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }
    val widthDp = with(density) { popupWidthPx.toDp() }
    val heightDp = with(density) { popupHeightPx.toDp() }
    val itemHeight = 36.dp
    val pageHeight = with(density) { (rowCount.coerceIn(1, 2) * 42.dpToPx()).toDp() }
    val shape = RoundedCornerShape(style.panelRadius)

    Column(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(style.surface)
            .border(1.dp, style.stroke, shape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .width(widthDp - 12.dp)
                .height(pageHeight)
        ) { pageIndex ->
            val page = pages.getOrNull(pageIndex) ?: MenuPage(emptyList())
            Column(
                modifier = Modifier.width(widthDp - 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                page.rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.cells.forEach { cell ->
                            TextActionChip(
                                action = cell.action,
                                width = with(density) { cell.width.toDp() },
                                height = itemHeight,
                                textColor = style.primaryText,
                                background = style.fieldSurface,
                                accent = style.accent,
                                onClick = { onActionClick(cell.action) },
                                onLongClick = { onActionLongClick(cell.action) }
                            )
                        }
                    }
                }
            }
        }
        if (pageCount > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (selected) style.accent else style.secondaryText.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextActionChip(
    action: TextMenuAction,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    textColor: ComposeColor,
    background: ComposeColor,
    accent: ComposeColor,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = action.title,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(max = width - 22.dp)
        )
    }
}
