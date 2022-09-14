package com.brigitttta.remote_screencast.push

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjection
import android.os.IBinder
import android.os.SystemClock
import android.util.Size
import android.view.InputEvent
import android.view.MotionEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import com.blankj.utilcode.util.ScreenUtils
import com.brigitttta.remote_screencast.R
import com.brigitttta.remote_screencast.bean.WebrtcMessage
import com.brigitttta.remote_screencast.webrtc.SimplePeerConnectionObserver
import com.brigitttta.remote_screencast.webrtc.SimpleSdpObserver
import com.brigitttta.remote_screencast.webrtc.Utils
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer

class PushService : Service() {
    private val mainScope = MainScope()

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val screenHeight = ScreenUtils.getScreenHeight()
    private val screenWidth = ScreenUtils.getScreenWidth()

    // 通过反射获取注入[触摸事件]需要的类,方法与变量
    private val inputManagerClass = Class.forName("android.hardware.input.InputManager")
    private val inputManagerInstance = inputManagerClass.getDeclaredMethod("getInstance")
    private val inputManager = inputManagerInstance.invoke(null) as android.hardware.input.InputManager
    private val injectInputEvent = inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

    // EglBase
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private val peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .createPeerConnectionFactory()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("surface_texture_thread", eglBaseContext, true)
    private val videoSource = peerConnectionFactory.createVideoSource(true, true)
    private val videoTrack = peerConnectionFactory.createVideoTrack("local_video_track", videoSource)

    private val datagramSocket = DatagramSocket(9999)


    override fun onCreate() {
        super.onCreate()
        registerWebcast()
        registerReceiver(stopPushBroadcastReceiver, IntentFilter(stopPushBroadcastAction))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mediaProjectionPermissionResultData = intent?.getParcelableExtra<Intent?>(MediaProjectionPermissionResultData)
        if (mediaProjectionPermissionResultData == null) {
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }
        createMediaProjectionForegroundServiceType()
        ScreenCapturerAndroid(mediaProjectionPermissionResultData, object : MediaProjection.Callback() {
            override fun onStop() {
            }
        }).apply {
            initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
            startCapture(screenWidth, screenHeight, 0)
        }
        startServer()
        state.value = 1
        return super.onStartCommand(intent, flags, startId)
    }

