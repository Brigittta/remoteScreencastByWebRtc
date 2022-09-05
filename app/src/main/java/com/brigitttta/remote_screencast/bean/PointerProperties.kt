package com.brigitttta.remote_screencast.bean

import android.view.MotionEvent
import java.io.Serializable

class PointerProperties : Serializable {
    var id = 0
    var toolType = 0

    constructor()
    constructor(it: MotionEvent.PointerProperties) : this() {
        fromMotionEventPointerProperties(it)
    }

    fun toMotionEventPointerProperties(): MotionEvent.PointerProperties {
        return MotionEvent.PointerProperties().also {
            it.id = id
            it.toolType = toolType
        }
    }

    fun fromMotionEventPointerProperties(it: MotionEvent.PointerProperties) {
        id = it.id
        toolType = it.toolType
    }
}
