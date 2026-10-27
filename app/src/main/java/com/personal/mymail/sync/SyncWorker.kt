package com.personal.mymail.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.mymail.data.MailRepository

/** Runs in the background on a schedule: downloads new mail and notifies about it. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = MailRepository(applicationContext).refreshAll()
        Notifier.notifyNew(applicationContext, result.newMail)
        // Problems (no signal, wrong password) are simply retried at the next scheduled check.
        return Result.success()
    }
}
