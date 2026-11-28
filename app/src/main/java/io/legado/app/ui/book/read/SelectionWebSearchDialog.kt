package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.databinding.DialogSelectionWebSearchBinding
import io.legado.app.help.config.SelectionSearchEngineConfig
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import org.json.JSONObject

/**
 * 正文长按「全文搜索」长按弹出的网页搜索窗口。
 * 布局与操作跟随 mod/archive 版：顶部返回 + 搜索框 + 更多菜单，
 * 下面一排搜索引掣（百度百科 / Bing / Baidu / Google）。
 */
class SelectionWebSearchDialog() : BottomSheetDialogFragment(R.layout.dialog_selection_web_search) {

    constructor(query: String) : this() {
        arguments = Bundle().apply {
            putString("query", query)
        }
    }

    private val binding by viewBinding(DialogSelectionWebSearchBinding::bind)
    private lateinit var pooledWebView: PooledWebView
    private lateinit var webView: WebView
    private var currentEngine: SelectionSearchEngineConfig.SearchEngine? = null
    private var currentQuery: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        pooledWebView = WebViewPool.acquire(context)
        webView = pooledWebView.realWebView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleBack()
                true
            } else {
                false
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0.32f)
        val sheetHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight)
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT)
            // BottomSheet 自身高度必须显式给足，否则首次弹出会按内容高度折叠成一条
            sheet.layoutParams = sheet.layoutParams.apply {
                this.height = sheetHeight
                this.width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            val behavior = BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                isHideable = true
                peekHeight = sheetHeight
            }
            // 等布局完成再展开，首次弹出也按全高显示
            sheet.post {
                if (isAdded) {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyStyle()
        binding.webViewContainer.addView(
            webView,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        prepareWebView()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visible()
                binding.progressBar.progress = 8
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.gone()
                injectHideCss()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress >= 100) binding.progressBar.gone()
            }
        }
        currentQuery = arguments?.getString("query").orEmpty().trim()
        binding.editQuery.setText(currentQuery)
        binding.editQuery.setSelection(binding.editQuery.text?.length ?: 0)
        bindActions()
        loadSearch(currentQuery, SelectionSearchEngineConfig.currentEngine(requireContext()).id)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareWebView() {
        webView.run {
            setBackgroundColor(requireContext().backgroundColor)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100
            }
            resumeTimers()
            onResume()
        }
    }

    private fun applyStyle() = binding.run {
        val radius = 16f.dpToPx()
        sheetSurface.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(requireContext().backgroundColor)
        }
        val textColor = requireContext().primaryTextColor
        btnBack.setColorFilter(textColor)
        btnMore.setColorFilter(textColor)
        editQuery.setTextColor(textColor)
        editQuery.setHintTextColor(requireContext().secondaryTextColor)
        editQuery.background = GradientDrawable().apply {
            cornerRadius = 20f.dpToPx()
            setColor(requireContext().secondaryTextColor and 0x22FFFFFF.toInt())
        }
        progressBar.progressTintList =
            android.content.res.ColorStateList.valueOf(requireContext().accentColor)
    }

    private fun bindActions() = binding.run {
        btnBack.setOnClickListener {
            handleBack()
        }
        btnMore.setOnClickListener {
            showMoreMenu()
        }
        editQuery.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                loadSearch(editQuery.text?.toString().orEmpty(), currentEngine?.id.orEmpty())
                true
            } else {
                false
            }
        }
    }

    private fun showMoreMenu() {
        PopupMenu(requireContext(), binding.btnMore, Gravity.NO_GRAVITY).apply {
            menu.add(Menu.NONE, MENU_OPEN_BROWSER, 1, R.string.open_in_browser)
            menu.add(Menu.NONE, MENU_REFRESH, 2, R.string.refresh)
            menu.add(Menu.NONE, MENU_EDIT_ENGINE, 3, R.string.edit)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    MENU_OPEN_BROWSER -> openInBrowser()
                    MENU_REFRESH -> webView.reload()
                    MENU_EDIT_ENGINE -> showEngineManageDialog()
                }
                true
            }
            show()
        }
    }

    private fun openInBrowser() {
        val url = webView.url ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            requireContext().toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    /** 引擎管理：卡片列表，每项可编辑名称/地址/CSS，可删除（带确认） */
    private fun showEngineManageDialog() {
        val context = requireContext()
        val scrollView = android.widget.ScrollView(context).apply {
            setPadding(0, 8.dpToPx(), 0, 0)
        }
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        }
        scrollView.addView(listLayout)
        engineDialogList = listLayout
        renderEngineCards()

        AlertDialog.Builder(context)
            .setTitle(R.string.selection_search_engine_title)
            .setView(scrollView)
            .setNeutralButton(R.string.reset) { _, _ ->
                SelectionSearchEngineConfig.resetToDefault(context)
                val engine = SelectionSearchEngineConfig.currentEngine(context)
                loadSearch(binding.editQuery.text?.toString().orEmpty(), engine.id)
            }
            .setNegativeButton(R.string.add) { _, _ ->
                showEditEngineDialog(null)
            }
            .setPositiveButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    scrollView.layoutParams = scrollView.layoutParams.apply {
                        height = (resources.displayMetrics.heightPixels * 0.55f).toInt()
                    }
                }
                dialog.show()
            }
    }

    private var engineDialogList: LinearLayout? = null

    private fun renderEngineCards() {
        val context = requireContext()
        val listLayout = engineDialogList ?: return
        listLayout.removeAllViews()
        SelectionSearchEngineConfig.engines(context).forEach { engine ->
            listLayout.addView(createEngineCard(engine))
        }
    }

    /** 引擎列表改动后：刷新列表、必要时切换当前引擎并重新搜索 */
    private fun onEnginesChanged() {
        val context = requireContext()
        val engines = SelectionSearchEngineConfig.engines(context)
        if (engines.isEmpty()) {
            SelectionSearchEngineConfig.resetToDefault(context)
        }
        val current = currentEngine
        val newCurrent = SelectionSearchEngineConfig.currentEngine(context)
        if (current == null || engines.none { it.id == current.id }) {
            loadSearch(binding.editQuery.text?.toString().orEmpty(), newCurrent.id)
        }
        renderEngineCards()
    }

    /** 单个引擎卡片：名称 / 地址 / CSS 屏蔽状态 / 编辑·删除 */
    private fun createEngineCard(
        engine: SelectionSearchEngineConfig.SearchEngine
    ): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dpToPx(), 12.dpToPx(), 14.dpToPx(), 10.dpToPx())
            background = GradientDrawable().apply {
                cornerRadius = 12f.dpToPx()
                setColor(context.secondaryTextColor and 0x14FFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
        }
        val nameView = TextView(context).apply {
            text = engine.name
            textSize = 16f
            maxLines = 1
            setTextColor(context.primaryTextColor)
        }
        val urlView = TextView(context).apply {
            text = engine.url
            textSize = 12f
            setPadding(0, 5.dpToPx(), 0, 6.dpToPx())
            setTextColor(context.secondaryTextColor)
        }
        val cssView = TextView(context).apply {
            text = context.getString(R.string.selection_search_engine_hide_css_enabled)
            textSize = 12f
            setTextColor(context.accentColor)
            visibility = if (engine.hideCss.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val editButton = cardButton(context, context.getString(R.string.edit))
        val deleteButton = cardButton(context, context.getString(R.string.delete))
        editButton.setOnClickListener { showEditEngineDialog(engine) }
        deleteButton.setOnClickListener { confirmDeleteEngine(engine) }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 6.dpToPx(), 0, 0)
            addView(editButton)
            addView(deleteButton)
        }
        card.addView(nameView)
        card.addView(urlView)
        card.addView(cssView)
        card.addView(buttonRow)
        return card
    }

    private fun cardButton(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(context.accentColor)
            setPadding(12.dpToPx(), 7.dpToPx(), 12.dpToPx(), 7.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8.dpToPx()
            }
        }
    }

    private fun confirmDeleteEngine(engine: SelectionSearchEngineConfig.SearchEngine) {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(R.string.delete)
            .setMessage(engine.name)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val engines = SelectionSearchEngineConfig.engines(context)
                    .filterNot { it.id == engine.id }
                if (engines.isEmpty()) {
                    SelectionSearchEngineConfig.resetToDefault(context)
                } else {
                    SelectionSearchEngineConfig.saveEngines(context, engines)
                    if (currentEngine?.id == engine.id) {
                        SelectionSearchEngineConfig.selectEngine(context, engines.first().id)
                    }
                }
                onEnginesChanged()
            }
            .show()
    }

    private fun showEditEngineDialog(engine: SelectionSearchEngineConfig.SearchEngine?) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        val editName = EditText(context).apply {
            hint = context.getString(R.string.selection_search_engine_name)
            setText(engine?.name.orEmpty())
            setSingleLine()
        }
        val editUrl = EditText(context).apply {
            hint = context.getString(R.string.selection_search_engine_url)
            setText(engine?.url.orEmpty())
            setSingleLine()
        }
        val editCss = EditText(context).apply {
            hint = context.getString(R.string.selection_search_engine_hide_css_hint)
            gravity = Gravity.TOP or Gravity.START
            setText(engine?.hideCss.orEmpty())
            setTextSize(13f)
            minLines = 2
            maxLines = 6
            setHorizontallyScrolling(false)
            setSingleLine(false)
        }
        layout.addView(editName)
        layout.addView(editUrl)
        layout.addView(editCss)
        AlertDialog.Builder(context)
            .setTitle(if (engine == null) R.string.add else R.string.edit)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = editName.text?.toString().orEmpty().trim()
                val url = editUrl.text?.toString().orEmpty().trim()
                val css = editCss.text?.toString().orEmpty().trim()
                if (name.isBlank() || url.isBlank()) {
                    context.toastOnUi(R.string.cannot_empty)
                    return@setPositiveButton
                }
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                    context.toastOnUi(R.string.selection_search_engine_url_error)
                    return@setPositiveButton
                }
                val edited = SelectionSearchEngineConfig.SearchEngine(
                    id = engine?.id?.takeIf { it.isNotBlank() }
                        ?: "engine_${System.currentTimeMillis()}",
                    name = name,
                    url = url,
                    hideCss = css
                )
                val engines = SelectionSearchEngineConfig.engines(context).toMutableList()
                val index = engines.indexOfFirst { it.id == edited.id }
                if (index >= 0) engines[index] = edited else engines.add(edited)
                SelectionSearchEngineConfig.saveEngines(context, engines)
                if (edited.id == currentEngine?.id) {
                    loadSearch(binding.editQuery.text?.toString().orEmpty(), edited.id)
                }
                onEnginesChanged()
            }
            .show()
    }

    private fun renderEngines() {
        val context = requireContext()
        binding.engineContainer.removeAllViews()
        SelectionSearchEngineConfig.engines(context).forEach { engine ->
            val selected = engine.id == currentEngine?.id
            val chip = TextView(context).apply {
                text = engine.name
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
                isFocusable = false
                setTextColor(if (selected) context.accentColor else context.secondaryTextColor)
                background = GradientDrawable().apply {
                    cornerRadius = 16f.dpToPx()
                    setColor(
                        if (selected) context.accentColor and 0x33FFFFFF.toInt()
                        else context.secondaryTextColor and 0x1AFFFFFF.toInt()
                    )
                }
                setPadding(14.dpToPx(), 8.dpToPx(), 14.dpToPx(), 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8.dpToPx()
                }
                setOnClickListener {
                    SelectionSearchEngineConfig.selectEngine(context, engine.id)
                    loadSearch(binding.editQuery.text?.toString().orEmpty(), engine.id)
                }
            }
            binding.engineContainer.addView(chip)
        }
        binding.engineContainer.post {
            (binding.engineContainer.parent as? android.widget.HorizontalScrollView)?.scrollTo(0, 0)
        }
    }

    private fun loadSearch(rawQuery: String, engineId: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            requireContext().toastOnUi(R.string.cannot_empty)
            return
        }
        currentQuery = query
        if (binding.editQuery.text?.toString() != query) {
            binding.editQuery.setText(query)
            binding.editQuery.setSelection(query.length)
        }
        val engine = SelectionSearchEngineConfig.engines(requireContext())
            .firstOrNull { it.id == engineId }
            ?: SelectionSearchEngineConfig.currentEngine(requireContext())
        currentEngine = engine
        renderEngines()
        SelectionSearchEngineConfig.selectEngine(requireContext(), engine.id)
        webView.stopLoading()
        webView.clearHistory()
        webView.loadUrl(SelectionSearchEngineConfig.buildSearchUrl(engine, query))
    }

    private fun injectHideCss() {
        val css = currentEngine?.hideCss?.takeIf { it.isNotBlank() } ?: return
        val js = """
            (function() {
                try {
                    var css = ${JSONObject.quote(css)};
                    var id = 'legado-selection-search-hide-css';
                    var style = document.getElementById(id);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = id;
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.textContent = css;
                } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun handleBack() {
        if (!::webView.isInitialized || !webView.canGoBack()) {
            dismissAllowingStateLoss()
            return
        }
        val history = webView.copyBackForwardList()
        val previousIndex = history.currentIndex - 1
        val previousUrl = if (previousIndex >= 0) {
            history.getItemAtIndex(previousIndex)?.url.orEmpty()
        } else {
            ""
        }
        if (previousUrl.isBlank() || previousUrl == WebViewPool.BLANK_HTML) {
            dismissAllowingStateLoss()
        } else {
            webView.goBack()
        }
    }

    override fun onDestroyView() {
        currentEngine = null
        webView.stopLoading()
        binding.webViewContainer.removeView(webView)
        WebViewPool.release(pooledWebView)
        super.onDestroyView()
    }

    private companion object {
        const val MENU_OPEN_BROWSER = 1
        const val MENU_REFRESH = 2
        const val MENU_EDIT_ENGINE = 3
    }
}
