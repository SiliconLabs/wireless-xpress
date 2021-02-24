package com.silabs.bgxcommander.dialogs

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog

class DialogUtils {
    fun getInvalidGattHandlesDialog(context: Context, deviceName: String): Dialog {
        return AlertDialog.Builder(context).apply {
            setTitle("Invalid GATT Handles")
            setMessage("The bonding information on this device is invalid (probably due to a firmware update). You should select $deviceName in the Bluetooth Settings and choose \"Forget\".")
            setPositiveButton("Okay") { _, _ -> }
        }.create()
    }

    fun getMissingDMSKeyDialog(context: Context): Dialog {
        return AlertDialog.Builder(context).apply {
            setTitle("MISSING_KEY")
            setMessage("The DMS_API_KEY supplied in your app's AndroidManifest.xml is missing. Contact Silicon Labs xpress@silabs.com for a DMS API Key for BGX.")
            setPositiveButton("OK") { _, _ -> }
        }.create()
    }

    fun showForgetBluetoothDeviceDialog(context: Context, deviceName: String, finishOta: () -> Unit): Dialog {
        return AlertDialog.Builder(context).apply {
            setTitle("Important")
            setMessage("You should select $deviceName in the Bluetooth Settings on any paired devices and choose \"Forget\" and then turn Bluetooth off and back on again for correct operation.")
            setPositiveButton("Okay") { _, _ -> finishOta.invoke() }
        }.create()
    }
}