package com.example.masjiddisplay

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit


data class PrayerTimings(
    @Json(name = "Fajr") val fajr: String,
    @Json(name = "Dhuhr") val dhuhr: String,
    @Json(name = "Asr") val asr: String,
    @Json(name = "Maghrib") val maghreb: String,
    @Json(name = "Isha") val isha: String
)

data class DataWrapper(
    @Json(name = "timings") val timings: PrayerTimings
)

data class ApiResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: DataWrapper
)

var timingsList = mutableMapOf("a" to "1")

var iqamaTimeMap = mutableMapOf("fajr" to 25, "dhuhr" to 20, "asr" to 20, "maghreb" to 13, "isha" to 15)

class MidnightTVWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        val url =
            "https://api.aladhan.com/v1/timingsByCity/04-02-2025?city=Amman&country=Jordan&method=2"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        // Execute HTTP request
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
            Log.e("FetchTimings", "Failed to fetch prayer times: ${response.message}")

        }

        // Parse JSON using Moshi
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val jsonAdapter = moshi.adapter(ApiResponse::class.java)

        val apiResponse = responseBody?.let { jsonAdapter.fromJson(it) }

        // Extract and print prayer times
        apiResponse?.data?.timings?.let { timings ->
            timingsList["isha"] = timings.isha
            timingsList["maghreb"] = timings.maghreb
            timingsList["asr"] = timings.asr
            timingsList["dhuhr"] = timings.dhuhr
            timingsList["fajr"] = timings.fajr

        } ?: Log.e("Fetchtimings", "Failed to parse prayer times")
        Log.i(
            "FetchTimings", timingsList.toString()
        )
        return Result.success()
    }
}

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateinterval: Long = 10000
    private lateinit var imageView: ImageView

    private val updateImageRunnable = object : Runnable {
        override fun run() {
            imageView.setImageResource(getImageForTimeOfDay())
            handler.postDelayed(this, updateinterval)
        }
    }

    // Function to fetch prayer times from the API
    suspend fun fetchPrayerTimes() {
        var job = CoroutineScope(Dispatchers.IO).launch {
            val url =
                "https://api.aladhan.com/v1/timingsByCity/04-02-2025?city=Amman&country=Jordan&method=2"

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            // Execute HTTP request
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                Log.e("GetImageFun", "Failed to fetch prayer times: ${response.message}")

            }

            // Parse JSON using Moshi
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(ApiResponse::class.java)

            val apiResponse = jsonAdapter.fromJson(responseBody)

            // Extract and print prayer times
            apiResponse?.data?.timings?.let { timings ->
                timingsList["isha"] = timings.isha
                timingsList["maghreb"] = timings.maghreb
                timingsList["asr"] = timings.asr
                timingsList["dhuhr"] = timings.dhuhr
                timingsList["fajr"] = timings.fajr

            } ?: Log.e("GetImageFun", "Failed to parse prayer times")
        }
        job.join()
        Log.i("FetchTimings", timingsList.toString())

    }

    override fun onStart() {
        super.onStart()
        // App is opened or brought to the foreground
        lifecycleScope.launch {
            fetchPrayerTimes()

        }
    }

    override fun onResume() {
        super.onResume()
        // App is in the foreground (active)
        lifecycleScope.launch {
            fetchPrayerTimes()

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleMidnightWork(this)
        setContentView(R.layout.activity_main)
        lifecycleScope.launch {
            imageView = findViewById(R.id.imageView)
            imageView.setImageResource(getImageForTimeOfDay())
            fetchPrayerTimes()

        }

        handler.post(updateImageRunnable)
    }

    @SuppressLint("DefaultLocale")
    private fun getImageForTimeOfDay(): Int {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        for ((key, value) in timingsList) {

            if (key == "a") continue

            val afterPrayerTime = compareTimes(
                String.format("%02d:%02d", hour, minute),
                value.orEmpty()
            ) >= 0
            val beforeIqamaTime = compareTimes(
                    String.format(
                        "%02d:%02d",
                        hour,
                        minute
                    )
                , addMinutesToTimestamp(value.orEmpty(), iqamaTimeMap[key]!!)
            ) < 0


            val afterprayerTime =  compareTimes(
                String.format(
                    "%02d:%02d",
                    hour,
                    minute
                )
                , addMinutesToTimestamp(addMinutesToTimestamp(value.orEmpty(), iqamaTimeMap[key]!!), 10)
            )  >= 0

            val endTime =  compareTimes(
                String.format(
                    "%02d:%02d",
                    hour,
                    minute
                )
                , addMinutesToTimestamp(addMinutesToTimestamp(value.orEmpty(), iqamaTimeMap[key]!!), 20)
            )  < 0

            if ((afterPrayerTime && beforeIqamaTime) || (afterprayerTime && endTime)) {
                return when (key) {
                    "isha" -> R.drawable.isha
                    "maghreb" -> R.drawable.maghreb
                    "asr" -> R.drawable.asr
                    "dhuhr" -> R.drawable.dhuhr
                    "fajr" -> R.drawable.fajr
                    else -> R.drawable.black
                }
            }
        }
        return R.drawable.black
    }

    fun compareTimes(time1: String, time2: String): Int {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time1Parsed = formatter.parse(time1)
        val time2Parsed = formatter.parse(time2)

        if (time1Parsed != null) {
            return time1Parsed.compareTo(time2Parsed)
        } else return 0
    }


    fun scheduleMidnightWork(context: Context) {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (now.after(this)) { // If past midnight, schedule for the next day
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = midnight.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<MidnightTVWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS) // Start exactly at 12 AM
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)  // Avoid running on low battery
                    .setRequiresCharging(false)      // Runs even if not charging
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "MidnightTVTask",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun addMinutesToTimestamp(timestamp: String, minutesToAdd: Int): String {
        val parts = timestamp.split(":").map { it.toInt() }
        val initialMinutes = parts[0] * 60 + parts[1] // Convert HH:MM to total minutes
        val newMinutes = initialMinutes + minutesToAdd // Add the given minutes

        val newHours = (newMinutes / 60) % 24 // Ensure it wraps around 24 hours if needed
        val newMins = newMinutes % 60
        return String.format("%02d:%02d", newHours, newMins)
    }

}
