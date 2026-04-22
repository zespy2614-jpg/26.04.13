package com.jitji.todo

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jitji.todo.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onToggle: (Task) -> Unit,
    private val onClick: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.VH>(DIFF) {

    private val formatter = SimpleDateFormat("MM/dd(E) HH:mm", Locale.KOREAN)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTaskBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.checkDone.setOnCheckedChangeListener(null)
            binding.checkDone.isChecked = task.isDone
            binding.textTitle.text = task.title
            if (task.isDone) {
                binding.textTitle.paintFlags =
                    binding.textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textTitle.alpha = 0.45f
            } else {
                binding.textTitle.paintFlags =
                    binding.textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.textTitle.alpha = 1f
            }

            if (task.memo.isNotBlank()) {
                binding.textMemo.visibility = View.VISIBLE
                binding.textMemo.text = task.memo
            } else {
                binding.textMemo.visibility = View.GONE
            }

            val due = task.dueAt
            if (due != null) {
                binding.textDue.visibility = View.VISIBLE
                binding.textDue.text = "⏰ " + formatter.format(Date(due))
                val overdue = !task.isDone && due < System.currentTimeMillis()
                binding.textDue.setTextColor(
                    binding.root.context.getColor(
                        if (overdue) R.color.red else R.color.muted
                    )
                )
            } else {
                binding.textDue.visibility = View.GONE
            }

            binding.checkDone.setOnCheckedChangeListener { _, _ -> onToggle(task) }
            binding.root.setOnClickListener { onClick(task) }
            binding.buttonDelete.setOnClickListener { onDelete(task) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(old: Task, new: Task) = old.id == new.id
            override fun areContentsTheSame(old: Task, new: Task) = old == new
        }
    }
}
