package com.brigitttta.remote_screencast.bean

import android.view.MotionEvent
import java.io.Serializable

class PointerCoords : Serializable {
    var x = 0f
    var y = 0f
    var pressure = 0f
    var size = 0f
    var touchMajor = 0f
    var touchMinor = 0f
    var toolMajor = 0f
    var toolMinor = 0f
    var orientation = 0f

    constructor()
    constructor(it: MotionEvent.PointerCoords) : this() {
        formMotionEventPointerCoords(it)
    }

    fun toMotionEventPointerCoords(): MotionEvent.PointerCoords {
        return MotionEvent.PointerCoords().also {
            it.x = x
            it.y = y
            it.pressure = pressure
            it.size = size
            it.touchMajor = touchMajor
            it.touchMinor = touchMinor
            it.toolMajor = toolMajor
            it.toolMinor = toolMinor
            it.orientation = orientation
        }
    }

    fun formMotionEventPointerCoords(it: MotionEvent.PointerCoords) {
        x = it.x
        y = it.y
        pressure = it.pressure
        size = it.size
        touchMajor = it.touchMajor
        touchMinor = it.touchMinor
        toolMajor = it.toolMajor
        toolMinor = it.toolMinor
        orientation = it.orientation
    }

}