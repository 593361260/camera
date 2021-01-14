package com.mingo.runplugin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.PREVIEW_SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO
                    ),
                    0
                )
            }
        }
        findViewById<View>(R.id.tvStart).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    CaptureVideoActivity::class.java
                ).putExtra(
                    "EXTRA_DATA_FILE_NAME",
                    getExternalFilesDir("")!!.absolutePath + "/${System.currentTimeMillis()}.mp4"
                )
            )
        }
    }
}