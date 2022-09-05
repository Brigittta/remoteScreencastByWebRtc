package com.brigitttta.remote_screencast

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.brigitttta.remote_screencast.pull.PullChooseActivity
import com.brigitttta.remote_screencast.push.PushService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val context = this


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        StrictMode.setThreadPolicy(ThreadPolicy.Builder()
//                .detectDiskReads()
//                .detectDiskWrites()
//                .detectNetwork() // or .detectAll() for all detectable problems
//                .penaltyLog()
//                .build())
//        StrictMode.setVmPolicy(VmPolicy.Builder()
//                .detectLeakedSqlLiteObjects()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .penaltyDeath()
//                .build())

        val pushStart = findViewById<Button>(R.id.btn_push_start)
        val pushStop = findViewById<Button>(R.id.btn_push_stop)
        val pull = findViewById<Button>(R.id.btn_pull)

        val registerMediaProjectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                PushService.start(this, it.data)
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "开始运行投屏服务并等待连接", Toast.LENGTH_LONG).show()
                }
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "需要媒体投影许可才可使用", Toast.LENGTH_LONG).show()
                }
            }
        }

        pushStart.setOnClickListener {
            val mediaProjectionManager = getSystemService<MediaProjectionManager>()
            registerMediaProjectionPermission.launch(mediaProjectionManager?.createScreenCaptureIntent())
        }
        pushStop.setOnClickListener {
            PushService.stop(this)
        }
        pull.setOnClickListener {
            startActivity(Intent(this, PullChooseActivity::class.java))
        }

        PushService.state.observe(this) {
            when (it) {
                0 -> {
                    pushStart.visibility = View.VISIBLE
                    pushStop.visibility = View.GONE
                    pull.visibility = View.VISIBLE
                }
                1 -> {
                    pushStart.visibility = View.GONE
                    pushStop.visibility = View.VISIBLE
                    pull.visibility = View.GONE
                }
            }
        }
    }
}