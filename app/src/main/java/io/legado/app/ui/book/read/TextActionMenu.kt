package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.ContentSelectMenuConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    private var menuItems: List<MenuItemImpl> = emptyList()
    private val builtInItems = arrayListOf<MenuItemImpl>()
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        val myMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        builtInItems.addAll(myMenu.visibleItems)
        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter
        setOnDismissListener {
            if (!context.getPrefBoolean(PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        upMenu()
    }

    fun upMenu() {
        menuItems = orderedBuiltInItems() + buildProcessTextItems()
        visibleMenuItems.clear()
        visibleMenuItems.addAll(menuItems.take(5))
        moreMenuItems.clear()
        moreMenuItems.addAll(menuItems.drop(5))
        binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
        binding.recyclerViewMore.gone()
        binding.recyclerView.visible()
        if (expandTextMenu) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.visible()
        }
    }

    /**
     * 按用户保存的顺序排列内置动作；没有保存过就用菜单默认顺序。
     */
    private fun orderedBuiltInItems(): List<MenuItemImpl> {
        val disabled = ContentSelectMenuConfig.disabledBuiltIn(context)
        val enabled = builtInItems.filter {
            context.resources.getResourceEntryName(it.itemId) !in disabled
        }
        // 全关掉时至少留「复制」，否则长按条会空着没法选文字
        if (enabled.isEmpty()) {
            return builtInItems.filter {
                context.resources.getResourceEntryName(it.itemId) == "menu_copy"
            }.ifEmpty { builtInItems }
        }
        val order = ContentSelectMenuConfig.savedOrder(context)
        if (order.isEmpty()) return enabled
        val index = order.withIndex().associate { it.value to it.index }
        return enabled.sortedBy {
            index[context.resources.getResourceEntryName(it.itemId)] ?: Int.MAX_VALUE
        }
    }

    /**
     * 系统「文字处理」动作，可在配置里逐个关掉；每次读配置，新装应用不用重启。
     */
    private fun buildProcessTextItems(): List<MenuItemImpl> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val menu = MenuBuilder(context)
        onInitializeMenu(menu)
        val disabled = ContentSelectMenuConfig.disabledProcessText(context)
        val enabled = menu.visibleItems.filter { item ->
            val component = item.intent?.component
            component == null || "${component.packageName}/${component.className}" !in disabled
        }
        val order = ContentSelectMenuConfig.processTextOrder(context)
        if (order.isEmpty()) return enabled
        val index = order.withIndex().associate { it.value to it.index }
        return enabled.sortedBy { item ->
            val component = item.intent?.component
            if (component == null) Int.MAX_VALUE
            else index["${component.packageName}/${component.className}"] ?: Int.MAX_VALUE
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        contentView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED,
        )
        val density = context.resources.displayMetrics.density
        val margin = (6 * density).toInt()
        val popupWidth = contentView.measuredWidth
        val popupHeight = contentView.measuredHeight
        // 横向不要超出屏幕
        val maxX = (view.width - popupWidth - margin).coerceAtLeast(margin)
        val x = startX.coerceIn(margin, maxX)
        // 优先放在选区上方（底边贴着选中第一行的行顶），放不下再放到选区下方，
        // 两边都放不下才夹在屏幕内，避免像原来那样压在选中的文字上。
        var y = startTopY - popupHeight - margin
        if (y < margin) {
            y = endBottomY + margin
            if (y + popupHeight > windowHeight - margin) {
                y = (windowHeight - popupHeight - margin).coerceAtLeast(margin)
            }
        }
        showAtLocation(view, Gravity.TOP or Gravity.START, x, y)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = if (item.itemId == R.id.menu_aloud) {
                    // 有选中内容时只读这一段，没有选中才从光标处一直往下读
                    if (callBack.selectedText.isNotBlank()) {
                        context.getString(R.string.read_aloud_selected)
                    } else {
                        context.getString(R.string.read_aloud_from_here)
                    }
                } else {
                    item.title
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                callBack.onMenuActionFinally()
            }
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
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

            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
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

    /**
     * Start with a menu Item order value that is high enough
     * so that your "PROCESS_TEXT" menu items appear after the
     * standard selection menu items like Cut, Copy, Paste.
     */
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
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}