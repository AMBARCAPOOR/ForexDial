package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.YieldClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class YieldSpreadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val result = YieldClient.getYieldSpread()

            val request = PutDataMapRequest.create(Constants.PATH_YIELD).apply {
                dataMap.putString(Constants.WKEY_SENTIMENT,    result.direction)
                dataMap.putFloat(Constants.WKEY_YIELD_SPREAD,  result.spread.toFloat())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(applicationContext).putDataItem(request).await()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
