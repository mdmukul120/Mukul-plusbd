package com.example.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadNotificationHelper.ACTION_CANCEL_DOWNLOAD) {
            val taskId = intent.getStringExtra(DownloadNotificationHelper.EXTRA_TASK_ID)
            if (!taskId.isNullOrEmpty()) {
                InAppDownloader.cancelDownload(taskId)
                DownloadNotificationHelper.cancelNotification(context, taskId)
            }
        }
    }
}
