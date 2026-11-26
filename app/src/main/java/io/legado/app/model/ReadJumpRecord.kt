package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress

/**
 * 一次“跳转前阅读位置”的记录。
 *
 * 目录、全文搜索、进度条等会改变阅读位置的操作，在跳转前把当前页记录到
 * [ReadBook.jumpHistory]；返回书架时用列表让用户选择恢复到哪一页。
 */
data class ReadJumpRecord(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterPos: Int,
    val chapterTitle: String,
    val recordTime: Long,
    val chapterCount: Int = 0,
    val pageIndex: Int = -1,
    val pageCount: Int = -1,
    val snippet: String = ""
) {

    fun toBookProgress(book: Book) = BookProgress(
        name = book.name,
        author = book.author,
        durChapterIndex = chapterIndex,
        durChapterPos = chapterPos,
        durChapterTime = recordTime,
        durChapterTitle = chapterTitle
    )
}

/**
 * 阅读页向模型层提供的“当前页”快照。
 * 直接阅读 EPUB / 直接文本模式和传统分页模式的页码来源不同，由阅读界面负责取值。
 */
data class ReadPageSnapshot(
    val chapterIndex: Int,
    val chapterPos: Int,
    val chapterTitle: String,
    val chapterCount: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val snippet: String
)

/**
 * 取一段正文的第一句话：跳过开头空白，截到第一个句末符号（含收尾引号）。
 * 结果最多 [maxLength] 个字符，超出部分用省略号收尾。
 */
fun firstSentenceOfPageText(text: CharSequence?, maxLength: Int = 60): String {
    if (text.isNullOrBlank()) return ""
    val raw = text.toString()
    var start = 0
    while (start < raw.length && raw[start].isWhitespace()) start++
    if (start >= raw.length) return ""
    val boundaries = "。！？!?；;…"
    var end = raw.length
    for (index in start until raw.length) {
        val char = raw[index]
        if (char == '\n' || char == '\r') {
            end = index
            break
        }
        if (char in boundaries) {
            end = index + 1
            break
        }
    }
    while (end < raw.length && raw[end] in "”’」』）)】]") end++
    var sentence = raw.substring(start, end)
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
    if (sentence.length > maxLength) {
        sentence = sentence.take(maxLength).trimEnd() + "…"
    }
    return sentence
}
