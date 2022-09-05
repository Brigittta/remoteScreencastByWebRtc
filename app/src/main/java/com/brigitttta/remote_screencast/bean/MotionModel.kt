package com.brigitttta.remote_screencast.bean

import android.view.MotionEvent
import java.io.Serializable

class MotionModel : Serializable {

    var action = 0
    var pointerCount = 0
    var buttonState = 0
    var metaState = 0
    var flags = 0
    var edgeFlags = 0
    var downTime = 0L
    var eventTime = 0L
    var deviceId = 0
    var source = 0
    var xPrecision = 0f
    var yPrecision = 0f
    var pointerProperties = mutableListOf<PointerProperties>()
    var pointerCoords = mutableListOf<PointerCoords>()
    var remoteHeight = 0
    var remoteWidth = 0

    private fun getPointerProperties(): Array<MotionEvent.PointerProperties> {
        return pointerProperties.map {
            it.toMotionEventPointerProperties()
        }.toTypedArray()
    }

    private fun getPointerCoords(): Array<MotionEvent.PointerCoords> {
        return pointerCoords.map {
            it.toMotionEventPointerCoords()
        }.toTypedArray()
    }

    constructor()
    constructor(event: MotionEvent, width: Int, height: Int) : this() {
        fromMotionEvent(event)
        remoteWidth = width
        remoteHeight = height
    }

    fun fromMotionEvent(event: MotionEvent) {
        downTime = event.downTime
        eventTime = event.eventTime
        action = event.action
        pointerCount = event.pointerCount
        repeat(pointerCount) {
            val properties = MotionEvent.PointerProperties()
            event.getPointerProperties(it, properties)
            pointerProperties.add(PointerProperties(properties))

            val coords = MotionEvent.PointerCoords()
            event.getPointerCoords(it, coords)
            pointerCoords.add(PointerCoords(coords))
        }
        metaState = event.metaState
        buttonState = event.buttonState
        xPrecision = event.xPrecision
        yPrecision = event.yPrecision
        deviceId = event.deviceId
        edgeFlags = event.edgeFlags
        source = event.source
        flags = event.flags

    }


    fun toMotionEvent(): MotionEvent {
        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                getPointerProperties(),
                getPointerCoords(),
                metaState,
                buttonState,
                xPrecision,
                yPrecision,
                deviceId,
                edgeFlags,
                source,
                flags,
        )

    }

    fun scaleByScreen(screenHeight: Int, screenWidth: Int) {
        val screenRatio = screenWidth * 1f / screenHeight
        val remoteRatio = remoteWidth * 1f / remoteHeight

        if (remoteRatio > screenRatio) {
            val scale = screenHeight * 1f / remoteHeight
            val scaleWidth = remoteWidth * scale
            val offset = (scaleWidth - screenWidth) * 1f / 2
            pointerCoords.forEach { coords ->
                coords.x = coords.x * scale
                coords.y = coords.y * scale
                if (coords.x < offset || coords.x > scaleWidth - offset) {
                    coords.x = 0f
                } else {
                    coords.x = coords.x - offset
                }
            }
        } else {
            val scale = screenWidth * 1f / remoteWidth
            val scaleHeight = remoteHeight * scale
            val offset = (scaleHeight - screenHeight) * 1f / 2
            pointerCoords.forEach { coords ->
                coords.x = coords.x * scale
                coords.y = coords.y * scale
                if (coords.y < offset || coords.y > scaleHeight - offset) {
                    coords.y = 0f
                } else {
                    coords.y = coords.y - offset
                }
            }
        }
    }
}
