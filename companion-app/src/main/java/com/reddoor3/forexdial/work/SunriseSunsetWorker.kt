package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.SunriseSunsetClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SunriseSunsetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sun = SunriseSunsetClient.getSunData()

            val request = PutDataMapRequest.create(Constants.PATH_SUN).apply {
                dataMap.putLong(Constants.WKEY_SUNRISE, sun.sunriseEpoch)
                dataMap.putLong(Constants.WKEY_SUNSET,  sun.sunsetEpoch)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(applicationContext).putDataItem(request).await()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
