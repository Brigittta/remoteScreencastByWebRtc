package com.brigitttta.remote_screencast.pull

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.brigitttta.remote_screencast.R
import com.brigitttta.remote_screencast.bean.ServiceDevice
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class PullChooseActivity : AppCompatActivity() {
    private val datagramSocket = DatagramSocket(9999)
    private val devices = mutableSetOf<ServiceDevice>()
    private val serviceDeviceAdapter = ServiceDeviceAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pull_choose)
        val rv = findViewById<RecyclerView>(R.id.rv)
        val fab_refresh = findViewById<ExtendedFloatingActionButton>(R.id.fab_refresh)


        rv.adapter = serviceDeviceAdapter
        serviceDeviceAdapter.setOnClickListener {
            PullActivity.start(this, it.remoteHost, 8888)
            finish()
        }
        fab_refresh.setOnClickListener {
            discovery()
        }

        receiveWebcast()
    }

    override fun onStart() {
        super.onStart()
        discovery()
    }


    private fun receiveWebcast() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val byteArray = ByteArray(1024)
                    val packet = DatagramPacket(byteArray, 0, 1024)
                    datagramSocket.receive(packet)
                    val buffer = ByteBuffer.wrap(byteArray)
                    val flag = buffer.getInt()
                    when (flag) {
                        //2.响应“查询局域网内设备包”
                        0x45854523 -> {
                            val address = packet.socketAddress as InetSocketAddress
                            devices.remove(ServiceDevice(address.hostName, "", ""))
                            devices.add(ServiceDevice(address.hostName, "", ""))
                            withContext(Dispatchers.Main) {
                                serviceDeviceAdapter.observe(devices)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun discovery() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = ByteBuffer.allocate(1024)
                    .putInt(0x45854522)
                    .flip()
                    .array() as ByteArray

            //TODO 广播这里有问题 需要其他解决方案
            datagramSocket.send(DatagramPacket(bytes, 0, 1024, InetAddress.getByName("192.168.8.101"), 9999))
            datagramSocket.send(DatagramPacket(bytes, 0, 1024, InetAddress.getByName("192.168.8.102"), 9999))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        datagramSocket.close()
    }

}

