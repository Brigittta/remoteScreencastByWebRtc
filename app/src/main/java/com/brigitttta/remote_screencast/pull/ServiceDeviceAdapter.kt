package com.brigitttta.remote_screencast.pull

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brigitttta.remote_screencast.R
import com.brigitttta.remote_screencast.bean.ServiceDevice

class ServiceDeviceAdapter : RecyclerView.Adapter<ServiceDeviceAdapter.VH>() {
    private var data = emptyList<ServiceDevice>()
    private var mOnClickListener: (device: ServiceDevice) -> Unit = {}

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val song_name = itemView.findViewById<TextView>(R.id.song_name)
        val song_info = itemView.findViewById<TextView>(R.id.song_info)

        fun bind(device: ServiceDevice, onClick: ((ServiceDevice) -> Unit)) {

            itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            song_name.text = "${device.remoteHost}"
            song_info.text = "${device.mac}"
            itemView.setOnClickListener {
                onClick.invoke(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service_device, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val device = data[position]
        holder.bind(device, mOnClickListener)
    }

    override fun getItemCount(): Int {
        return data.size
    }


    fun observe(data: Collection<ServiceDevice>) {
        this.data = data.sortedBy { it.ip }
        notifyDataSetChanged()
    }

    fun setOnClickListener(onClick: (device: ServiceDevice) -> Unit) {
        this.mOnClickListener = onClick
    }
}

