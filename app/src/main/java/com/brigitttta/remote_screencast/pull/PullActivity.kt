package com.brigitttta.remote_screencast.pull

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.brigitttta.remote_screencast.R
import com.brigitttta.remote_screencast.bean.MotionModel
import com.brigitttta.remote_screencast.bean.WebrtcMessage
import com.brigitttta.remote_screencast.webrtc.SimplePeerConnectionObserver
import com.brigitttta.remote_screencast.webrtc.SimpleSdpObserver
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
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
import org.webrtc.PeerConnection.IceConnectionState.*
import org.webrtc.audio.JavaAudioDeviceModule


class PullActivity : AppCompatActivity() {
    private val mainScope = MainScope()
    private var eventLoopGroup: NioEventLoopGroup? = null
    private var inetHost: String? = null
    private var inetPort = 0

    //EglBase
    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.getEglBaseContext()
    private lateinit var surfaceViewRenderer: SurfaceViewRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 配置隐藏系统栏的行为
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 隐藏状态栏和导航栏
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_pull)

        surfaceViewRenderer = findViewById<SurfaceViewRenderer>(R.id.srv)
        surfaceViewRenderer.init(eglBaseContext, null)

        inetHost = intent.getStringExtra(VA)
        inetPort = intent.getIntExtra(VB, 0)

        if (inetHost.isNullOrBlank() || inetPort == 0) {
            return finish()
        }

        initService()
    }


    private fun initService() {
        mainScope.launch(Dispatchers.IO) {
            runCatching {
                eventLoopGroup = NioEventLoopGroup()
                Bootstrap()
                        .group(eventLoopGroup)
                        .channel(NioSocketChannel::class.java)
                        .handler(object : ChannelInitializer<SocketChannel>() {
                            override fun initChannel(channel: SocketChannel) {
                                val pipeline = channel.pipeline()
                                //数据分包，组包，粘包
                                pipeline.addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                                pipeline.addLast(LengthFieldPrepender(4))
                                pipeline.addLast(StringDecoder(CharsetUtil.UTF_8))
                                pipeline.addLast(StringEncoder(CharsetUtil.UTF_8))
                                pipeline.addLast(object : SimpleChannelInboundHandler<String>() {
                                    private var peerConnection: PeerConnection? = null

                                    @SuppressLint("ClickableViewAccessibility")
                                    //信道激活消息
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                        launch(Dispatchers.Main) {
                                            surfaceViewRenderer.setOnTouchListener { v, event ->
                                                val motionModel = MotionModel(event, v.width, v.height)
                                                ctx?.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.MOVE, motionModel = motionModel).toString())
                                                return@setOnTouchListener true
                                            }
                                        }


                                        //初始化
//                                        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(requireContext().applicationContext).createInitializationOptions())
                                        //视频编码器
                                        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
                                        //视频解码器
                                        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
                                        //音频设备模块
                                        val audioDeviceModule = JavaAudioDeviceModule.builder(this@PullActivity).createAudioDeviceModule()
                                        //选项
                                        val options = PeerConnectionFactory.Options()
                                        //对等连接Factory
                                        val peerConnectionFactory = PeerConnectionFactory.builder()
                                                .setOptions(options)
                                                .setVideoEncoderFactory(encoderFactory)
                                                .setVideoDecoderFactory(decoderFactory)
                                                .setAudioDeviceModule(audioDeviceModule)
                                                .createPeerConnectionFactory()

                                        //rtc配置
                                        val rtcConfig = PeerConnection.RTCConfiguration(listOf()).apply {
                                            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                                            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                            keyType = PeerConnection.KeyType.ECDSA
//                                            sdpSemantics = PeerConnection.SdpSemantics.PLAN_B
                                        }


                                        //创建对等连接
                                        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver("pull") {

                                            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                                                super.onAddTrack(rtpReceiver, mediaStreams)
                                                val track = rtpReceiver.track()
                                                when (track) {
                                                    is VideoTrack -> {
                                                        track.addSink(surfaceViewRenderer)
                                                    }
                                                }
                                            }

                                            override fun onIceCandidate(iceCandidate: IceCandidate) {
                                                super.onIceCandidate(iceCandidate)
                                                ctx?.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.ICE, iceCandidate = iceCandidate).toString())
                                            }

                                            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                                                super.onIceConnectionChange(state)
                                                Log.d("TAG", "onIceConnectionChange:${state} ")
                                                when (state) {
                                                    NEW -> {}
                                                    CHECKING -> {}
                                                    CONNECTED -> {}
                                                    COMPLETED -> {}
                                                    FAILED -> {}
                                                    DISCONNECTED -> {}
                                                    CLOSED -> {}
                                                }
                                            }
                                        })
                                    }

                                    //信道不活跃消息
                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
                                        surfaceViewRenderer.clearImage()
                                        peerConnection?.dispose()
                                        peerConnection = null

                                        lifecycleScope.launch {
                                            Toast.makeText(this@PullActivity, "连接断开，请检查网络并重试", Toast.LENGTH_LONG).show()
                                        }
                                        finish()
                                    }

                                    //信道读消息
                                    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
                                        val webrtcMessage = WebrtcMessage(msg)
                                        when (webrtcMessage.type) {
                                            WebrtcMessage.Type.SDP -> {
                                                peerConnection?.setRemoteDescription(SimpleSdpObserver("pull-setRemoteDescription"), webrtcMessage.description)
                                                peerConnection?.createAnswer(object : SimpleSdpObserver("pull-createAnswer") {
                                                    override fun onCreateSuccess(description: SessionDescription) {
                                                        peerConnection?.setLocalDescription(SimpleSdpObserver("pull-setLocalDescription"), description)
                                                        ctx.writeAndFlush(WebrtcMessage(type = WebrtcMessage.Type.SDP, description = description).toString())
                                                    }
                                                }, MediaConstraints())
                                            }
                                            WebrtcMessage.Type.ICE -> {
                                                peerConnection?.addIceCandidate(webrtcMessage.iceCandidate)
                                            }
                                            WebrtcMessage.Type.SIZE -> {
                                                launch(Dispatchers.Main) {
                                                    surfaceViewRenderer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                                        dimensionRatio = "${webrtcMessage.size?.width}:${webrtcMessage.size?.height}"
                                                    }
                                                }
                                            }
                                            else -> {}
                                        }


                                    }
                                })
                            }
                        })
                        .connect(inetHost!!, inetPort).sync()
                        .channel()
                        .closeFuture().sync()
            }.onFailure {
                it.printStackTrace()
                eventLoopGroup?.shutdownGracefully()
                mainScope.launch(Dispatchers.Main) {
                    AlertDialog.Builder(this@PullActivity)
                            .setTitle("${it.message}")
                            .setNegativeButton("ok") { _, _ ->
                                finish()
                            }
                            .show()
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        surfaceViewRenderer.clearImage()
        surfaceViewRenderer.release()
        eglBase.release()
        eventLoopGroup?.shutdownGracefully()
        mainScope.cancel()
    }

    companion object {
        const val VA = "inetHost"
        const val VB = "inetPort"

        @JvmStatic
        fun start(context: Context, inetHost: String, inetPort: Int) {
            val starter = Intent(context, PullActivity::class.java)
                    .putExtra(VA, inetHost)
                    .putExtra(VB, inetPort)
            context.startActivity(starter)
        }
    }
}