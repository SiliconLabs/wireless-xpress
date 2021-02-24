/*
 * Copyright 2018-2019 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.silabs.bgxcommander.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.silabs.bgxcommander.R
import com.silabs.bgxpress.BGX_CONNECTION_STATUS
import com.silabs.bgxpress.BGXpressService
import kotlinx.android.synthetic.main.activity_indeterminate_progress.*

class IndeterminateProgressActivity : AppCompatActivity() {
    private lateinit var receiver: BroadcastReceiver
    private lateinit var handler: Handler

    private var deviceAddress: String? = null
    private var deviceName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_indeterminate_progress)

        handler = Handler()
        receiver = getBroadcastReceiver()
        registerReceiver(receiver, getIntentFilter())

        deviceAddress = intent.getStringExtra("DeviceAddress")
        deviceName = intent.getStringExtra("DeviceName")

        tv_connection_status.setText(R.string.bgx_connection_status_connecting)
        tv_device_name.text = deviceName
        btn_cancel.setOnClickListener {
            Log.d("bgx_dbg", "cancel button clicked.")
            BGXpressService.startActionBGXCancelConnect(this@IndeterminateProgressActivity, deviceAddress)
            finish()
        }

        BGXpressService.startActionBGXConnect(this@IndeterminateProgressActivity, deviceAddress)
    }

    override fun onBackPressed() {
        Log.d("bgx_dbg", "cancel on back pressed.")
        BGXpressService.startActionBGXCancelConnect(this@IndeterminateProgressActivity, deviceAddress)
        finish()
    }

    private fun getBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra("DeviceAddress")?.let {
                    deviceAddress = it
                }

                when (intent.action) {
                    BGXpressService.BGX_INVALID_GATT_HANDLES -> {
                        finish()
                    }
                    BGXpressService.BGX_CONNECTION_ERROR -> {
                        val status = intent.getIntExtra("status", -1)
                        when (status) {
                            137 -> {
                                tv_connection_status.setText(R.string.label_bond_fail)
                            }
                            133 -> {
                                BGXpressService.startActionBGXConnect(context, deviceAddress)
                            }
                            -1 -> {
                                Toast.makeText(context, R.string.toast_device_connection_error, Toast.LENGTH_LONG).show()
                                finish()
                            }
                            else -> {
                                tv_connection_status.text = getString(R.string.label_error_with_status, status)
                            }
                        }
                    }
                    BGXpressService.BGX_CONNECTION_STATUS_CHANGE -> {
                        val status = intent.getSerializableExtra("bgx-connection-status") as BGX_CONNECTION_STATUS
                        Log.d("bgx_dbg", "BGX Connection State Change: $status")

                        val isBonded = intent.getBooleanExtra("bonded", false)
                        when (isBonded) {
                            true -> iv_bond_state.setImageResource(R.drawable.lock_small)
                            false -> iv_bond_state.setImageResource(R.drawable.unlock_small)
                        }

                        when {
                            BGX_CONNECTION_STATUS.CONNECTING == status -> {
                                tv_connection_status.setText(R.string.bgx_connection_status_connecting)
                            }
                            BGX_CONNECTION_STATUS.INTERROGATING == status -> {
                                tv_connection_status.setText(R.string.bgx_connection_status_interrogating)
                            }
                            BGX_CONNECTION_STATUS.CONNECTED == status -> {
                                tv_connection_status.setText(R.string.bgx_connection_status_connected)
                                handler.postDelayed({
                                    finish()
                                }, 500)
                            }
                            BGX_CONNECTION_STATUS.DISCONNECTED == status -> {
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BGXpressService.BGX_CONNECTION_STATUS_CHANGE)
            addAction(BGXpressService.BGX_CONNECTION_ERROR)
            addAction(BGXpressService.BGX_INVALID_GATT_HANDLES)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}