    private fun registerWebcast() {
        mainScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val byteArray = ByteArray(1024)
                    val packet = DatagramPacket(byteArray, 0, 1024)
                    datagramSocket.receive(packet)
                    val buffer = ByteBuffer.wrap(byteArray)
                    val flag = buffer.getInt()
                    when (flag) {
                        //2.响应“查询局域网内设备包”
                        0x45854522 -> {
                            val bytes = ByteBuffer.allocate(1024)
                                    .putInt(0x45854523)
                                    .flip()
                                    .array() as ByteArray
                            datagramSocket.send(DatagramPacket(bytes, 0, 1024, packet.socketAddress))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun startServer() {
        mainScope.launch(Dispatchers.IO) {
            try {
                ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel::class.java)
                        .childHandler(object : ChannelInitializer<SocketChannel>() {
                            override fun initChannel(channel: SocketChannel) {
                                channel.pipeline()
                                        .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                                        .addLast(LengthFieldPrepender(4))
                                        .addLast(StringDecoder(CharsetUtil.UTF_8))
                                        .addLast(StringEncoder(CharsetUtil.UTF_8))
                                        .addLast(object : SimpleChannelInboundHandler<String>() {
                                            private var peerConnection: PeerConnection? = null

                                            private var downTime = 0L

                                            override fun channelActive(ctx: ChannelHandlerContext) {
                                                createOffer(ctx)
                                            }

                                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                                disposeOffer(ctx)
                                            }

                                            override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                                handlerMsg(ctx, msg)
                                            }

                                            private fun createOffer(ctx: ChannelHandlerContext) {
                                                //发送设备尺寸
                                                ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SIZE, size = Size(screenWidth, screenHeight)).toString())

                                                //RTC配置
                                                val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                                                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                                                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                                    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                                    keyType = PeerConnection.KeyType.ECDSA
                                                }

                                                //创建对等连接
                                                peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("push") {
                                                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                                                        //发送 连接两端的主机的网络地址
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
                                                    }
                                                })

                                                peerConnection?.addTrack(videoTrack)
                                                peerConnection?.createOffer(object : SimpleSdpObserver("push-createOffer") {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        var localSdp = description
                                                        localSdp = Utils.preferCodecs(localSdp, true)
                                                        localSdp = Utils.preferCodecs(localSdp, false)
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver("push-setLocalDescription"), localSdp)
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = localSdp).toString())
                                                    }
                                                }, MediaConstraints())
                                            }

                                            private fun disposeOffer(ctx: ChannelHandlerContext) {
                                                peerConnection?.dispose()
                                                peerConnection = null
                                            }

                                            private fun handlerMsg(ctx: ChannelHandlerContext, msg: String) {
                                                val webrtcMessage = WebrtcMessage(msg)

                                                when (webrtcMessage.type) {
                                                    WebrtcMessage.Type.SDP -> {
                                                        peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), webrtcMessage.description)
                                                    }
                                                    WebrtcMessage.Type.ICE -> {
                                                        peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                                    }
                                                    WebrtcMessage.Type.MOVE -> {
                                                        // 注入触摸事件
                                                        runCatching {
                                                            val model = webrtcMessage.motionModel ?: return
                                                            // 处理时钟差异导致的ANR
                                                            val now = SystemClock.uptimeMillis()
                                                            if (model.action == MotionEvent.ACTION_DOWN) {
                                                                downTime = now
                                                            }
                                                            model.eventTime = now
                                                            model.downTime = downTime
                                                            model.scaleByScreen(screenHeight, screenWidth)
                                                            val event = model.toMotionEvent()
                                                            injectInputEvent.invoke(inputManager, event, 0)
                                                        }.onFailure {
                                                            it.printStackTrace()
                                                        }
                                                    }
                                                    else -> {}
                                                }
                                            }

                                        })
                            }
                        }).bind(8888).sync().channel().closeFuture().sync()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }
    }

    private fun createMediaProjectionForegroundServiceType() {
        val groupId = "groupId"
        val channelId = "channelId"
        val notificationManager = NotificationManagerCompat.from(this@PushService)
        val notificationChannelGroup = NotificationChannelGroupCompat.Builder(groupId)
                .setName("投屏")
                .setDescription("投屏通知组别")
                .build()
        notificationManager.createNotificationChannelGroup(notificationChannelGroup)
        val notificationChannel = NotificationChannelCompat.Builder(channelId, 2)
                .setName("投屏通知渠道")
                .setDescription("投屏通知渠道")
                .setGroup(groupId)
                .build()
        notificationManager.createNotificationChannelsCompat(mutableListOf(notificationChannel))
        val stopIntent = PendingIntent.getBroadcast(baseContext, 101, Intent(stopPushBroadcastAction), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this@PushService, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("投屏服务运行中")
                .setSilent(true)
                .setOngoing(true)
                .addAction(0, "退出投屏", stopIntent)
                .build()
        startForeground(1, notification)
    }

    //停止自身服务的广播 用于响应通知栏按钮
    private val stopPushBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        mainScope.cancel()
        datagramSocket.close()
        stopForeground(true)
        unregisterReceiver(stopPushBroadcastReceiver)
        state.value = 0
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val MediaProjectionPermissionResultData = "mediaProjectionPermissionResultData"
        private const val stopPushBroadcastAction = "stopPushBroadcastAction"

        //0 无状态
        //1 投屏中
        val state = MutableLiveData(0)

        @JvmStatic
        fun start(context: Context, mediaProjectionPermissionResultData: Intent?) {
            val intent = Intent(context, PushService::class.java)
                    .putExtra(MediaProjectionPermissionResultData, mediaProjectionPermissionResultData)
            context.startService(intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, PushService::class.java)
            context.stopService(intent)
        }

    }
}