package io.legado.app.ui.book.read.config

import android.content.Context
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
 * 正文长按条编辑：拖动调整动作顺序，勾选要显示的系统「文字处理」动作。
 */
class ContentSelectMenuConfigDialog : BaseDialogFragment(R.layout.dialog_content_select_menu_config) {

    private val binding by viewBinding(DialogContentSelectMenuConfigBinding::bind)
    private val actionAdapter by lazy { ActionAdapter(requireContext()) }
    private val processAdapter by lazy { ProcessAdapter(requireContext()) }
    private val enabledProcessText = hashMapOf<String, Boolean>()

    override fun onStart() {
        super.onStart()
        setLayout(0.92f, 0.8f)
    }

    override fun onFragmentCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        binding.root.setBackgroundColor(requireContext().backgroundColor)
        binding.tvActionsTitle.setTextColor(requireContext().primaryTextColor)
        binding.tvProcessTitle.setTextColor(requireContext().primaryTextColor)
        binding.tvCancel.setTextColor(requireContext().primaryTextColor)
        binding.tvOk.setTextColor(requireContext().accentColor)

        val context = requireContext()
        val savedOrder = ContentSelectMenuConfig.savedOrder(context)
        val actions = ContentSelectMenuConfig.builtInActions(context)
        val ordered = if (savedOrder.isEmpty()) {
            actions
        } else {
            val index = savedOrder.withIndex().associate { it.value to it.index }
            actions.sortedBy { index[it.name] ?: Int.MAX_VALUE }
        }
        val disabled = ContentSelectMenuConfig.disabledProcessText(context)
        ContentSelectMenuConfig.processTextActions(context).forEach {
            enabledProcessText[it.key] = it.key !in disabled
        }

        binding.recyclerActions.layoutManager = LinearLayoutManager(context)
        binding.recyclerActions.adapter = actionAdapter
        actionAdapter.setItems(ordered)
        val touchCallback = ItemTouchCallback(actionAdapter).apply { isCanDrag = true }
        ItemTouchHelper(touchCallback).attachToRecyclerView(binding.recyclerActions)

        binding.recyclerProcess.layoutManager = LinearLayoutManager(context)
        binding.recyclerProcess.adapter = processAdapter
        processAdapter.setItems(ContentSelectMenuConfig.processTextActions(context))

        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvOk.setOnClickListener {
            save(context)
            dismissAllowingStateLoss()
        }
    }

    private fun save(context: Context) {
        ContentSelectMenuConfig.saveOrder(context, actionAdapter.getItems().map { it.name })
        val disabled = processAdapter.getItems()
            .filter { enabledProcessText[it.key] == false }
            .map { it.key }
            .toSet()
        ContentSelectMenuConfig.saveDisabledProcessText(context, disabled)
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
            binding.tvDrag.setTextColor(context.primaryTextColor)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemContentSelectActionBinding) {
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            return true
        }
    }

    private inner class ProcessAdapter(context: Context) :
        RecyclerAdapter<ContentSelectMenuConfig.ProcessTextAction, ItemContentSelectProcessBinding>(context) {

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
            binding.cbEnable.isChecked = enabledProcessText[item.key] != false
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemContentSelectProcessBinding) {
            binding.cbEnable.setOnClickListener {
                val item = getItem(holder.layoutPosition) ?: return@setOnClickListener
                val checked = !(enabledProcessText[item.key] ?: true)
                enabledProcessText[item.key] = checked
                binding.cbEnable.isChecked = checked
            }
            binding.root.setOnClickListener {
                binding.cbEnable.performClick()
            }
        }
    }
}
