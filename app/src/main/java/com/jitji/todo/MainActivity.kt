package com.jitji.todo

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jitji.todo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = TaskAdapter(
            onToggle = { task -> viewModel.toggleDone(task) },
            onClick = { task -> openEdit(task.id) },
            onDelete = { task -> confirmDelete(task) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        viewModel.tasks.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener { openEdit(0L) }

        requestNotificationPermissionIfNeeded()
        ensureExactAlarmPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_done -> {
                viewModel.deleteCompleted()
                Snackbar.make(binding.root, "완료된 할일을 정리했어요", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("'${task.title}'을(를) 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> viewModel.delete(task) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun openEdit(taskId: Long) {
        val intent = Intent(this, EditTaskActivity::class.java)
        intent.putExtra(EditTaskActivity.EXTRA_TASK_ID, taskId)
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) return
        AlertDialog.Builder(this)
            .setTitle("정확한 알림 허용")
            .setMessage("설정한 시간에 정확히 알려드리려면 '정확한 알람' 권한이 필요해요.")
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }
}
