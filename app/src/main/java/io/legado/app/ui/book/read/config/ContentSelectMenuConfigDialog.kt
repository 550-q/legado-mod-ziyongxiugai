package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogContentSelectMenuConfigBinding
import io.legado.app.databinding.ItemContentSelectActionBinding
import io.legado.app.databinding.ItemContentSelectProcessBinding
import io.legado.app.help.config.ContentSelectMenuConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 正文长按条编辑：内置动作与系统「文字处理」动作都能拖动排序、都能勾选开关，
 * 左下角「恢复默认」清掉自定义顺序与开关。
 */
class ContentSelectMenuConfigDialog : BaseDialogFragment(R.layout.dialog_content_select_menu_config) {

    private val binding by viewBinding(DialogContentSelectMenuConfigBinding::bind)
    private val actionAdapter by lazy { ActionAdapter(requireContext()) }
    private val processAdapter by lazy { ProcessAdapter(requireContext()) }
    private val enabledActions = hashMapOf<String, Boolean>()
    private val enabledProcessText = hashMapOf<String, Boolean>()

    override fun onStart() {
        super.onStart()
        setLayout(0.92f, 0.8f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.setBackgroundColor(requireContext().backgroundColor)
        binding.tvActionsTitle.setTextColor(requireContext().primaryTextColor)
        binding.tvProcessTitle.setTextColor(requireContext().primaryTextColor)
        binding.tvReset.setTextColor(requireContext().primaryTextColor)
        binding.tvCancel.setTextColor(requireContext().primaryTextColor)
        binding.tvOk.setTextColor(requireContext().accentColor)

        binding.recyclerActions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerActions.adapter = actionAdapter
        ItemTouchHelper(ItemTouchCallback(actionAdapter).apply { isCanDrag = true })
            .attachToRecyclerView(binding.recyclerActions)

        binding.recyclerProcess.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProcess.adapter = processAdapter
        ItemTouchHelper(ItemTouchCallback(processAdapter).apply { isCanDrag = true })
            .attachToRecyclerView(binding.recyclerProcess)

        loadConfig()

        binding.tvReset.setOnClickListener {
            ContentSelectMenuConfig.resetToDefault(requireContext())
            loadConfig()
            (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
        }
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvOk.setOnClickListener {
            save(requireContext())
            dismissAllowingStateLoss()
        }
    }

    private fun loadConfig() {
        val context = requireContext()
        val savedOrder = ContentSelectMenuConfig.savedOrder(context)
        val actions = ContentSelectMenuConfig.builtInActions(context)
        val ordered = if (savedOrder.isEmpty()) {
            actions
        } else {
            val index = savedOrder.withIndex().associate { it.value to it.index }
            actions.sortedBy { index[it.name] ?: Int.MAX_VALUE }
        }
        val disabledActions = ContentSelectMenuConfig.disabledBuiltIn(context)
        enabledActions.clear()
        ordered.forEach { enabledActions[it.name] = it.name !in disabledActions }

        val processActions = ContentSelectMenuConfig.processTextActions(context)
        val processOrder = ContentSelectMenuConfig.processTextOrder(context)
        val orderedProcess = if (processOrder.isEmpty()) {
            processActions
        } else {
            val index = processOrder.withIndex().associate { it.value to it.index }
            processActions.sortedBy { index[it.key] ?: Int.MAX_VALUE }
        }
        val disabled = ContentSelectMenuConfig.disabledProcessText(context)
        enabledProcessText.clear()
        orderedProcess.forEach {
            enabledProcessText[it.key] = it.key !in disabled
        }

        actionAdapter.setItems(ordered)
        processAdapter.setItems(orderedProcess)
    }

    private fun save(context: Context) {
        ContentSelectMenuConfig.saveOrder(context, actionAdapter.getItems().map { it.name })
        ContentSelectMenuConfig.saveDisabledBuiltIn(
            context,
            actionAdapter.getItems()
                .filter { enabledActions[it.name] == false }
                .map { it.name }
                .toSet()
        )
        ContentSelectMenuConfig.saveProcessTextOrder(
            context,
            processAdapter.getItems().map { it.key }
        )
        ContentSelectMenuConfig.saveDisabledProcessText(
            context,
            processAdapter.getItems()
                .filter { enabledProcessText[it.key] == false }
                .map { it.key }
                .toSet()
        )
        (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
    }

    private inner class ActionAdapter(context: Context) :
        RecyclerAdapter<ContentSelectMenuConfig.BuiltInAction, ItemContentSelectActionBinding>(context),
        ItemTouchCallback.Callback {

        override fun getViewBinding(parent: ViewGroup): ItemContentSelectActionBinding =
            ItemContentSelectActionBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemContentSelectActionBinding,
            item: ContentSelectMenuConfig.BuiltInAction,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.title
            binding.tvTitle.setTextColor(context.primaryTextColor)
            binding.tvTitle.alpha = if (enabledActions[item.name] != false) 1f else 0.4f
            binding.tvDrag.setTextColor(context.primaryTextColor)
            binding.cbEnable.isChecked = enabledActions[item.name] != false
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemContentSelectActionBinding) {
            binding.cbEnable.setOnClickListener {
                val item = getItem(holder.layoutPosition) ?: return@setOnClickListener
                val checked = !(enabledActions[item.name] ?: true)
                enabledActions[item.name] = checked
                notifyItemChanged(holder.layoutPosition)
            }
            binding.root.setOnClickListener {
                binding.cbEnable.performClick()
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            return true
        }
    }

    private inner class ProcessAdapter(context: Context) :
        RecyclerAdapter<ContentSelectMenuConfig.ProcessTextAction, ItemContentSelectProcessBinding>(context),
        ItemTouchCallback.Callback {

        override fun getViewBinding(parent: ViewGroup): ItemContentSelectProcessBinding =
            ItemContentSelectProcessBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemContentSelectProcessBinding,
            item: ContentSelectMenuConfig.ProcessTextAction,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.label
            binding.tvTitle.setTextColor(context.primaryTextColor)
            binding.tvTitle.alpha = if (enabledProcessText[item.key] != false) 1f else 0.4f
            binding.tvDrag.setTextColor(context.primaryTextColor)
            binding.cbEnable.isChecked = enabledProcessText[item.key] != false
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemContentSelectProcessBinding) {
            binding.cbEnable.setOnClickListener {
                val item = getItem(holder.layoutPosition) ?: return@setOnClickListener
                val checked = !(enabledProcessText[item.key] ?: true)
                enabledProcessText[item.key] = checked
                notifyItemChanged(holder.layoutPosition)
            }
            binding.root.setOnClickListener {
                binding.cbEnable.performClick()
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            return true
        }
    }
}
