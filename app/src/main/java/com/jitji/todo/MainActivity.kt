package com.jitji.todo

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jitji.todo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableShowOnLockscreen()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = TaskAdapter(
            onToggle = { viewModel.toggleDone(it) },
            onClick = { openEdit(it.id) },
            onDelete = { confirmDelete(it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        viewModel.tasks.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonAdd.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submitNewTask(); true } else false
        }

        binding.buttonAdd.setOnClickListener { submitNewTask() }

        requestNotificationPermissionIfNeeded()
        ensureExactAlarmPermission()
        LockscreenService.start(this)
        ServiceWatchdog.scheduleHeartbeat(this)
        promptBatteryOptimizationIfNeeded()
        promptOverlayPermissionIfNeeded()
    }

    private fun promptOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(this)) return
        AlertDialog.Builder(this)
            .setTitle("다른 앱 위에 표시 권한")
            .setMessage(
                "전원 버튼으로 화면을 켤 때 앱이 자동으로 뜨려면 " +
                    "'다른 앱 위에 표시' 권한이 필요해요. 설정에서 허용해주세요."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    private fun promptRevertHomeIfNeeded() {
        val prefs = getSharedPreferences("jitji", MODE_PRIVATE)
        if (prefs.getBoolean("revert_home_prompted", false)) return
        prefs.edit().putBoolean("revert_home_prompted", true).apply()
        AlertDialog.Builder(this)
            .setTitle("홈 앱 되돌리기")
            .setMessage(
                "이전 버전에서 이 앱을 기본 홈 앱으로 설정했었습니다. " +
                    "이제 홈 런처 기능을 제거했으니 원래 쓰던 런처로 되돌려주세요.\n\n" +
                    "'설정 열기'를 누르면 홈 앱 선택 화면이 나옵니다."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure {
                        runCatching {
                            val i = Intent(Intent.ACTION_MAIN)
                            i.addCategory(Intent.CATEGORY_HOME)
                            startActivity(Intent.createChooser(i, "홈 앱 선택"))
                        }
                    }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        LockscreenService.start(this)
    }

    private fun enableShowOnLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                km.requestDismissKeyguard(this, null)
            }
        }
    }

    private fun submitNewTask() {
        val title = binding.editInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) return
        val task = Task(title = title)
        viewModel.save(task)
        binding.editInput.text?.clear()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editInput.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> { checkUpdate(); true }
            R.id.action_clear_done -> { viewModel.deleteCompleted(); true }
            R.id.action_battery_opt -> { openBatterySettings(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openBatterySettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }.onFailure {
            runCatching {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("'${task.title}'을(를) 삭제할까요?")
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(task) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openEdit(taskId: Long) {
        val intent = Intent(this, EditTaskActivity::class.java)
        intent.putExtra(EditTaskActivity.EXTRA_TASK_ID, taskId)
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkUpdate() {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.checking_update)
            .setCancelable(false)
            .create()
        progress.show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            progress.dismiss()
            result.onSuccess { info ->
                val current = UpdateChecker.currentVersion()
                if (info.versionCode <= current) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.latest_version, current),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@onSuccess
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.check_update)
                    .setMessage(
                        getString(R.string.update_available, info.versionCode) +
                            "\n\n" + info.body.take(400)
                    )
                    .setPositiveButton(R.string.download) { _, _ -> beginDownload(info) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }.onFailure { e ->
                Toast.makeText(
                    this@MainActivity,
                    "업데이트 확인 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun beginDownload(info: UpdateInfo) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }.onFailure { e ->
            Toast.makeText(this, "브라우저 열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
