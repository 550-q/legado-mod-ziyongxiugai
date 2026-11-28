package io.legado.app.help.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 正文长按「全文搜索」长按时弹出的网页搜索窗口所用的搜索引擎配置。
 * 默认引擎与 mod/archive 版一致：百度百科 / Bing / Baidu / Google。
 */
object SelectionSearchEngineConfig {

    data class SearchEngine(
        val id: String = "",
        val name: String = "",
        val url: String = "",
        val hideCss: String? = null
    )

    private const val KEY_ENGINES = "selectionWebSearchEngines"
    private const val KEY_CURRENT = "selectionWebSearchCurrentEngine"

    private val gson = Gson()

    private val defaultBingHideCss = """
        header#b_header,
        #b_header,
        #sb_form,
        #b_searchboxForm,
        .b_searchboxForm,
        .b_searchbox,
        .b_scopebar,
        #est_switch {
            display: none !important;
        }
    """.trimIndent()

    private val defaultBaiduHideCss = """
        .se-page-hd,
        .se-page-hd-fixed,
        .se-results-top,
        .se-head-tablink,
        .se-head-logo,
        .se-box,
        .se-form,
        .se-searchbox,
        .sfc-dom,
        #head,
        #s_tab,
        #u,
        .s_form {
            display: none !important;
        }
    """.trimIndent()

    private val defaultBaiduBaikeHideCss = """
        header,
        .navbar-wrapper,
        .lemmaWgt-searchHeader,
        .search-box,
        .header-wrapper,
        .baike-header,
        .wgt-searchbar {
            display: none !important;
        }
    """.trimIndent()

    private val defaultGoogleHideCss = """
        header,
        #searchform,
        .sfbg,
        .minidiv,
        [jsname="RNNXgb"],
        form[role="search"],
        div[role="search"] {
            display: none !important;
        }
    """.trimIndent()

    val defaultEngines = listOf(
        SearchEngine(
            "baidu_baike",
            "百度百科",
            "https://baike.baidu.com/item/{queryPath}",
            defaultBaiduBaikeHideCss
        ),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q={query}", defaultBingHideCss),
        SearchEngine("baidu", "Baidu", "https://www.baidu.com/s?wd={query}", defaultBaiduHideCss),
        SearchEngine("google", "Google", "https://www.google.com/search?q={query}", defaultGoogleHideCss)
    )

    fun engines(context: Context): List<SearchEngine> {
        val raw = context.getPrefString(KEY_ENGINES).orEmpty()
        if (raw.isBlank()) return defaultEngines
        val parsed = runCatching {
            gson.fromJson<Array<SearchEngine>>(
                raw, object : TypeToken<Array<SearchEngine>>() {}.type
            )?.toList()
        }.getOrNull()
        if (parsed.isNullOrEmpty()) return defaultEngines
        return parsed.filter { it.id.isNotBlank() && it.url.isNotBlank() }
            .ifEmpty { defaultEngines }
    }

    fun saveEngines(context: Context, engines: List<SearchEngine>) {
        context.putPrefString(KEY_ENGINES, gson.toJson(engines))
    }

    fun resetToDefault(context: Context) {
        context.putPrefString(KEY_ENGINES, null)
        context.putPrefString(KEY_CURRENT, null)
    }

    fun currentEngine(context: Context): SearchEngine {
        val list = engines(context)
        val id = context.getPrefString(KEY_CURRENT)
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun selectEngine(context: Context, id: String) {
        context.putPrefString(KEY_CURRENT, id)
    }

    /** 按引擎模板拼出搜索地址：{query} 为编码后的关键词，{queryPath} 为路径用编码 */
    fun buildSearchUrl(engine: SearchEngine, query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val path = encoded.replace("+", "%20")
        return engine.url
            .replace("{query}", encoded)
            .replace("{queryPath}", path)
    }
}
