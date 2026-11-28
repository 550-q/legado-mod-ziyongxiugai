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

    /** 引擎管理：点击编辑名称/地址，底部可恢复默认或新增 */
    private fun showEngineManageDialog() {
        val context = requireContext()
        val engines = SelectionSearchEngineConfig.engines(context).toMutableList()
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        fun render() {
            listLayout.removeAllViews()
            engines.forEach { engine ->
                val row = TextView(context).apply {
                    text = "${engine.name}\n${engine.url}"
                    textSize = 14f
                    setTextColor(context.primaryTextColor)
                    setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
                    setOnClickListener { showEditEngineDialog(engine) { updated -> replaceEngine(updated) } }
                }
                listLayout.addView(row)
            }
        }
        render()
        AlertDialog.Builder(context)
            .setTitle(R.string.selection_search_engine_title)
            .setView(listLayout)
            .setNeutralButton(R.string.reset) { _, _ ->
                SelectionSearchEngineConfig.resetToDefault(context)
                // 当前引擎可能被删掉，重新按默认引擎加载
                loadSearch(
                    binding.editQuery.text?.toString().orEmpty(),
                    SelectionSearchEngineConfig.currentEngine(context).id
                )
            }
            .setNegativeButton(R.string.add) { _, _ ->
                showEditEngineDialog(null) { updated -> engines.add(updated); replaceEngines(engines) }
            }
            .setPositiveButton(R.string.cancel, null)
            .show()
    }

    private fun replaceEngine(updated: SelectionSearchEngineConfig.SearchEngine) {
        val engines = SelectionSearchEngineConfig.engines(requireContext()).toMutableList()
        val index = engines.indexOfFirst { it.id == updated.id }
        if (index >= 0) engines[index] = updated else engines.add(updated)
        SelectionSearchEngineConfig.saveEngines(requireContext(), engines)
        if (updated.id == currentEngine?.id) {
            loadSearch(binding.editQuery.text?.toString().orEmpty(), updated.id)
        }
    }

    private fun replaceEngines(engines: List<SelectionSearchEngineConfig.SearchEngine>) {
        SelectionSearchEngineConfig.saveEngines(requireContext(), engines)
        loadSearch(binding.editQuery.text?.toString().orEmpty(), currentEngine?.id.orEmpty())
    }

    private fun showEditEngineDialog(
        engine: SelectionSearchEngineConfig.SearchEngine?,
        onSave: (SelectionSearchEngineConfig.SearchEngine) -> Unit
    ) {
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
        layout.addView(editName)
        layout.addView(editUrl)
        AlertDialog.Builder(context)
            .setTitle(R.string.selection_search_engine_title)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = editName.text?.toString().orEmpty().trim()
                val url = editUrl.text?.toString().orEmpty().trim()
                if (name.isBlank() || url.isBlank()) return@setPositiveButton
                onSave(
                    SelectionSearchEngineConfig.SearchEngine(
                        id = engine?.id?.takeIf { it.isNotBlank() } ?: "custom_${System.currentTimeMillis()}",
                        name = name,
                        url = url,
                        hideCss = engine?.hideCss
                    )
                )
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
