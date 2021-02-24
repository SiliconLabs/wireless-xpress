package com.silabs.bgxcommander.views

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.silabs.bgxcommander.R
import kotlinx.android.synthetic.main.bluetooth_enable_bar.view.*

class BluetoothEnableBar(context: Context, attrs: AttributeSet?) : RelativeLayout(context, attrs) {
    constructor(context: Context) : this(context, null)

    fun show() {
        bluetooth_enable_msg.setText(R.string.bluetooth_bar_info_adapter_is_disabled)
        setBackgroundColor(ContextCompat.getColor(context, R.color.dark_red))
        bluetooth_enable_btn.visibility = View.VISIBLE
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
    }

    fun changeEnableBluetoothAdapterToConnecting() {
        BluetoothAdapter.getDefaultAdapter().enable()
        bluetooth_enable_btn.visibility = View.GONE
        bluetooth_enable_msg.setText(R.string.bluetooth_bar_info_turning_on_bluetooth_adapter)
        setBackgroundColor(ContextCompat.getColor(context, R.color.blue))
    }
}