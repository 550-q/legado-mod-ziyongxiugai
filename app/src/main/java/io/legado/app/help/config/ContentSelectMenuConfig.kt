package io.legado.app.help.config

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getPrefStringSet
import io.legado.app.utils.putPrefString
import io.legado.app.utils.putPrefStringSet

/**
 * 正文长按条（文本选择菜单）的配置：动作顺序、系统「文字处理」动作的启用开关。
 *
 * 顺序按资源名保存（menu_copy、menu_aloud ……），这样换版本 id 变了也能对上。
 */
object ContentSelectMenuConfig {

    data class BuiltInAction(
        val name: String,
        val title: String,
        val itemId: Int
    )

    data class ProcessTextAction(
        val key: String,
        val label: String,
        val packageName: String,
        val activityName: String
    )

    fun builtInActions(context: Context): List<BuiltInAction> {
        val menu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, menu)
        return menu.visibleItems.map {
            BuiltInAction(
                name = context.resources.getResourceEntryName(it.itemId),
                title = it.title?.toString().orEmpty(),
                itemId = it.itemId
            )
        }
    }

    fun savedOrder(context: Context): List<String> {
        val raw = context.getPrefString(PreferKey.contentSelectActionsOrder).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun saveOrder(context: Context, names: List<String>) {
        context.putPrefString(PreferKey.contentSelectActionsOrder, names.joinToString(","))
    }

    fun processTextActions(context: Context): List<ProcessTextAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val intent = Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
        return context.packageManager.queryIntentActivities(intent, 0).map {
            val info = it.activityInfo
            ProcessTextAction(
                key = "${info.packageName}/${info.name}",
                label = it.loadLabel(context.packageManager).toString(),
                packageName = info.packageName,
                activityName = info.name
            )
        }.sortedBy { it.label }
    }

    /**
     * 默认全部启用，只有用户手动取消勾选的才记在「禁用」集合里。
     */
    fun disabledProcessText(context: Context): Set<String> =
        context.getPrefStringSet(PreferKey.contentSelectProcessTextDisabled).orEmpty()

    fun saveDisabledProcessText(context: Context, keys: Set<String>) {
        context.putPrefStringSet(PreferKey.contentSelectProcessTextDisabled, keys.toMutableSet())
    }
}
