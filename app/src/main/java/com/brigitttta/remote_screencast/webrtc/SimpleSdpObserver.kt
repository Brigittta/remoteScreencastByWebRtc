package com.brigitttta.remote_screencast.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


open class SimpleSdpObserver constructor(private val TAG: String) : SdpObserver {

    override fun onSetFailure(str: String) {
        Log.d(TAG, "onSetFailure: ${str}")
    }

    override fun onSetSuccess() {
        Log.d(TAG, "onSetSuccess: ${null}")
    }

    override fun onCreateSuccess(description: SessionDescription) {
        Log.d(TAG, "onCreateSuccess: ${description}")
    }

    override fun onCreateFailure(str: String) {
        Log.d(TAG, "onCreateFailure: ${str}")
    }
}