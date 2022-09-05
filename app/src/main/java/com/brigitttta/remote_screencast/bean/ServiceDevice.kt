package com.brigitttta.remote_screencast.bean

class ServiceDevice {
    var remoteHost = ""
    var ip = ""
    var mac = ""

    constructor()
    constructor(remoteHost: String?, ip: String?, mac: String?) {
        this.remoteHost = remoteHost ?: ""
        this.ip = ip ?: ""
        this.mac = mac ?: ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServiceDevice

        if (remoteHost != other.remoteHost) return false

        return true
    }

    override fun hashCode(): Int {
        return remoteHost.hashCode()
    }


}