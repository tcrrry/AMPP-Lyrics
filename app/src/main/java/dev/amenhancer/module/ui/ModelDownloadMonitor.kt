package dev.amenhancer.module.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.common.sdkinternal.SharedPrefManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.Locale

/** Observes this translation request's download IDs, never unrelated music downloads. */
internal class ModelDownloadMonitor(private val activity: Activity, codes: List<String>) {
    private val models = codes.filter { it != "en" }.distinct().map { TranslateRemoteModel.Builder(it).build() }
    private val handler = Handler(Looper.getMainLooper())
    private val started = SystemClock.elapsedRealtime()
    private var lastProgressAt = started
    private var previousBytes = -1L
    private var closed = false
    private val dialog = AlertDialog.Builder(activity)
        .setTitle("离线语言包下载")
        .setMessage("正在准备下载任务…")
        .setPositiveButton("关闭窗口", null)
        .create()
    private val poll = object : Runnable {
        override fun run() {
            if (closed || activity.isFinishing || activity.isDestroyed) {
                stop()
                return
            }
            val message = runCatching { status() }.getOrElse {
                "暂时无法读取系统下载状态：${it.message.orEmpty().take(100)}"
            }
            dialog.setMessage(message + "\n\n关闭窗口不会取消下载。")
            handler.postDelayed(this, 1_000L)
        }
    }

    fun start() {
        dialog.setOnDismissListener { stop() }
        dialog.show()
        handler.post(poll)
    }

    fun finish(message: String) {
        handler.removeCallbacks(poll)
        if (!closed && !activity.isFinishing && !activity.isDestroyed) dialog.setMessage(message)
    }

    private fun stop() {
        closed = true
        handler.removeCallbacks(poll)
    }

    private fun status(): String {
        val prefs = SharedPrefManager.getInstance(MlKitContext.getInstance())
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloaded = 0L
        var hasTask = false
        val rows = models.map { model ->
            val name = Locale.forLanguageTag(model.language).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE)
            val id = prefs.getDownloadingModelId(model)
            if (id == null) return@map "$name：等待任务建立或模型校验"
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "$name：系统中未找到任务 #$id"
                hasTask = true
                fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                val bytes = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0L)
                val total = number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                downloaded += bytes
                val state = number(DownloadManager.COLUMN_STATUS).toInt()
                val reason = number(DownloadManager.COLUMN_REASON).toInt()
                val detail = when (state) {
                    DownloadManager.STATUS_PENDING -> "系统排队中"
                    DownloadManager.STATUS_RUNNING -> "正在下载"
                    DownloadManager.STATUS_SUCCESSFUL -> "文件已下载，等待校验和试译"
                    DownloadManager.STATUS_FAILED -> "下载失败，系统原因 $reason"
                    DownloadManager.STATUS_PAUSED -> when (reason) {
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待可用网络"
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi"
                        DownloadManager.PAUSED_WAITING_TO_RETRY -> "连接失败，系统等待重试"
                        else -> "系统暂停，原因 $reason"
                    }
                    else -> "状态 $state，原因 $reason"
                }
                val size = String.format(Locale.ROOT, "%.1f", bytes / 1048576.0)
                val limit = if (total > 0) String.format(Locale.ROOT, " / %.1f MB", total / 1048576.0) else " MB"
                "$name：$detail\n$size$limit · 任务 #$id"
            } ?: "$name：无法读取系统任务"
        }
        val now = SystemClock.elapsedRealtime()
        if (downloaded != previousBytes) {
            previousBytes = downloaded
            lastProgressAt = now
        }
        val warning = if (now - lastProgressAt >= 60_000L) {
            if (hasTask) "\n\n已超过一分钟没有下载进展。请检查系统下载管理器能否联网；如使用按应用代理，系统下载服务也需要能访问模型服务器。"
            else "\n\n超过一分钟仍未检测到下载任务，可能停在初始化或模型校验阶段。请保留此状态供排查。"
        } else ""
        return rows.joinToString("\n\n") + "\n\n已等待 ${(now - started) / 1000} 秒" + warning
    }
}
