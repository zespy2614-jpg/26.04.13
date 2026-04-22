package com.jitji.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jitji.todo.databinding.ActivityEditTaskBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTaskBinding
    private val viewModel: TaskViewModel by viewModels()
    private var editingId: Long = 0L
    private var dueAt: Long? = null
    private var isDone: Boolean = false
    private var createdAt: Long = System.currentTimeMillis()

    private val dueFormatter = SimpleDateFormat("yyyy/MM/dd(E) HH:mm", Locale.KOREAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editingId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        supportActionBar?.title = if (editingId == 0L) "새 할일" else "할일 수정"

        binding.buttonPickDue.setOnClickListener { pickDateTime() }
        binding.buttonClearDue.setOnClickListener {
            dueAt = null
            refreshDueLabel()
        }
        binding.buttonSave.setOnClickListener { save() }

        if (editingId != 0L) loadExisting()
        refreshDueLabel()
    }

    private fun loadExisting() {
        lifecycleScope.launch {
            val task = withContext(Dispatchers.IO) {
                TaskRepository(applicationContext).find(editingId)
            } ?: run { finish(); return@launch }
            binding.editTitle.setText(task.title)
            binding.editMemo.setText(task.memo)
            dueAt = task.dueAt
            isDone = task.isDone
            createdAt = task.createdAt
            refreshDueLabel()
        }
    }

    private fun pickDateTime() {
        val cal = Calendar.getInstance()
        dueAt?.let { cal.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val timeCal = Calendar.getInstance()
                dueAt?.let { timeCal.timeInMillis = it }
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val result = Calendar.getInstance()
                        result.set(y, m, d, hour, minute, 0)
                        result.set(Calendar.MILLISECOND, 0)
                        dueAt = result.timeInMillis
                        refreshDueLabel()
                    },
                    timeCal.get(Calendar.HOUR_OF_DAY),
                    timeCal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun refreshDueLabel() {
        val due = dueAt
        if (due == null) {
            binding.textDue.text = "알림 없음"
            binding.buttonClearDue.visibility = View.GONE
        } else {
            binding.textDue.text = dueFormatter.format(Date(due))
            binding.buttonClearDue.visibility = View.VISIBLE
        }
    }

    private fun save() {
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.editTitle.error = "제목을 입력하세요"
            return
        }
        val memo = binding.editMemo.text?.toString()?.trim().orEmpty()
        val task = Task(
            id = editingId,
            title = title,
            memo = memo,
            dueAt = dueAt,
            isDone = isDone,
            createdAt = createdAt
        )
        viewModel.save(task) { runOnUiThread { finish() } }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
