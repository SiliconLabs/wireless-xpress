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

import android.accounts.AccountManager
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.silabs.bgxcommander.*
import com.silabs.bgxcommander.dialogs.DialogUtils
import com.silabs.bgxcommander.dialogs.OptionsDialog
import com.silabs.bgxcommander.enums.PasswordKind
import com.silabs.bgxcommander.enums.TextSource
import com.silabs.bgxcommander.other.Version
import com.silabs.bgxpress.BGX_CONNECTION_STATUS
import com.silabs.bgxpress.BGXpressService
import com.silabs.bgxpress.BGXpressService.BGXPartID
import com.silabs.bgxpress.BusMode
import kotlinx.android.synthetic.main.activity_device_details.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DeviceDetailsActivity : AppCompatActivity() {
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var receiver: BroadcastReceiver
    private lateinit var handler: Handler

    private var busMode = BusMode.UNKNOWN_MODE
    private var textSource = TextSource.UNKNOWN

    private var deviceAddress: String? = null
    private var deviceName: String? = null

    private var stateIconMenuItem: MenuItem? = null

    private var mBGXPartIdentifier: String? = null
    private var mBGXPartID: BGXPartID? = null
    private var mBGXDeviceID: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        sharedPrefs = getSharedPreferences("com.silabs.bgxcommander", Context.MODE_PRIVATE)
        handler = Handler(Handler.Callback {
            Log.d("bgx_dbg", "Handle message.")
            false
        })

        receiver = getBroadcastReceiver()
        registerReceiver(receiver, getIntentFilter())

        rb_stream.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (busMode != BusMode.STREAM_MODE) {
                    sendBusMode(BusMode.STREAM_MODE)
                    setBusMode(BusMode.STREAM_MODE)
                }
            }
        }

        rb_command.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (busMode != BusMode.REMOTE_COMMAND_MODE && busMode != BusMode.LOCAL_COMMAND_MODE) {
                    sendBusMode(BusMode.REMOTE_COMMAND_MODE)
                    setBusMode(BusMode.REMOTE_COMMAND_MODE)
                }
            }
        }

        btn_send.setOnClickListener(View.OnClickListener {
            Log.d("bgx_dbg", "Send button clicked.")
            val messageText = et_message.text.toString()
            if (0 == messageText.compareTo("bytetest")) {
                runByteTest()
                return@OnClickListener
            }

            val newLinesOnSendValue = sharedPrefs.getBoolean("newlinesOnSend", true)
            val messageToSend = if (newLinesOnSendValue) {
                messageText + "\r\n"
            } else {
                messageText
            }
            BGXpressService.startActionBGXWriteMessage(this, messageToSend, deviceAddress)

            processText(messageToSend, TextSource.LOCAL)
            et_message.setText("", TextView.BufferType.EDITABLE)
        })

        ib_clear.setOnClickListener {
            Log.d("bgx_dbg", "clear")
            et_stream.setText("")
        }

        deviceName = intent.getStringExtra("DeviceName")
        deviceAddress = intent.getStringExtra("DeviceAddress")
        supportActionBar?.title = deviceName

        handler.post {
            BGXpressService.startActionBGXReadBusMode(this, deviceAddress)
        }

        BGXpressService.getBGXDeviceInfo(this, deviceAddress)
    }

    private fun getBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val intentDeviceAddress = intent.getStringExtra("DeviceAddress")
                if (intentDeviceAddress != null && intentDeviceAddress.length > 1 && !intentDeviceAddress.equals(deviceAddress, ignoreCase = true)) {
                    return
                }
                when (intent.action) {
                    BGXpressService.BGX_INVALID_GATT_HANDLES -> {
                        deviceName?.let {
                            DialogUtils().getInvalidGattHandlesDialog(this@DeviceDetailsActivity, it).show()
                        }
                    }
                    BGXpressService.FIRMWARE_VERSIONS_AVAILABLE -> {
                        val bootloaderVersion = BGXpressService.getBGXBootloaderVersion(deviceAddress)
                        if (bootloaderVersion >= kBootloaderSecurityVersion) {
                            val versionJSON = intent.getStringExtra("versions-available-json")
                            try {
                                Log.d("bgx_dbg", "Device Address: $deviceAddress")
                                val versions = JSONArray(versionJSON)
                                val vFirmwareRevision = Version(BGXpressService.getFirmwareRevision(deviceAddress))

                                for (i in 0 until versions.length()) {
                                    val rec = versions[i] as JSONObject
                                    val sversion = rec["version"] as String
                                    val iversion = Version(sversion)
                                    if (iversion > vFirmwareRevision) {
                                        // newer version available.
                                        stateIconMenuItem?.icon = ContextCompat.getDrawable(this@DeviceDetailsActivity, R.drawable.update_decoration)
                                        break
                                    }
                                }
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            } catch (e: RuntimeException) {
                                e.printStackTrace()
                            }
                        }
                    }
                    BGXpressService.BGX_CONNECTION_STATUS_CHANGE -> {
                        Log.d("bgx_dbg", "BGX Connection State Change")
                        val state = intent.getSerializableExtra("bgx-connection-status") as BGX_CONNECTION_STATUS
                        when (state) {
                            BGX_CONNECTION_STATUS.CONNECTED -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to CONNECTED")
                            }
                            BGX_CONNECTION_STATUS.CONNECTING -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to CONNECTING")
                            }
                            BGX_CONNECTION_STATUS.DISCONNECTING -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to DISCONNECTING")
                            }
                            BGX_CONNECTION_STATUS.DISCONNECTED -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to DISCONNECTED")
                                finish()
                            }
                            BGX_CONNECTION_STATUS.INTERROGATING -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to INTERROGATING")
                            }
                            else -> {
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to Unknown connection state.")
                            }
                        }
                    }
                    BGXpressService.BGX_MODE_STATE_CHANGE -> {
                        Log.d("bgx_dbg", "BGX Bus Mode Change")
                        setBusMode(intent.getIntExtra("busmode", BusMode.UNKNOWN_MODE))
                    }
                    BGXpressService.BGX_DATA_RECEIVED -> {
                        val stringReceived = intent.getStringExtra("data")
                        processText(stringReceived, TextSource.REMOTE)
                    }
                    BGXpressService.BGX_DEVICE_INFO -> {
                        val bootloaderVersion = BGXpressService.getBGXBootloaderVersion(deviceAddress)
                        mBGXDeviceID = intent.getStringExtra("bgx-device-uuid")
                        mBGXPartID = intent.getSerializableExtra("bgx-part-id") as BGXPartID
                        mBGXPartIdentifier = intent.getStringExtra("bgx-part-identifier")

                        if (bootloaderVersion >= kBootloaderSecurityVersion) {
                            BGXpressService.startActionBGXGetFirmwareVersions(this@DeviceDetailsActivity, mBGXPartIdentifier)
                        } else if (bootloaderVersion > 0) {
                            stateIconMenuItem?.icon = ContextCompat.getDrawable(this@DeviceDetailsActivity, R.drawable.security_decoration)
                        }
                    }
                    BGXpressService.BUS_MODE_ERROR_PASSWORD_REQUIRED -> {
                        setBusMode(BusMode.STREAM_MODE)
                        context.startActivity(getPasswordIntent())
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_details, menu)
        stateIconMenuItem = menu.findItem(R.id.menu_item_state_icon)
        stateIconMenuItem?.icon = null
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.menu_item_update -> {
                openFirmwareUpdateActivity()
            }
            R.id.menu_item_options -> {
                showOptionsDialog()
            }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    private fun openFirmwareUpdateActivity() {
        if (mBGXPartID == null || BGXPartID.BGXInvalid == mBGXPartID) {
            Toast.makeText(this, "Invalid BGX Part ID", Toast.LENGTH_LONG).show()
        } else {
            startActivity(getFirmwareUpdateIntent())
        }
    }

    private fun showOptionsDialog() {
        OptionsDialog().show(supportFragmentManager, "dialog_options")
    }

    override fun onBackPressed() {
        Log.d("bgx_dbg", "Back button pressed.")
        disconnect()
        finish()
        super.onBackPressed()
    }

    private fun disconnect() {
        BGXpressService.startActionBGXDisconnect(this, deviceAddress)
    }

    fun setBusMode(busMode: Int) {
        if (this.busMode != busMode) {
            this.busMode = busMode
            handler.post {
                when (this@DeviceDetailsActivity.busMode) {
                    BusMode.UNKNOWN_MODE -> {
                        rb_stream.isChecked = false
                        rb_command.isChecked = false
                    }
                    BusMode.STREAM_MODE -> {
                        rb_stream.isChecked = true
                        rb_command.isChecked = false
                    }
                    BusMode.LOCAL_COMMAND_MODE,
                    BusMode.REMOTE_COMMAND_MODE -> {
                        rb_stream.isChecked = false
                        rb_command.isChecked = true
                    }
                }
            }
        }
    }

    /* Here we need to check to see if a busmode password is set for this device.
         * If there is one, then we would need to add it to the intent.
         */
    private fun sendBusMode(busMode: Int) {
        val am = AccountManager.get(this)
        val password = PasswordActivity.retrievePassword(am, PasswordKind.BusModePasswordKind, deviceAddress)
        BGXpressService.startActionBGXWriteBusMode(this, deviceAddress, busMode, password)
    }


    fun processText(text: String?, source: TextSource) {
        val ssb = SpannableStringBuilder()
        val newLinesOnSendValue = sharedPrefs.getBoolean("newlinesOnSend", true)
        when (source) {
            TextSource.LOCAL -> {
                if (TextSource.LOCAL !== textSource && newLinesOnSendValue) {
                    ssb.append("\n>", ForegroundColorSpan(Color.WHITE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                ssb.append(text, ForegroundColorSpan(Color.WHITE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            TextSource.REMOTE -> {
                if (TextSource.REMOTE !== textSource && newLinesOnSendValue) {
                    ssb.append("\n<", ForegroundColorSpan(Color.GREEN), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                ssb.append(text, ForegroundColorSpan(Color.GREEN), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        et_stream.append(ssb)
        textSource = source
    }

    /*
     * The purpose of this function is to test ACTION_WRITE_SERIAL_BIN_DATA
     * by sending all the possible byte values.
     */
    private fun runByteTest() {
        val byteArray = ByteArray(256)
        for (i in 0..255) {
            byteArray[i] = i.toByte()
        }
        BGXpressService.startActionBGXWriteByteArray(this, byteArray, deviceAddress)
    }

    private fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BGXpressService.BGX_CONNECTION_STATUS_CHANGE)
            addAction(BGXpressService.BGX_MODE_STATE_CHANGE)
            addAction(BGXpressService.BGX_DATA_RECEIVED)
            addAction(BGXpressService.BGX_DEVICE_INFO)
            addAction(BGXpressService.FIRMWARE_VERSIONS_AVAILABLE)
            addAction(BGXpressService.BUS_MODE_ERROR_PASSWORD_REQUIRED)
            addAction(BGXpressService.BGX_INVALID_GATT_HANDLES)
        }
    }

    private fun getPasswordIntent(): Intent {
        return Intent(this@DeviceDetailsActivity, PasswordActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("DeviceAddress", deviceAddress)
            putExtra("PasswordKind", PasswordKind.BusModePasswordKind)
            putExtra("DeviceName", deviceName)
        }
    }

    private fun getFirmwareUpdateIntent(): Intent {
        return Intent(this@DeviceDetailsActivity, FirmwareUpdateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("bgx-device-uuid", mBGXDeviceID)
            putExtra("bgx-part-id", mBGXPartID)
            putExtra("bgx-part-identifier", mBGXPartIdentifier)
            putExtra("DeviceAddress", deviceAddress)
            putExtra("DeviceName", deviceName)
        }
    }

    companion object {
        const val kBootloaderSecurityVersion = 1229
    }
}