package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.help.config.BookInfoCatalogOpenMode
import io.legado.app.help.config.BookInfoComponentItem
import io.legado.app.help.config.BookInfoComponentType
import io.legado.app.help.config.BookInfoPageStyle
import io.legado.app.ui.widget.compose.AppManagementCard
import io.legado.app.ui.widget.compose.AppListSpacing
import io.legado.app.ui.widget.compose.AppManagementPalette
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.LegadoMiuixSwitch
import io.legado.app.ui.widget.compose.rememberAppManagementPalette

@Composable
internal fun BookInfoManageScreen(
    style: BookInfoPageStyle,
    components: List<BookInfoComponentItem>,
    catalogOpenMode: BookInfoCatalogOpenMode,
    onStyleChanged: (BookInfoPageStyle) -> Unit,
    onComponentToggle: (Int, Boolean) -> Unit,
    onCatalogOpenModeChanged: (BookInfoCatalogOpenMode) -> Unit,
    onReset: () -> Unit,
    onMoveItem: (BookInfoComponentType, Int) -> Unit
) {
    val palette = rememberAppManagementPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.settings.page)
    ) {
        // Tab bar
        StyleTabBar(
            style = style,
            palette = palette,
            onStyleChanged = onStyleChanged
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Summary text
        val summaryText = when (style) {
            BookInfoPageStyle.CLASSIC -> stringResource(R.string.book_info_components_hint)
            BookInfoPageStyle.IMMERSIVE_COMPOSE -> stringResource(R.string.book_info_style_immersive_hint)
        }
        Text(
            text = summaryText,
            color = palette.settings.secondaryText,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (style) {
            BookInfoPageStyle.CLASSIC -> {
                val listState = rememberLazyListState()
                val latestComponents = rememberUpdatedState(components)
                val latestOnMoveItem = rememberUpdatedState(onMoveItem)
                var draggingType by remember { mutableStateOf<BookInfoComponentType?>(null) }
                // 拖动起点时被拖行的位置与其余行的中心点（固定在拖动开始时，避免高度差异导致来回跳）
                var dragAccum by remember { mutableFloatStateOf(0f) }
                var dragBaseTop by remember { mutableFloatStateOf(0f) }
                var dragBaseSize by remember { mutableFloatStateOf(0f) }
                var dragOthersAbove by remember { mutableStateOf<List<Float>>(emptyList()) }
                var dragItemsBefore by remember { mutableIntStateOf(0) }

                val startDrag: (BookInfoComponentType) -> Unit = { type ->
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val dragged = visible.firstOrNull { it.key == type.name }
                    if (dragged == null) {
                        draggingType = null
                    } else {
                        draggingType = type
                        dragAccum = 0f
                        dragBaseTop = dragged.offset.toFloat()
                        dragBaseSize = dragged.size.toFloat()
                        dragOthersAbove = visible
                            .filter { it.key != type.name }
                            .map { it.offset + it.size / 2f }
                        dragItemsBefore = visible.firstOrNull()?.index ?: 0
                    }
                }
                val dragBy: (BookInfoComponentType, Float) -> Unit = drag@{ type, delta ->
                    if (draggingType != type) return@drag
                    val current = latestComponents.value
                    val total = current.size
                    if (total <= 1) return@drag
                    dragAccum += delta
                    // 以被拖行"视觉中心"越过其它行原始中心的个数作为目标位置，
                    // 行高不同（目录行更高）时也单调稳定，不会反复回弹
                    val center = dragBaseTop + dragAccum + dragBaseSize / 2f
                    val above = dragItemsBefore + dragOthersAbove.count { it < center }
                    val target = above.coerceIn(0, total - 1)
                    val from = current.indexOfFirst { it.type == type }
                    if (from >= 0 && target != from) {
                        latestOnMoveItem.value(type, target)
                    }
                }
                val endDrag: () -> Unit = {
                    draggingType = null
                    dragAccum = 0f
                }

                // Component list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(AppListSpacing.Normal)
                ) {
                    itemsIndexed(
                        items = components,
                        key = { _, item -> item.type.name }
                    ) { index, item ->
                        val isDragging = draggingType == item.type
                        ComponentItemRow(
                            item = item,
                            palette = palette,
                            catalogOpenMode = catalogOpenMode,
                            onCatalogOpenModeChanged = onCatalogOpenModeChanged,
                            onCheckedChange = { checked ->
                                onComponentToggle(index, checked)
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        // 用当前布局位置反推位移，行高不同也能跟手，不会因换位而漂移
                                        val info = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == item.type.name }
                                        if (info != null) {
                                            translationY =
                                                (dragBaseTop + dragAccum) - info.offset
                                        }
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                },
                            rowDragModifier = Modifier.pointerInput(item.type.name) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(item.type) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragBy(item.type, dragAmount.y)
                                    },
                                    onDragEnd = endDrag,
                                    onDragCancel = endDrag
                                )
                            },
                            dragHandleModifier = Modifier.pointerInput(item.type.name) {
                                detectDragGestures(
                                    onDragStart = { startDrag(item.type) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragBy(item.type, dragAmount.y)
                                    },
                                    onDragEnd = endDrag,
                                    onDragCancel = endDrag
                                )
                            }
                        )
                    }
                }

                // Reset button
                LegadoMiuixActionButton(
                    text = stringResource(R.string.reset),
                    palette = palette.miuix,
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            BookInfoPageStyle.IMMERSIVE_COMPOSE -> {
                // Immersive info panel
                AppManagementCard(
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth(),
                    insidePadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_info_style_immersive_title),
                        color = palette.settings.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.book_info_style_immersive_desc),
                        color = palette.settings.secondaryText,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StyleTabBar(
    style: BookInfoPageStyle,
    palette: AppManagementPalette,
    onStyleChanged: (BookInfoPageStyle) -> Unit
) {
    val isClassic = style == BookInfoPageStyle.CLASSIC
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StyleTabButton(
            text = stringResource(R.string.book_info_style_classic),
            selected = isClassic,
            palette = palette,
            onClick = { onStyleChanged(BookInfoPageStyle.CLASSIC) },
            modifier = Modifier.weight(1f)
        )
        StyleTabButton(
            text = stringResource(R.string.book_info_style_immersive),
            selected = !isClassic,
            palette = palette,
            onClick = { onStyleChanged(BookInfoPageStyle.IMMERSIVE_COMPOSE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StyleTabButton(
    text: String,
    selected: Boolean,
    palette: AppManagementPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    val backgroundColor = if (selected) {
        palette.settings.accent.copy(alpha = 0.14f)
    } else {
        palette.miuix.surfaceVariant
    }
    val textColor = if (selected) palette.settings.accent else palette.settings.secondaryText

    Box(
        modifier = modifier
            .height(height)
            .background(backgroundColor, RoundedCornerShape(palette.miuix.actionRadius ?: 12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComponentItemRow(
    item: BookInfoComponentItem,
    palette: AppManagementPalette,
    catalogOpenMode: BookInfoCatalogOpenMode,
    onCatalogOpenModeChanged: (BookInfoCatalogOpenMode) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rowDragModifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    AppManagementCard(
        palette = palette,
        modifier = modifier.fillMaxWidth(),
        insidePadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegadoMiuixSwitch(
                checked = item.enabled,
                onCheckedChange = onCheckedChange,
                palette = palette.miuix
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(rowDragModifier)
            ) {
                Text(
                    text = stringResource(item.type.titleRes),
                    color = palette.settings.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(item.type.hintRes),
                    color = palette.settings.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrange),
                    contentDescription = stringResource(R.string.read_record_drag_sort),
                    tint = palette.settings.secondaryText.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (item.type == BookInfoComponentType.CATALOG && item.enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.book_info_catalog_open_mode),
                color = palette.settings.secondaryText,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StyleTabButton(
                    text = stringResource(R.string.book_info_catalog_open_swipe),
                    selected = catalogOpenMode == BookInfoCatalogOpenMode.SWIPE,
                    palette = palette,
                    onClick = { onCatalogOpenModeChanged(BookInfoCatalogOpenMode.SWIPE) },
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
                StyleTabButton(
                    text = stringResource(R.string.book_info_catalog_open_button),
                    selected = catalogOpenMode == BookInfoCatalogOpenMode.BUTTON,
                    palette = palette,
                    onClick = { onCatalogOpenModeChanged(BookInfoCatalogOpenMode.BUTTON) },
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
            }
        }
    }
}
