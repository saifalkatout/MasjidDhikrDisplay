package com.example.masjiddisplay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateinterval: Long = 10000
    private lateinit var imageView: ImageView

    private val updateImageRunnable = object: Runnable {
        override fun run(){
            imageView.setImageResource(getImageForTimeOfDay())

            handler.postDelayed(this, updateinterval)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        imageView.setImageResource(getImageForTimeOfDay())
        handler.post(updateImageRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateImageRunnable)
    }

    private fun getImageForTimeOfDay(): Int {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.SECOND)
        Log.i("GetImageFun","The seconds are: " + minute)
        if(minute >= 10) return R.drawable.morning
        return R.drawable.evening
    }
}
