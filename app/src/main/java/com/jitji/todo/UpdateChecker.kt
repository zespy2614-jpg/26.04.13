package com.jitji.todo

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val assetName: String,
    val downloadUrl: String,
    val releaseName: String,
    val body: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private val VERSION_FROM_NAME = Regex("""JitjiTodo-v(\d+)\.apk""")

    suspend fun fetchLatest(): Result<UpdateInfo> = runCatching {
        val url = URL(
            "https://api.github.com/repos/" +
                "${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/releases?per_page=10"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "JitjiTodo-Updater")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            error("HTTP $code: $err")
        }

        val body = conn.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
        conn.disconnect()

        val releases = JSONArray(body)
        var best: UpdateInfo? = null
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                val match = VERSION_FROM_NAME.matchEntire(name) ?: continue
                val v = match.groupValues[1].toIntOrNull() ?: continue
                if (best == null || v > best.versionCode) {
                    best = UpdateInfo(
                        versionCode = v,
                        assetName = name,
                        downloadUrl = downloadUrl,
                        releaseName = rel.optString("name", rel.optString("tag_name")),
                        body = rel.optString("body").orEmpty()
                    )
                }
            }
        }
        best ?: error("릴리스에 APK 에셋이 없습니다.")
    }

    fun currentVersion(): Int = BuildConfig.VERSION_CODE

    fun startDownload(
        context: Context,
        info: UpdateInfo,
        onDownloaded: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val dir = File(context.getExternalFilesDir(null), "updates")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, info.assetName)
        if (dest.exists()) dest.delete()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("잊지마 할일 업데이트")
            .setDescription("v${info.versionCode} 다운로드 중…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(dest))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val enqueueId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != enqueueId) return
                ctx.applicationContext.unregisterReceiver(this)
                val query = DownloadManager.Query().setFilterById(enqueueId)
                val cursor: Cursor? = dm.query(query)
                cursor?.use {
                    if (!it.moveToFirst()) {
                        onError("다운로드 상태를 확인하지 못했어요.")
                        return
                    }
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        onDownloaded(dest)
                    } else {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        onError("다운로드 실패 (status=$status, reason=$reason)")
                    }
                } ?: onError("다운로드 상태를 확인하지 못했어요.")
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canInstallPackages(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }
}
