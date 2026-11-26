package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.read.ContentSelectConfig
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.widget.compose.AppDialogSize
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.putPrefStringSet
import kotlinx.coroutines.delay
import kotlin.math.abs

class ContentSelectMenuConfigDialog : ComposeDialogFragment() {

    override val dialogSize: AppDialogSize = AppDialogSize.Form

    private data class ActionItem(
        val id: String,
        val titleRes: Int
    )

    private data class MenuEntry(
        val id: String,
        val title: String,
        val subtitle: String? = null
    )

    private data class DefaultOpenItem(
        val id: String,
        val titleRes: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val initialSelected = remember {
                    sanitizeActionIds(
                        requireContext(),
                        ContentSelectConfig.selectedActionIds(requireContext())
                    ).toList()
                }
                var selectedActions by rememberSaveable {
                    mutableStateOf(initialSelected)
                }
                var builtinEntries by remember {
                    mutableStateOf(buildBuiltinEntries(requireContext()))
                }
                var processEntries by remember {
                    mutableStateOf(buildProcessEntries(requireContext()))
                }
                var draggingId by remember { mutableStateOf<String?>(null) }
                val moveEntry: (String, Int) -> Unit = remember {
                    { id, targetIndex ->
                        val isBuiltin = builtinEntries.any { it.id == id }
                        val list = if (isBuiltin) builtinEntries else processEntries
                        val from = list.indexOfFirst { it.id == id }
                        if (from >= 0) {
                            val to = targetIndex.coerceIn(0, list.lastIndex)
                            if (to != from) {
                                val updated = list.toMutableList()
                                    .apply { add(to, removeAt(from)) }
                                if (isBuiltin) {
                                    builtinEntries = updated
                                } else {
                                    processEntries = updated
                                }
                            }
                        }
                    }
                }
                val initialDefaultOpen = remember {
                    requireContext()
                        .getPrefString(PreferKey.contentSelectDefaultOpen, "")
                        .orEmpty()
                        .takeIf { it in defaultOpenValues }
                        .orEmpty()
                }
                var defaultOpen by rememberSaveable {
                    mutableStateOf(initialDefaultOpen)
                }
                val style = rememberAppDialogStyle()
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = style.bodyFontFamily)
                ) {
                    AppDialogFrame(
                        title = stringResource(R.string.content_select_menu_config),
                        content = {
                            ContentSelectMenuContent(
                                entries = builtinEntries,
                                processEntries = processEntries,
                                selectedActions = selectedActions.toSet(),
                                defaultOpen = defaultOpen,
                                style = style,
                                draggingId = draggingId,
                                onDragStart = { draggingId = it },
                                onDragEnd = { draggingId = null },
                                onMove = moveEntry,
                                onActionToggle = { actionId ->
                                    selectedActions = selectedActions
                                        .toMutableSet()
                                        .apply {
                                            if (!add(actionId)) {
                                                remove(actionId)
                                            }
                                        }
                                        .toList()
                                },
                                onDefaultOpenChange = { defaultOpen = it }
                            )
                        },
                        actions = {
                            val palette = style.toMiuixPalette()
                            LegadoMiuixActionButton(
                                text = stringResource(R.string.cancel),
                                palette = palette,
                                onClick = { dismissAllowingStateLoss() },
                                cornerRadius = style.actionRadius
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LegadoMiuixActionButton(
                                text = stringResource(R.string.ok),
                                palette = palette,
                                primary = true,
                                onClick = {
                                    saveConfig(
                                        selectedActions.toSet(),
                                        defaultOpen,
                                        builtinEntries.map { it.id } + processEntries.map { it.id }
                                    )
                                },
                                cornerRadius = style.actionRadius
                            )
                        }
                    )
                }
            }
        }
    }

    private fun saveConfig(
        actions: Set<String>,
        defaultOpen: String,
        order: List<String>
    ) {
        val selected = sanitizeActionIds(requireContext(), actions).toMutableSet()
        if (defaultOpen.isNotEmpty()) {
            selected += defaultOpen
        }
        if (selected.isEmpty()) {
            selected += ContentSelectConfig.ACTION_COPY
        }
        requireContext().putPrefStringSet(PreferKey.contentSelectActions, selected)
        ContentSelectConfig.saveActionOrder(requireContext(), order)
        requireContext().putPrefString(PreferKey.contentSelectDefaultOpen, defaultOpen)
        postEvent(EventBus.CONTENT_SELECT_MENU_CONFIG_CHANGED, true)
        dismissAllowingStateLoss()
    }

    @Composable
    private fun ContentSelectMenuContent(
        entries: List<MenuEntry>,
        processEntries: List<MenuEntry>,
        selectedActions: Set<String>,
        defaultOpen: String,
        style: AppDialogStyle,
        draggingId: String?,
        onDragStart: (String) -> Unit,
        onDragEnd: () -> Unit,
        onMove: (String, Int) -> Unit,
        onActionToggle: (String) -> Unit,
        onDefaultOpenChange: (String) -> Unit
    ) {
        // 手势挂在整列上，拖动排序时条目重排不会打断手势（否则会只动一格、来回闪）。
        val rowBounds = remember { mutableStateMapOf<String, Rect>() }
        val checkboxBounds = remember { mutableStateMapOf<String, Rect>() }
        var pendingId by remember { mutableStateOf<String?>(null) }
        var armed by remember { mutableStateOf(false) }
        // 拖动开始时把各行的位置定住，之后用固定坐标算目标位置，
        // 避免重排后位置变化导致两行来回交换、画面闪烁。
        var dragSlots by remember { mutableStateOf<List<Pair<String, Rect>>>(emptyList()) }
        LaunchedEffect(pendingId) {
            if (pendingId != null) {
                delay(1000L)
                armed = true
            }
        }

        fun checkboxHit(position: Offset): String? {
            for ((id, row) in rowBounds) {
                val box = checkboxBounds[id] ?: continue
                if (position.x >= row.left + box.left && position.x <= row.left + box.right &&
                    position.y >= row.top + box.top && position.y <= row.top + box.bottom
                ) {
                    return id
                }
            }
            return null
        }

        fun groupOf(id: String): List<MenuEntry>? {
            return when {
                entries.any { it.id == id } -> entries
                processEntries.any { it.id == id } -> processEntries
                else -> null
            }
        }

        fun slotIndexAt(y: Float): Int {
            if (dragSlots.isEmpty()) return -1
            val containing = dragSlots.indexOfFirst { y >= it.second.top && y <= it.second.bottom }
            if (containing >= 0) return containing
            return dragSlots.indices.minByOrNull { index ->
                val rect = dragSlots[index].second
                abs((rect.top + rect.bottom) / 2f - y)
            } ?: -1
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = checkboxHit(down.position) ?: return@awaitEachGesture
                        pendingId = hit
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging || armed) {
                                    change.consume()
                                }
                                break
                            }
                            if (!dragging && armed) {
                                val group = groupOf(hit)
                                dragSlots = group?.mapNotNull { item ->
                                    rowBounds[item.id]?.let { item.id to it }
                                } ?: emptyList()
                                dragging = true
                                onDragStart(hit)
                            }
                            if (dragging) {
                                val target = slotIndexAt(change.position.y)
                                if (target >= 0) {
                                    onMove(hit, target)
                                }
                                change.consume()
                            }
                        }
                        if (dragging) {
                            onDragEnd()
                        }
                        dragSlots = emptyList()
                        pendingId = null
                        armed = false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(
                title = stringResource(R.string.content_select_actions),
                style = style
            )
            entries.forEach { entry ->
                key(entry.id) {
                    CheckRow(
                        title = entry.title,
                        checked = entry.id in selectedActions,
                        style = style,
                        subtitle = entry.subtitle,
                        dragging = draggingId == entry.id,
                        onPositioned = { rect ->
                            if (rowBounds[entry.id] != rect) rowBounds[entry.id] = rect
                        },
                        onCheckboxPositioned = { rect ->
                            if (checkboxBounds[entry.id] != rect) checkboxBounds[entry.id] = rect
                        },
                        onClick = { onActionToggle(entry.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle(
                title = stringResource(R.string.content_select_process_text_actions),
                style = style
            )
            if (processEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.content_select_process_text_empty),
                    color = style.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            } else {
                processEntries.forEach { entry ->
                    key(entry.id) {
                        CheckRow(
                            title = entry.title,
                            checked = entry.id in selectedActions,
                            style = style,
                            dragging = draggingId == entry.id,
                            onPositioned = { rect ->
                                if (rowBounds[entry.id] != rect) rowBounds[entry.id] = rect
                            },
                            onCheckboxPositioned = { rect ->
                                if (checkboxBounds[entry.id] != rect) checkboxBounds[entry.id] = rect
                            },
                            onClick = { onActionToggle(entry.id) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle(
                title = stringResource(R.string.content_select_default_open),
                style = style
            )
            defaultOpenItems.forEach { item ->
                RadioRow(
                    title = stringResource(item.titleRes),
                    selected = defaultOpen == item.id,
                    style = style,
                    onClick = { onDefaultOpenChange(item.id) }
                )
            }
        }
    }

    @Composable
    private fun SectionTitle(
        title: String,
        style: AppDialogStyle
    ) {
        Text(
            text = title,
            color = style.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = style.titleFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp)
        )
    }

    @Composable
    private fun CheckRow(
        title: String,
        checked: Boolean,
        style: AppDialogStyle,
        subtitle: String? = null,
        dragging: Boolean = false,
        onPositioned: (Rect) -> Unit,
        onCheckboxPositioned: (Rect) -> Unit,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onPositioned(it.boundsInParent()) }
                .background(
                    if (dragging) style.accent.copy(alpha = 0.12f) else Color.Transparent
                )
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onClick() }
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = style.primaryText,
                fontSize = 15.sp,
                maxLines = 1
            )
            if (subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    color = style.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier.onGloballyPositioned { onCheckboxPositioned(it.boundsInParent()) },
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = style.accent,
                        uncheckedColor = style.secondaryText,
                        checkmarkColor = style.surface
                    )
                )
            }
        }
    }

    @Composable
    private fun RadioRow(
        title: String,
        selected: Boolean,
        style: AppDialogStyle,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = style.primaryText,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = style.accent,
                    unselectedColor = style.secondaryText
                )
            )
        }
    }

    companion object {
        private val defaultOpenValues = ContentSelectConfig.defaultOpenValues

        private val actionItems = listOf(
            ActionItem(ContentSelectConfig.ACTION_REPLACE, R.string.replace),
            ActionItem(ContentSelectConfig.ACTION_COPY, R.string.copy_text),
            ActionItem(ContentSelectConfig.ACTION_WEB_SEARCH, R.string.search),
            ActionItem(ContentSelectConfig.ACTION_BOOKMARK, R.string.bookmark),
            ActionItem(ContentSelectConfig.ACTION_ALOUD, R.string.read_aloud),
            ActionItem(ContentSelectConfig.ACTION_DICT, R.string.dict),
            ActionItem(ContentSelectConfig.ACTION_ASK_AI, R.string.ask_ai),
            ActionItem(ContentSelectConfig.ACTION_GENERATE_IMAGE, R.string.ai_image_generate),
            ActionItem(ContentSelectConfig.ACTION_SHARE_IMAGE, R.string.share)
        )
        private val knownActionIds = actionItems.map { it.id }.toSet()

        private val defaultOpenItems = listOf(
            DefaultOpenItem("", R.string.default_none),
            DefaultOpenItem(ContentSelectConfig.ACTION_WEB_SEARCH, R.string.default_search),
            DefaultOpenItem(ContentSelectConfig.ACTION_DICT, R.string.default_dict),
            DefaultOpenItem(ContentSelectConfig.ACTION_ASK_AI, R.string.default_ask_ai)
        )

        private fun buildBuiltinEntries(context: Context): List<MenuEntry> {
            val entries = actionItems.map { item ->
                MenuEntry(
                    id = item.id,
                    title = context.getString(item.titleRes),
                    subtitle = if (item.id == ContentSelectConfig.ACTION_WEB_SEARCH) {
                        context.getString(R.string.content_select_search_hint)
                    } else {
                        null
                    }
                )
            }
            return applySavedOrder(context, entries)
        }

        private fun buildProcessEntries(context: Context): List<MenuEntry> {
            val entries = ContentSelectConfig.processTextActions(context)
                .map { MenuEntry(it.id, it.label) }
            return applySavedOrder(context, entries)
        }

        private fun applySavedOrder(
            context: Context,
            entries: List<MenuEntry>
        ): List<MenuEntry> {
            val order = ContentSelectConfig.actionOrder(context)
            if (order.isEmpty()) return entries
            val index = order.withIndex().associate { it.value to it.index }
            return entries.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }

        private fun sanitizeActionIds(
            context: Context,
            ids: Iterable<String>
        ): Set<String> {
            val knownIds = knownActionIds +
                ContentSelectConfig.processTextActions(context).map { it.id }
            return ids.filterTo(linkedSetOf()) { it in knownIds }
        }
    }
}
