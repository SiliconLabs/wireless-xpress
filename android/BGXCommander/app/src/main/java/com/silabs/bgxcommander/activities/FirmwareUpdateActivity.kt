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
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.silabs.bgxcommander.*
import com.silabs.bgxcommander.activities.PasswordActivity.Companion.retrievePassword
import com.silabs.bgxcommander.adapters.DMSVersionsAdapter
import com.silabs.bgxcommander.dialogs.DialogUtils
import com.silabs.bgxcommander.enums.PasswordKind
import com.silabs.bgxcommander.other.SelectionChangedListener
import com.silabs.bgxcommander.other.Version
import com.silabs.bgxpress.BGXpressService
import com.silabs.bgxpress.BGXpressService.BGXPartID
import com.silabs.bgxpress.OTA_Status
import kotlinx.android.synthetic.main.firmware_update.*
import kotlinx.android.synthetic.main.firmware_update_progress.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class FirmwareUpdateActivity : AppCompatActivity(), SelectionChangedListener {
    private lateinit var receiver: BroadcastReceiver
    private lateinit var handler: Handler

    private var mBGXPartIdentifier: String? = null
    private var mBGXDeviceAddress: String? = null
    private var firmwareVersions: JSONArray? = null
    private var mBGXDeviceName: String? = null
    private var mBGXPartID: BGXPartID? = null
    private var mBGXDeviceID: String? = null

    private var selectedObject: JSONObject? = null
    private var imagePath: String? = null

    override fun selectionDidChange(position: Int, selectedObject: JSONObject?) {
        this.selectedObject = selectedObject
        Log.d("bgx_dbg", "selectionDidChange called.")
        btn_install_update.isEnabled = position != -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_firmware_update)
        handler = Handler()

        mBGXPartID = intent.getSerializableExtra("bgx-part-id") as BGXPartID
        mBGXDeviceID = intent.getStringExtra("bgx-device-id")
        mBGXDeviceAddress = intent.getStringExtra("DeviceAddress")
        mBGXDeviceName = intent.getStringExtra("DeviceName")
        mBGXPartIdentifier = intent.getStringExtra("bgx-part-identifier")

        initRecyclerView()
        BGXpressService.startActionBGXGetFirmwareVersions(this, mBGXPartIdentifier)

        btn_install_update.isEnabled = false
        supportActionBar?.title = "Firmware Available for $mBGXDeviceName"

        receiver = getBroadcastReceiver()
        registerReceiver(receiver, getIntentFilter())

        btn_install_update.setOnClickListener {
            try {
                Log.d("bgx_dbg", "Install update")
                firmware_update.visibility = View.GONE
                firmware_update_progress.visibility = View.VISIBLE
                val selectedVersion = selectedObject?.getString("version")
                val otaImageSize = selectedObject?.getInt("size")
                imagePath = selectedObject?.getString("file")
                otaImageSize?.let {
                    progress_bar.max = otaImageSize
                }

                startOTAUpdate()

                progress_bar.progress = 0
                tv_upper_progress_msg.text = resources.getString(R.string.label_installing_firmware, selectedVersion)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        btn_cancel_update.setOnClickListener {
            BGXpressService.startActionBGXCancelOta(this, mBGXDeviceAddress)
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                finish()
            }, 1000)
        }

        btn_release_notes.setOnClickListener {
            Log.d("bgx_dbg", "Show the firmware release notes now.")
            startActivity(getFirmwareReleaseNotesIntent())
        }
        tv_current_version.text = BGXpressService.getFirmwareRevision(mBGXDeviceAddress)
    }

    override fun onBackPressed() {
        BGXpressService.startActionBGXCancelOta(this, mBGXDeviceAddress)
        super.onBackPressed()
    }

    private fun getBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PasswordActivity.ACTION_PASSWORD_UPDATED -> startOTAUpdate()
                    BGXpressService.FIRMWARE_VERSIONS_AVAILABLE -> {
                        val versionJSON = intent.getStringExtra("versions-available-json")
                        try {
                            setVersions(JSONArray(versionJSON))
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                    BGXpressService.OTA_STATUS_MESSAGE -> {
                        processOTAStatusMessage(intent)
                    }
                }
            }
        }
    }

    private fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BGXpressService.FIRMWARE_VERSIONS_AVAILABLE)
            addAction(BGXpressService.OTA_STATUS_MESSAGE)
            addAction(PasswordActivity.ACTION_PASSWORD_UPDATED)
        }
    }

    private fun initRecyclerView() {
        rv_dms_versions.adapter = DMSVersionsAdapter(this, this, JSONArray())
        rv_dms_versions.layoutManager = LinearLayoutManager(this)
        rv_dms_versions.setHasFixedSize(true)
    }

    fun startOTAUpdate() {
        val sharedPrefs = getSharedPreferences("com.silabs.bgxcommander", Context.MODE_PRIVATE)
        val fUseAckdWritesForOTA = sharedPrefs.getBoolean("useAckdWritesForOTA", true)
        val writeType = if (fUseAckdWritesForOTA) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val am = AccountManager.get(this@FirmwareUpdateActivity)
        val password = retrievePassword(am, PasswordKind.OTAPasswordKind, mBGXDeviceAddress)
        BGXpressService.startActionBGXOtaFirmwareImage(this, mBGXDeviceAddress, imagePath, writeType, password)
    }

    public override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    fun setVersions(versions: JSONArray) {
        sortByVersion(versions)

        try {
            val tmpArray = JSONArray(firmwareVersions.toString())

            // check and assign decorator if needed.
            val bootloaderVersion = BGXpressService.getBGXBootloaderVersion(mBGXDeviceAddress)
            if (bootloaderVersion != -1 && bootloaderVersion < DeviceDetailsActivity.kBootloaderSecurityVersion) {
                val securityDecoration = ContextCompat.getDrawable(this, R.drawable.security_decoration)
                iv_decoration.background = securityDecoration
            } else {
                try {
                    val vFirmwareRevision = Version(BGXpressService.getFirmwareRevision(mBGXDeviceAddress))
                    for (i in 0 until tmpArray.length()) {
                        val rec = tmpArray[i] as JSONObject
                        val sversion = rec["version"] as String
                        val iversion = Version(sversion)
                        if (iversion > vFirmwareRevision) {
                            // newer version available.
                            val updateDecoration = ContextCompat.getDrawable(this, R.drawable.update_decoration)
                            iv_decoration.background = updateDecoration
                            break
                        }
                    }
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }
            }

            rv_dms_versions.adapter = DMSVersionsAdapter(this, this, tmpArray)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun sortByVersion(versions: JSONArray) {
        try {
            val jsons: MutableList<JSONObject> = ArrayList()

            for (i in 0 until versions.length()) {
                jsons.add(versions.getJSONObject(i))
            }

            Collections.sort(jsons, Comparator { o1, o2 ->
                try {
                    val slversion = o1["version"] as String
                    val srversion = o2["version"] as String
                    val lversion = Version(slversion)
                    val rversion = Version(srversion)
                    return@Comparator rversion.compareTo(lversion)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    return@Comparator 0
                }
            })

            firmwareVersions = JSONArray()
            for (i in jsons.indices) {
                firmwareVersions?.put(jsons[i])
            }
        } catch (e: JSONException) {
            firmwareVersions = versions
        }
    }

    fun processOTAStatusMessage(intent: Intent) {
        val otaFailed = intent.getBooleanExtra("ota_failed", false)
        if (otaFailed) {
            tv_upper_progress_msg.setText(R.string.label_firmware_update_failed)
        }

        val otaStatus = intent.getSerializableExtra("ota_status") as OTA_Status
        when (otaStatus) {
            OTA_Status.Invalid -> {
            }
            OTA_Status.Idle -> tv_lower_progress_msg.text = ""
            OTA_Status.Password_Required -> {
                tv_lower_progress_msg.setText(R.string.ota_status_password_required)
                startActivityForResult(getPasswordIntent(), REQUEST_CODE_CANCEL_PASSWORD)
            }
            OTA_Status.Downloading -> tv_lower_progress_msg.setText(R.string.ota_status_downloading)
            OTA_Status.Installing -> tv_lower_progress_msg.setText(R.string.ota_status_installing)
            OTA_Status.Finishing -> tv_lower_progress_msg.setText(R.string.ota_status_finishing)
            OTA_Status.Finished -> {
                tv_lower_progress_msg.setText(R.string.ota_status_finished)
                mBGXDeviceName?.let {
                    DialogUtils().showForgetBluetoothDeviceDialog(this, it) {
                        otaFinished()
                    }.show()
                }
            }
            OTA_Status.Failed -> {
                tv_lower_progress_msg.setText(R.string.label_firmware_update_failed)
                btn_cancel_update.setOnClickListener { finish() }
            }
            OTA_Status.UserCanceled -> {
                tv_lower_progress_msg.setText(R.string.ota_status_user_canceled)
                progress_bar.progress = 0
                btn_cancel_update.isEnabled = false
            }
        }
        val bytesSent = intent.getIntExtra("bytes_sent", -1)
        if (-1 != bytesSent) {
            progress_bar.progress = bytesSent
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_CODE_CLOSE_FIRMWARE_UPDATE_VIEW && requestCode == REQUEST_CODE_CANCEL_PASSWORD) {
            finish()
        }
    }

    private fun otaFinished() {
        Log.d("bgx_dbg", "OTA is finished.")
        btn_cancel_update.visibility = View.INVISIBLE
        handler.postDelayed({
            finish()
            BGXpressService.startActionBGXDisconnect(this, mBGXDeviceAddress)
        }, 2000)
    }

    private fun getFirmwareReleaseNotesIntent(): Intent {
        return Intent(this, FirmwareReleaseNotesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun getPasswordIntent(): Intent {
        return Intent(this@FirmwareUpdateActivity, PasswordActivity::class.java).apply {
            putExtra("DeviceAddress", mBGXDeviceAddress)
            putExtra("PasswordKind", PasswordKind.OTAPasswordKind)
            putExtra("DeviceName", mBGXDeviceName)
        }
    }

    companion object {
        const val REQUEST_CODE_CANCEL_PASSWORD = 999
        const val RESULT_CODE_CLOSE_FIRMWARE_UPDATE_VIEW = 998
    }
}