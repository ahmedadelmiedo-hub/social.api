package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlMalafWorkManagerHelper {

    fun scheduleAutonomousWork(context: Context, runImmediately: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule periodic work every 6 hours
        val periodicRequest = PeriodicWorkRequestBuilder<AlMalafAutonomousWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlMalafAutonomousWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        // Optionally run immediately once to give instant feedback
        if (runImmediately) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<AlMalafAutonomousWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
    }

    fun cancelAutonomousWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AlMalafAutonomousWorker.WORK_NAME)
    }

    fun isWorkScheduled(context: Context, onResult: (Boolean) -> Unit) {
        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(AlMalafAutonomousWorker.WORK_NAME)
        try {
            val infos = workInfos.get()
            val isEnqueued = infos.any { !it.state.isFinished }
            onResult(isEnqueued)
        } catch (_: Exception) {
            onResult(false)
        }
    }
}
