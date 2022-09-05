package com.brigitttta.remote_screencast.webrtc

import org.webrtc.*

open class SimplePeerConnectionObserver(private val TAG: String) : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) {

    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {

    }

    override fun onIceConnectionReceivingChange(b: Boolean) {

    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {

    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {

    }

    override fun onIceCandidatesRemoved(mediaStreams: Array<out IceCandidate>) {

    }

    override fun onAddStream(mediaStream: MediaStream) {

    }

    override fun onRemoveStream(mediaStream: MediaStream) {

    }

    override fun onDataChannel(dataChannel: DataChannel) {

    }

    override fun onRenegotiationNeeded() {

    }

    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {

    }

}
