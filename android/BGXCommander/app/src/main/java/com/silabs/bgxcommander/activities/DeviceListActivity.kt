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

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.silabs.bgxcommander.adapters.BGXDeviceListAdapter
import com.silabs.bgxcommander.adapters.BGXDeviceListAdapter.ItemClickListener
import com.silabs.bgxcommander.R
import com.silabs.bgxcommander.dialogs.AboutDialog
import com.silabs.bgxcommander.dialogs.DialogUtils
import com.silabs.bgxcommander.dialogs.LocationInfoDialog
import com.silabs.bgxcommander.dialogs.OptionsDialog
import com.silabs.bgxcommander.views.BluetoothEnableBar
import com.silabs.bgxcommander.views.LocationDisabledBar
import com.silabs.bgxpress.BGX_CONNECTION_STATUS
import com.silabs.bgxpress.BGXpressService
import kotlinx.android.synthetic.main.activity_device_list.*
import kotlinx.android.synthetic.main.bluetooth_enable_bar.*
import kotlinx.android.synthetic.main.location_disabled_bar.*
import java.util.*
import kotlin.collections.ArrayList

class DeviceListActivity : AppCompatActivity(), ItemClickListener {
    private lateinit var scanResults: ArrayList<Map<String, String>>
    private lateinit var deviceListAdapter: BGXDeviceListAdapter
    private lateinit var locationDisabledBar: LocationDisabledBar
    private lateinit var bluetoothEnableBar: BluetoothEnableBar
    private lateinit var receiver: BroadcastReceiver
    private lateinit var handler: Handler

    private var scanMenuItem: MenuItem? = null

    private var isBluetoothEnabled = false
    private var isScanning = false

    private val startScanningRunnable = Runnable {
        BGXpressService.startActionStartScan(this)
    }

    private val bluetoothAdapterStateChangeListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        isBluetoothEnabled = false
                        bluetoothEnableBar.show()
                        isScanning = false
                        setScanButtonState()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        if (!isBluetoothEnabled) Toast.makeText(this@DeviceListActivity, R.string.toast_bluetooth_enabled, Toast.LENGTH_SHORT).show()
                        isBluetoothEnabled = true
                        bluetooth_enable?.visibility = View.GONE
                        setScanButtonState()
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> isBluetoothEnabled = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        locationDisabledBar = findViewById(R.id.location_disabled)
        bluetoothEnableBar = findViewById(R.id.bluetooth_enable)

        scanResults = ArrayList()
        handler = Handler()

        initRecyclerView()
        checkPermissions()
        handleBluetoothBarActions()
        handleLocationBarActions()

        receiver = getBroadcastReceiver()
        registerReceiver(receiver, getIntentFilter())

        isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (isBluetoothEnabled && isLocationEnabled() && isAccessFineLocationGranted()) {
            BGXpressService.startActionStartScan(this)
            isScanning = true
        }
    }

    private fun handleBluetoothBarActions() {
        bluetooth_enable_btn.setOnClickListener {
            bluetoothEnableBar.changeEnableBluetoothAdapterToConnecting()
        }
    }

    private fun handleLocationBarActions() {
        enable_location.setOnClickListener {
            val enableLocationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(enableLocationIntent)
        }

        location_info.setOnClickListener {
            LocationInfoDialog().show(supportFragmentManager, "location_info_dialog")
        }
    }

    private fun getBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BGXpressService.BGX_SCAN_DEVICE_DISCOVERED -> {
                        val deviceRecord = intent.getSerializableExtra("DeviceRecord") as HashMap<String, String>

                        // must now check if the scan resuls already contain this device because we are no longer clearing the scan results when scan starts
                        // because in multi-connect scenario you wouldn't rediscover devices you are already connected to.
                        val deviceAddress = deviceRecord["uuid"]
                        var containsRecord = false

                        for (i in 0 until scanResults.size) {
                            val record = scanResults[i] as HashMap<String, String>
                            val address = record["uuid"]
                            if (deviceAddress.equals(address, ignoreCase = true)) {
                                containsRecord = true
                                break
                            }
                        }

                        if (!containsRecord) {
                            scanResults.add(deviceRecord)
                            scanResults.sortWith(Comparator { leftRecord, rightRecord ->
                                val leftRssi = leftRecord["rssi"]
                                val rightRssi = rightRecord["rssi"]
                                leftRssi!!.compareTo(rightRssi!!)
                            })
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                    BGXpressService.BGX_CONNECTION_STATUS_CHANGE -> {
                        val connectionStatusValue = intent.getSerializableExtra("bgx-connection-status") as BGX_CONNECTION_STATUS
                        if (BGX_CONNECTION_STATUS.CONNECTED == connectionStatusValue) {
                            val btDevice = intent.getParcelableExtra<Parcelable>("device") as BluetoothDevice
                            BGXpressService.setBGXAcknowledgedReads(btDevice.address, true)
                            BGXpressService.setBGXAcknowledgedWrites(btDevice.address, true)
                            context.startActivity(getDeviceDetailsIntent(btDevice))
                        }
                    }
                    BGXpressService.BGX_SCAN_MODE_CHANGE -> {
                        isScanning = intent.getBooleanExtra("isscanning", false)
                        val isScanFailed = intent.getBooleanExtra("scanFailed", false)
                        if (isScanFailed) {
                            val error = intent.getIntExtra("error", 0)
                            Toast.makeText(this@DeviceListActivity, "Scan Failed. Error: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                    BGXpressService.BGX_INVALID_GATT_HANDLES -> {
                        val deviceAddress = intent.getStringExtra("DeviceAddress")
                        val deviceName = intent.getStringExtra("DeviceName")
                        BGXpressService.startActionBGXCancelConnect(this@DeviceListActivity, deviceAddress)
                        deviceName?.let {
                            DialogUtils().getInvalidGattHandlesDialog(this@DeviceListActivity, it).show()
                        }
                    }
                }
            }
        }
    }

    private fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BGXpressService.BGX_SCAN_DEVICE_DISCOVERED)
            addAction(BGXpressService.BGX_CONNECTION_STATUS_CHANGE)
            addAction(BGXpressService.BGX_SCAN_MODE_CHANGE)
            addAction(BGXpressService.BGX_INVALID_GATT_HANDLES)
        }
    }

    private fun initRecyclerView() {
        deviceListAdapter = BGXDeviceListAdapter(this, scanResults, this)
        rv_device_list.apply {
            adapter = deviceListAdapter
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
        }
    }

    private fun checkPermissions() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("bgx_dbg", "BLE Not Supported.")
            finish()
        }

        if (!isAccessFineLocationGranted()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        }
    }

    private fun isAccessFineLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_list, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_item_scan -> {
                handler.removeCallbacks(startScanningRunnable)

                if (isScanning) {
                    BGXpressService.startActionStopScan(this)
                } else {
                    scanResults.clear()
                    deviceListAdapter.notifyDataSetChanged()
                    handler.postDelayed(startScanningRunnable, 1000)
                }

                isScanning = !isScanning
                setScanButtonState()
                true
            }
            R.id.menu_item_about -> {
                showAboutDialog()
                true
            }
            R.id.menu_item_help -> {
                showHelp()
                true
            }
            R.id.menu_item_options -> {
                showOptionsDialog()
                true
            }
            else -> {
                super.onOptionsItemSelected(menuItem)
            }
        }
    }

    private fun showAboutDialog() {
        AboutDialog().show(supportFragmentManager, "dialog_about")
    }

    private fun showOptionsDialog() {
        OptionsDialog().show(supportFragmentManager, "dialog_options")
    }

    private fun showHelp() {
        val sHelpURL = getString(R.string.url_help)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sHelpURL))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(browserIntent)
    }

    override fun onResume() {
        super.onResume()

        isBluetoothEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (!isBluetoothEnabled) {
            bluetoothEnableBar.show()
        } else {
            bluetoothEnableBar.hide()
        }

        if (!isLocationEnabled()) {
            locationDisabledBar.show()
        } else {
            locationDisabledBar.hide()
        }

        setScanButtonState()

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothAdapterStateChangeListener, filter)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var isGpsEnabled = false
        var isNetworkEnabled = false

        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
        }

        try {
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
        }

        return isNetworkEnabled || isGpsEnabled
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(startScanningRunnable)
        BGXpressService.startActionStopScan(this)
        isScanning = false

        unregisterReceiver(bluetoothAdapterStateChangeListener)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        scanMenuItem = menu.findItem(R.id.menu_item_scan)
        setScanButtonState()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setScanButtonState() {
        scanMenuItem?.isEnabled = isLocationEnabled() && isBluetoothEnabled && isAccessFineLocationGranted()

        if (isScanning) {
            scanMenuItem?.title = getString(R.string.button_stop)
        } else {
            scanMenuItem?.title = getString(R.string.button_scan)
        }
    }

    private fun getDeviceDetailsIntent(device: BluetoothDevice): Intent {
        return Intent(this, DeviceDetailsActivity::class.java).apply {
            putExtra("BLUETOOTH_DEVICE", device)
            putExtra("DeviceName", device.name)
            putExtra("DeviceAddress", device.address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    override fun connectToDevice(deviceData: Map<String, String>) {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
            BGXpressService.startActionStopScan(this@DeviceListActivity)
            val intent = Intent(this@DeviceListActivity, IndeterminateProgressActivity::class.java).apply {
                putExtra("DeviceAddress", deviceData["uuid"])
                putExtra("DeviceName", deviceData["name"])
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.toast_bluetooth_not_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_LOCATION = 1234
    }
}