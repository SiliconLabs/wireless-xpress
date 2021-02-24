package com.silabs.bgxcommander.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.silabs.bgxcommander.R
import com.silabs.bgxcommander.adapters.BGXDeviceListAdapter.BGXDeviceViewHolder
import kotlinx.android.synthetic.main.adapter_devices_list.view.*

class BGXDeviceListAdapter(private val context: Context, private val list: List<Map<String, String>>, private val listener: ItemClickListener) : RecyclerView.Adapter<BGXDeviceViewHolder>() {

    inner class BGXDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val tvDeviceName = itemView.tv_device_name as TextView
        private val tvDeviceUuid = itemView.tv_device_uuid as TextView
        private val tvRssiValue = itemView.tv_rssi_value as TextView

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: Map<String, String>) {
            tvDeviceName.text = item["name"]
            tvDeviceUuid.text = item["uuid"]
            tvRssiValue.text = item["rssi"]
        }

        override fun onClick(v: View) {
            Log.d("bgx_dbg", "Selected $tvDeviceName $tvDeviceUuid")
            listener.connectToDevice(list[adapterPosition])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BGXDeviceViewHolder {
        return BGXDeviceViewHolder(LayoutInflater.from(context).inflate(R.layout.adapter_devices_list, parent, false))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: BGXDeviceViewHolder, position: Int) {
        holder.bind(list[position])
    }

    interface ItemClickListener {
        fun connectToDevice(deviceData: Map<String, String>)
    }
}