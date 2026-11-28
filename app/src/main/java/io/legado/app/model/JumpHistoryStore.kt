package io.legado.app.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 在阅读界面之外产生的「跳转前位置」暂存区。
 *
 * 从书籍详情点开目录选章时阅读界面还没启动，跳转前的进度只存在于书籍记录里，
 * 这里先把它存下来，等阅读界面启动后并入 [ReadBook.jumpHistory]。
 */
object JumpHistoryStore {

    private const val KEY_PENDING_JUMP_HISTORY = "pendingJumpHistory"

    private val gson = Gson()

    fun addPending(record: ReadJumpRecord) {
        val list = allPending().toMutableList()
        list.removeAll {
            it.bookUrl == record.bookUrl && it.chapterIndex == record.chapterIndex &&
                    it.chapterPos == record.chapterPos
        }
        list.add(0, record)
        while (list.size > 30) list.removeAt(list.lastIndex)
        save(list)
    }

    /** 取出某本书暂存的记录（取走后从暂存区删除，避免重复并入） */
    fun takePending(bookUrl: String): List<ReadJumpRecord> {
        val list = allPending()
        val mine = list.filter { it.bookUrl == bookUrl }
        if (mine.isEmpty()) return emptyList()
        save(list.filterNot { it.bookUrl == bookUrl })
        return mine
    }

    fun clear() {
        appCtx.putPrefString(KEY_PENDING_JUMP_HISTORY, null)
    }

    private fun allPending(): List<ReadJumpRecord> {
        val raw = appCtx.getPrefString(KEY_PENDING_JUMP_HISTORY).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<Array<ReadJumpRecord>>(
                raw, object : TypeToken<Array<ReadJumpRecord>>() {}.type
            )?.toList()
        }.getOrNull().orEmpty()
    }

    private fun save(list: List<ReadJumpRecord>) {
        appCtx.putPrefString(
            KEY_PENDING_JUMP_HISTORY,
            if (list.isEmpty()) null else gson.toJson(list)
        )
    }
}
