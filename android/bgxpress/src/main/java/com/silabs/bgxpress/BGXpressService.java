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

package com.silabs.bgxpress;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.util.Log;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

import androidx.core.app.JobIntentService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.net.URL;
import java.lang.String;
import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.net.ssl.HttpsURLConnection;

import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

import static com.silabs.bgxpress.BGX_CONNECTION_STATUS.INTERROGATING;
import static java.lang.Math.toIntExact;


/**
 * The Silicon Labs BGX device provides a bridge between Bluetooth Low Energy (BLE) and Serial communication.
 *
 * <p>BGXpressService is an Android Job Intent Service which makes it easier to write an Android app which interacts with BGX.
 * It supports discovery of BGX devices, connecting and disconnecting, setting and detecting changes to the bus mode of the BGX,
 * sending and receiving serial data, and OTA (Over The Air) Firmware Updates of the BGX accessed through
 * the Silicon Labs DMS (Device Management Service).
 *
 * <p>It is designed to target Android SDK 30 and supports a minimum SDK version 28.
 *
 * <p>BGXpressService api usage (Connect to device example):
 * For most actions you can use methods starting with "startActionBGX".</br>
 * <p>
 * Intent intent = new Intent(context, BGXpressService.class);</br>
 * intent.setAction(ACTION_BGX_CONNECT);</br>
 * intent.putExtra("DeviceAddress", deviceAddress);</br>
 * BGXpressService.enqueueWork(context, intent);</br>
 *
 * <p> It is suggested to use: startActionBGXConnect() which does
 * the same what above code, but allows you to have your code much more elegant.
 */

public class BGXpressService extends JobIntentService {

    /* BGX Scanning Actions */
    /**
     * The BGXpressService receives this intent and it begins scanning.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionStartScan} instead.
     */
    public static final String ACTION_START_SCAN = "com.silabs.bgx.action.StartScan";

    /**
     * The BGXpressService receives this intent and it stops scanning.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionStopScan} instead.
     */
    public static final String ACTION_STOP_SCAN = "com.silabs.bgx.action.StopScan";

    /* BGX Connection Actions */
    /**
     * This is sent to cause the BGXpressService to connect to a BGX.
     *
     * <p>Extras:
     * DeviceAddress - String - The bluetooth address of the device to which to connect.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXConnect} instead.
     */
    public static final String ACTION_BGX_CONNECT = "com.silabs.bgx.action.Connect";

    /**
     * This is sent to cause the BGXpressService to disconnect from a connected BGX.
     *
     * <p>Extras:
     * DeviceAddress - String - The bluetooth address of the device you want to disconnect from.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXDisconnect} instead.
     */
    public static final String ACTION_BGX_DISCONNECT = "com.silabs.bgx.action.Disconnect";

    /**
     * Attempt to cancel an in-progress connection operation.
     *
     * <p>Extras:
     * DeviceAddress - String - The bluetooth address of the device you want to cancel connection.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXCancelConnect} instead.
     */
    public static final String ACTION_BGX_CANCEL_CONNECTION = "com.silabs.bgx.action.CancelConnection";

    /* BGX Serial Actions */
    /**
     * Reads the bus mode. When the read operation completes, a broadcast intent
     * will be sent with the action BGX_MODE_STATE_CHANGE.
     *
     * <p>Extras:
     * DeviceAddress - String - The address of the device on which to read the bus mode.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXReadBusMode} instead.
     */
    public static final String ACTION_READ_BUS_MODE = "com.silabs.bgx.action.ReadBusMode";

    /**
     * Writes the bus mode. The service will not send a notification to indicate that
     * it succeeded at this time.
     *
     * <p>Extras:
     * busmode - int - One of the values from BusMode: STREAM_MODE, LOCAL_COMMAND_MODE, or REMOTE_COMMAND_MODE.
     * DeviceAddress - String - The address of the device on which to set the bus mode.
     * password - String - If supplied, this value will be used as the password for REMOTE_COMMAND_MODE.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXWriteBusMode} instead.
     */
    public static final String ACTION_WRITE_BUS_MODE = "com.silabs.bgx.action.WriteBusMode";

    /**
     * Writes text message to the BGX.
     *
     * <p>Extras:
     * value - String - The data to be written.
     * DeviceAddress - String - The device to which the operation pertains.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXWriteMessage} instead.
     */
    public static final String ACTION_WRITE_SERIAL_DATA = "com.silabs.bgx.action.WriteSerialData";

    /**
     * Writes array of bytes to the BGX.
     *
     * <p>Extras:
     * value - byte [] - The byte array to be written.
     * DeviceAddress - String - The device to which the operation pertains.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXWriteByteArray} instead.
     */
    public static final String ACTION_WRITE_SERIAL_BIN_DATA = "com.silabs.bgx.action.WriteSerialBinData";

    /* BGX Misc Actions */
    /**
     * This request causes the BGXpressService to read the device uuid
     * and determine the part id. The result is sent out as a broadcast
     * intent BGX_DEVICE_INFO with the following extras:
     *
     * <p>Extras:
     * bgx-device-uuid - String - A string containing the device uuid.
     * bgx-part-id - BGXPartID - An enum value from BGXPartID.
     * bgx-part-identifier - String - An 8 character string identifying the type of BGX part.
     * bgx-platform-identifier - String - Currently BGX13 or BGX220.
     */
    private static final String ACTION_BGX_GET_INFO = "com.silabs.bgx.action.GetInfo";

    /**
     * Gets available firmware versions.
     *
     * <p>Extras:
     * bgx-part-identifier - String - An 8 character string identifying the type of BGX.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXGetFirmwareVersions} instead.
     */
    public static final String ACTION_GET_FIRMWARE_VERSIONS = "com.silabs.bgx_action.GetFirmwareVersions";

    /* BGX DMS Actions */
    /**
     * Gets the firmware versions available from DMS.
     * Either bgx-part-id or bgx-part-identifier (preferred) must be specified.
     * bgx-part-identifier is used if both are specified.
     *
     * <p>Extras:
     * bgx-part-id - BGXPartID - An enum value from BGXPartID. Preserved for compatibility.
     * bgx-part-identifier - String - An 8 character string identifying the type of BGX (preferred).
     * bgx-platform-identifier - String - (optional) A string identifying the bgx platform. Currently
     * "bgx13" or "bgx220". In the future there could be other values. This could be read
     * from the beginning of the version string of the version characteristic. However,
     * if you do not specify this, then the platform will be determined using the
     * bgx-part-identifier for a list of known platform identifiers for each platform.
     *
     * <p>ACTION_DMS_GET_VERSIONS is deprecated. Please use {@link #ACTION_GET_FIRMWARE_VERSIONS} instead.
     */
    @Deprecated
    public static final String ACTION_DMS_GET_VERSIONS = "com.silabs.bgx.action.GetDMSVersions";

    /**
     * This request causes the specified DMS version to be loaded onto the phone.
     * When finished, the broadcast intent DMS_VERSION_LOADED is sent.
     * Either bgx-part-id or bgx-part-identifier (preferred) must be specified.
     * bgx-part-identifier is used if both are specified.
     *
     * <p>Extras:
     * bgx-part-id - BGXPartID - Optional one of the following values: BGX13S or BGX13P (Preserved for compatibility).
     * bgx-part-identifier - String - An 8 character string identifying the kind of BGX (Preferred).
     * dms-version - String - The DMS version number you want to retrieve.
     */
    @Deprecated
    public static final String ACTION_DMS_REQUEST_VERSION = "com.silabs.bgx.action.RequestDMSVersion";

    /* BGX OTA Actions */
    /**
     * Start Ota process.
     *
     * <p>Extras:
     * image_path - String - Path to the image file. You could get this from a DMS_VERSION_LOADED broadcast intent.
     * DeviceAddress - String - The address of the device to update.
     * password - String - The password to use for the OTA update if one is set.
     * writeType - Int - Optional - Either BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT or WRITE_TYPE_NO_RESPONSE.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXOtaWithImage} instead.
     *
     * <p>ACTION_OTA_WITH_IMAGE is deprecated. Please use {@link #ACTION_OTA_FIRMWARE_IMAGE} instead.
     */
    @Deprecated
    public static final String ACTION_OTA_WITH_IMAGE = "com.silabs.bgx.action.OTA";

    /**
     * Start Ota process.
     *
     * <p>Extras:
     * image_path - String - Path to the image file.
     * DeviceAddress - String - The address of the device to update.
     * password - String - The password to use for the OTA update if one is set.
     * writeType - Int - Optional - Either BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT or WRITE_TYPE_NO_RESPONSE.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXOtaFirmwareImage} instead.
     */
    public static final String ACTION_OTA_FIRMWARE_IMAGE = "com.silabs.bgx.action.OTA.firmware.image";

    /**
     * Attempts to cancel an OTA operation in progress. If the cancellation is successful you will
     * get an OTA_STATUS_MESSAGE where the ota_status is UserCanceled.
     *
     * <p>Extras:
     * DeviceAddress - String - Address of the device for the connecting operation to cancel.
     *
     * <p>There is no need to directly send this. You can call {@link #startActionBGXCancelOta} instead.
     */
    public static final String ACTION_OTA_CANCEL = "com.silabs.bgx.action.OTA.cancel";

    /**
     * Sent to indicate a connection status change.
     *
     * <p>Extras:
     * bgx-connection-status - BGX_CONNECTION_STATUS - A value from the BGX_CONNECTION_STATUS enum which is the new status.
     * device - BluetoothDevice - A BluetoothDevice (not present for DISCONNECTED).
     * DeviceAddress - String - Address of the BGX.
     * bonded - boolean - Indicating whether bonding has taken place. Only applicable for INTERROGATING and CONNECTED states. Default is false.
     */
    public static final String BGX_CONNECTION_STATUS_CHANGE = "com.silabs.bgx.intent.connection-status-change";

    /**
     * Broadcast Intent which indicates a state change in the bgxss mode.
     *
     * <p>Extras:
     * busmode - int - One of the values from BusMode: STREAM_MODE, LOCAL_COMMAND_MODE, or REMOTE_COMMAND_MODE.
     * DeviceAddress - String - The address of the device.
     */
    public static final String BGX_MODE_STATE_CHANGE = "com.silabs.bgx.intent.mode-state-change";

    /**
     * This intent is sent when a password is required to change the bus mode to remote
     * command mode.
     *
     * <p>Extras:
     * DeviceAddress - String - The address of the device.
     */
    public static final String BUS_MODE_ERROR_PASSWORD_REQUIRED = "com.silabs.bgx.busmode.password.required";

    /**
     * Indicates that data was received.
     *
     * <p>Extras:
     * data - String - Contains the data that was received from the BGX.
     * DeviceAddress - String - The address of the BGX that received data.
     */
    public static final String BGX_DATA_RECEIVED = "com.silabs.bgx.intent.data-received";

    /**
     * This change is sent when scanning begins or ends.
     *
     * <p>Extras:
     * isscanning - boolean - Indicates whether scanning is happening.
     * scanFailed - boolean - (option) true to indicate that scan failed. Default is false.
     * error - int - (optional) If the scan failed this is the error code received.
     */
    public static final String BGX_SCAN_MODE_CHANGE = "com.silabs.bgx.intent.scan-mode-change";

    /**
     * Indicates an MTU change was received.
     *
     * <p>Extras:
     * deviceAddress - String - Device address
     * status - int - Operation status
     * mtu - int - The new mtu size
     */
    public static final String BGX_MTU_CHANGE = "com.silabs.bgx.intent.mtu-change";

    /**
     * This is sent as a broadcast intent when a BGX device is discovered during scanning.
     *
     * <p>DeviceRecord is a HashMap containing information about the device that was discovered.
     * The HashMap contains following keys:
     *
     * <p>Extras:
     * name - String - Device name
     * uuid - String -  Device address
     * rssi - String -  The device's current RSSI
     */
    public static final String BGX_SCAN_DEVICE_DISCOVERED = "com.silabs.bgx.intent.scan-device-discovered";

    /**
     * Notification that a specific version has been loaded and is attached to the intent as "file_path".
     *
     * <p>Extras:
     * file_path - String - The path to the file containing the version.
     */
    @Deprecated
    public static final String DMS_VERSION_LOADED = "com.silabs.bgx.intent.dms-version-loaded";

    /**
     * <p>Indicates a change in the OTA status.
     *
     * <p>Extras:
     * ota_failed - boolean - True means it failed. Use false as the default.
     * ota_status - OTA_Status enum - Tells the current status of the OTA. Use Invalid as the default.
     * bytes_sent - int - Number of bytes transferred in the firmware image. Not always present.
     */
    public static final String OTA_STATUS_MESSAGE = "com.silabs.bgx.ota.status";

    /**
     * Notification message that is sent when invalid GATT handles are detected.
     * Invalid GATT handles result in operations failing to work correctly.
     * Invalid GATT handles occur as a result of a firmware update that adds or removes
     * Bluetooth characteristics and/or services to the BGX when you have an existing bond
     * on your Android device. To fix this condition, you would need to go to
     * the Bluetooth Settings on your Android device, select the BGX, and choose "Forget".
     * The next time you connect, the bond will be reestablished.
     * This broadcast intent will be sent when the BGXpressService is unable to read a valid
     * part id or when it is unable to read a valid firmware version. Your app should respond
     * to this broadcast intent by showing the user a message explaining the situation and
     * telling them how to recover from it.
     *
     * <p>This broadcast intent contains the following extras:
     *
     * <p>Extras:
     * DeviceAddress - String - The address of the BGX device.
     * DeviceName - String - The name of the BGX device.
     */
    public static final String BGX_INVALID_GATT_HANDLES = "com.silabs.bgx.gatt.invalid";

    /**
     * A value that indicates the type of part.
     */
    public enum BGXPartID {
        BGX13S,
        BGX13P,
        BGXV3S,
        BGXV3P,
        BGX220S,
        BGX220P,
        BGXUnknownPartID,
        BGXInvalid
    }

    /**
     * UUID prefix for the BGX13S.
     */
    private static final String BGX13S_Device_Prefix = "080447D0";
    /**
     * UUID prefix for the BGX13P.
     */
    private static final String BGX13P_Device_Prefix = "4C892A6A";
    /**
     * UUID Prefix for BGX13 V3 S Part.
     */
    private static final String BGXV3S_Device_Prefix = "F65FD7F0";
    /**
     * UUID Prefix for BGX13 V3 P Part.
     */
    private static final String BGXV3P_Device_Prefix = "76786556";
    /**
     * UUID Prefix for BGX220 P Part.
     */
    private static final String BGX220P_Device_Prefix = "CF07449C";
    /**
     * UUID Prefix for BGX220 S Part.
     */
    private static final String BGX220S_Device_Prefix = "9C1F257E";
    /**
     * UUID prefix for invalid device.
     */
    private static final String BGX_Invalid_Device_Prefix = "BAD1DEAD";

    /**
     * This is sent when connection state becomes Connected for the device.
     *
     * <p>Extras:
     * bgx-device-uuid - String - The UUID of the BGX device.
     * bgx-part-id - String - A BGXPartID
     * bgx-part-identifier - String - An 8 character String that identifies the type of BGX.
     */
    public static final String BGX_DEVICE_INFO = "com.silabs.bgx.intent.device-info";

    /**
     * This is sent when the BGXConnectionState moves from Connecting to Disconnecting
     * due to an error.
     *
     * <p>Extras:
     * status - int - A GATT Status value as returned by the Android GATT API.
     */
    public static final String BGX_CONNECTION_ERROR = "com.silabs.bgx.intent.connection_error";

    /**
     * Broadcast intent containing list of available firmware versions.
     *
     * <p>Extras:
     * versions-available-json - String - Contains JSON with the list of firmware versions available.
     */
    public static final String FIRMWARE_VERSIONS_AVAILABLE = "com.silabs.bgx.firmware-versions-available";

    /**
     * Broadcast intent sent when the list of versions is retrieved from DMS.
     *
     * <p>Extras:
     * versions-available-json - String - Contains JSON response from DMS with the list of firmware versions available.
     */
    @Deprecated
    public static final String DMS_VERSIONS_AVAILABLE = "com.silabs.bgx.dms.versions-available";

    /**
     * Extras:
     * mtu - Integer - The MTU to request. Default value is 250.
     * DeviceAddress - String - The address of the device to perform the operation.
     */
    private static final String ACTION_REQUEST_MTU = "com.silabs.bgx.request_mtu";

    /**
     * DMS URL for BGX13S firmware versions.
     */
    @Deprecated
    private static final String DMS_VERSIONS_URL_S = "https://xpress-api.zentri.com/platforms/bgx13s_v2/products/bgx13/versions";
    /**
     * DMS URL for BGX13P firmware versions.
     */
    @Deprecated
    private static final String DMS_VERSIONS_URL_P = "https://xpress-api.zentri.com/platforms/bgx13p_v2/products/bgx13/versions";
    /**
     * Brief description of whatever this is.
     */
    private final static char[] kHexArray = "0123456789ABCDEF".toCharArray();

    /**
     * A private enum used to track the internal state of an OTA update operation.
     */
    private enum OTA_State {
        OTA_Idle,
        WrittenZeroToControlCharacteristic,
        WritingOTAImage,
        WriteThreeToControlCharacteristic
    }

    /**
     * These are internal actions for intents that are queued internally to handle BGX setup operations.
     */
    private static final String ACTION_ENABLE_MODE_CHANGE_NOTIFICATION = "com.silabs.bgx.mode.notification.setup";
    private static final String ACTION_ENABLE_TX_CHANGE_NOTIFICATION = "com.silabs.bgx.tx.notification.setup";
    private static final String ACTION_SET_2M_PHY = "com.silabs.bgx.set2mphy";
    private static final String ACTION_READ_FIRMWARE_REVISION = "com.silabs.bgx.read_firmware_revision";
    private static final String ACTION_SETUP_FAST_ACK = "com.silabs.bgx.setup_fast_ack";
    private static final String ACTION_UPDATE_FAST_ACK_RX_BYTES = "com.silabs.bgx.rxbytes";
    private static final String ACTION_SET_WRITE_TYPE = "com.silabs.bgx.setWriteType";
    private static final String ACTION_SET_READ_TYPE = "com.silabs.bgx.setReadType";
    private static final String ACTION_POLL_BOND_STATUS = "com.silabs.bgx.pollBondStatus";

    @Deprecated
    private String getDmsAPIKey() {
        String api_key = null;

        try {
            ComponentName myService = new ComponentName(this, this.getClass());
            Bundle data = getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;
            if (null != data) {
                api_key = data.getString("DMS_API_KEY");
            }
        } catch (PackageManager.NameNotFoundException exception) {
            exception.printStackTrace();
        }

        if (null == api_key || 0 == api_key.length()) {
            Log.w("Warning", "The DMS_API_KEY supplied in your app's AndroidManifest.xml is blank. Contact Silicon Labs xpress@silabs.com for a DMS API Key for BGX.");
        } else if (0 == api_key.compareTo("MISSING_KEY")) {
            Log.w("Warning", "The DMS_API_KEY supplied in your app's AndroidManifest.xml is missing. Contact Silicon Labs xpress@silabs.com for a DMS API Key for BGX.");
        }

        return api_key;
    }

    public class ScanProperties {
        private List<ScanFilter> filters;
        private ScanSettings settings;
        private BluetoothLeScanner mLEScanner;
        private ArrayList<BluetoothDevice> mScanResults;

        private ScanCallback mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice btDevice = result.getDevice();
                String btDeviceName = btDevice.getName();

                if (null != btDeviceName && !mScanResults.contains(btDevice)) {
                    mScanResults.add(btDevice);

                    Intent broadcastIntent = new Intent();
                    HashMap<String, String> deviceRecord = new HashMap<>();

                    deviceRecord.put("name", btDeviceName);
                    deviceRecord.put("uuid", btDevice.getAddress());
                    deviceRecord.put("rssi", "" + result.getRssi());

                    broadcastIntent.setAction(BGX_SCAN_DEVICE_DISCOVERED);
                    broadcastIntent.putExtra("DeviceRecord", deviceRecord);

                    sendBroadcast(broadcastIntent);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("bgx_dbg", "Scan Failed. Error Code: " + errorCode);
                Intent intent = new Intent();
                intent.setAction(BGX_SCAN_MODE_CHANGE);
                intent.putExtra("isscanning", false);
                intent.putExtra("scanFailed", true);
                intent.putExtra("error", errorCode);
                sendBroadcast(intent);
            }
        };

        ScanProperties() {
            this.mScanResults = new ArrayList<>();

            this.mLEScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();

            this.filters = new ArrayList<>();
            ScanFilter.Builder sb = new ScanFilter.Builder();
            sb.setServiceUuid(new ParcelUuid(UUID.fromString("331a36f5-2459-45ea-9d95-6142f0c4b307")));

            this.filters.add(sb.build());

            this.settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        }
    }

    static private ScanProperties mScanProperties = null;

    public class DeviceProperties {

        DeviceProperties() {
            this.dataWriteSync = new Object();
            this.m2mphyRunnable = null;
            this.fUserConnectionCanceled = false;
            this.fGattBusy = false;
            this.mIntentArray = new ArrayList<>();
            this.mOTAState = OTA_State.OTA_Idle;
            this.fOTAUserCanceled = false;
            this.mBootloaderVersion = -1;
            this.mFirmwareRevisionString = null;
            this.deviceWriteChunkSize = kDataWriteChunkDefaultSize;
            this.mMTUInitialReadComplete = false;
            this.mLastBondState = BOND_NONE;
            this.mFastAckRxBytesToReturn = 0;
        }

        private BluetoothGatt mBluetoothGatt;

        private String partIdentifier;
        private String deviceIdentifier;

        private BGX_CONNECTION_STATUS mBGXDeviceConnectionState;
        private int mDeviceConnectionState;

        private BluetoothGattService mBGXSS;
        private BluetoothGattCharacteristic mRxCharacteristic;
        private BluetoothGattCharacteristic mRxCharacteristic2;

        private BluetoothGattCharacteristic mTxCharacteristic;
        private BluetoothGattCharacteristic mTxCharacteristic2;

        private BluetoothGattCharacteristic mBGXSSModeCharacteristic;


        private BluetoothGattService mDeviceInfoService;
        private BluetoothGattCharacteristic mFirmwareRevisionCharacteristic;

        private BluetoothGattService mOTAService;
        private BluetoothGattCharacteristic mOTAControlCharacteristic;
        private BluetoothGattCharacteristic mOTADataCharacteristic;
        private BluetoothGattCharacteristic mOTADeviceIDCharacterisitc;

        private boolean mMTUInitialReadComplete;
        private int deviceWriteChunkSize;

        private volatile Boolean fUserConnectionCanceled;

        private int mLastBondState; // used during polling the bond state to detect state changes.

        /**
         * Runnable
         */
        private Runnable m2mphyRunnable;

        /**
         * Firmware & Bootloader Instance Variables
         */
        private Integer mBootloaderVersion;
        private String mFirmwareRevisionString;
        private String mPlatformString; // identifies the bgx platform: e.g. bgx13, bgx220

        /**
         * Variables related to OTA.
         */
        private OTA_State mOTAState;
        private boolean fOTAInProgress;
        private boolean fOTAUserCanceled;

        /**
         * These variables are used to write data a bit at a time.
         */
        private Object dataWriteSync; // Used only to synchronize access to mData2Write
        private byte[] mData2Write;
        private int mWriteOffset;

        /**
         * FastAck variables.
         */
        private boolean mFastAck; // does the device support fastAck?
        private int mFastAckRxBytes;
        private int mFastAckTxBytes;
        private int mFastAckRxBytesToReturn;

        private final Integer kInitialFastAckRxBytes = 0x7FFF;
        private final Integer kFastAckRxReturnThreshold = 0x1000;

        private BroadcastReceiver mBroadcastReceiver;

        /**
         * mIntentQueue
         * The purpose of this is to solve a problem where the
         * BluetoothGatt cannot perform more than one asynchronous
         * operation at a time.
         *
         * <p>Some intents are for operations that require either a read
         * or write operation on the BluetoothGatt object. Handling
         * more than one at a time will result in failure. So we will
         * queue intents that require a read or write.
         *
         * <p>queueGattIntent()
         * Queues an intent for asynchronous execution when exclusive access
         * of the GATT is guaranteed.
         *
         * <p>clearGattBusyFlagAndExecuteNext()
         * Called when the current operation is finished with the GATT.
         *
         * <p>executeNextGattIntent()
         * Retrieves a GATT intent from the queue and then executes the operation
         * and guarantees exclusive access to the GATT.
         */

        private ArrayList<Intent> mIntentArray;
        private volatile boolean fGattBusy;
        private Intent mLastExecutedIntent;

        /**
         * Clear GattBusy flag and execute next Intent from queue.
         */
        private void clearGattBusyFlagAndExecuteNext() {
            if (fOTAInProgress) {
                Log.d("bgx_dbg", "clearGattBusyFlagAndExecuteNext called during fOTAInProgress");
                return;
            }

            if (fGattBusy) {
                fGattBusy = false;

                int sz;
                synchronized (this) {
                    sz = mIntentArray.size();
                }

                if (sz > 0) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            executeNextGattIntent();
                        }
                    });
                }
            }
        }

        /**
         * Add Gatt Intent to queue.
         *
         * @param intent - Gatt Intent which is added to queue.
         */
        private void queueGattIntent(Intent intent) {
            synchronized (this) {
                mIntentArray.add(intent);
            }

            if (!fGattBusy) {
                boolean fresult = mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        executeNextGattIntent();
                    }
                });

                if (!fresult) {
                    Log.e("bgx_dbg", "Error posting runnable.");
                } else {
                    Log.d("bgx_dbg", "Post succeeded of " + intent.getAction() + ".");
                }
            }
        }

        /**
         * Clear Gatt Intent queue.
         */
        private void clearGattQueue() {
            synchronized (this) {
                mIntentArray.clear();
                fGattBusy = false;
            }
        }

        /**
         * Execute Intent from Gatt Intent queue.
         */
        private void executeNextGattIntent() {
            boolean executeAnother = false;
            if (!fGattBusy) {
                synchronized (this) {
                    if (mIntentArray.size() > 0) {
                        Intent intent = mIntentArray.remove(0);
                        mLastExecutedIntent = intent;
                        fGattBusy = true;

                        Log.d("bgx_dbg", "Executing " + mLastExecutedIntent.getAction() + " from GATT queue.");

                        switch (intent.getAction()) {
                            case ACTION_WRITE_BUS_MODE: {
                                int busMode = intent.getIntExtra("busmode", BusMode.UNKNOWN_MODE);

                                String password = intent.getStringExtra("password");
                                if (null == password) {
                                    password = "";
                                }

                                byte[] modevalue;

                                // only send the password + null if the password is supplied because
                                //  writing more than one byte on a version of BGX firmware < 1.2 causes an error
                                if (BusMode.REMOTE_COMMAND_MODE == busMode && password.length() > 0) {
                                    modevalue = new byte[password.length() + 2];
                                    modevalue[0] = (byte) busMode;

                                    for (int i = 0; i < password.length(); ++i) {
                                        modevalue[1 + i] = password.getBytes()[i];
                                    }

                                    modevalue[1 + password.length()] = 0;
                                } else {
                                    modevalue = new byte[1];
                                    modevalue[0] = (byte) busMode;
                                }

                                this.mBGXSSModeCharacteristic.setValue(modevalue);

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_WRITE_BUS_MODE: BluetoothGatt is null");
                                    return;
                                }

                                boolean result = mBluetoothGatt.writeCharacteristic(this.mBGXSSModeCharacteristic);
                                if (!result) {
                                    Log.e("bgx_dbg", "mBGXSSModeCharacteristic write failed.");
                                }
                            }
                            break;
                            case ACTION_WRITE_SERIAL_DATA: {
                                synchronized (dataWriteSync) {
                                    try {
                                        String string2Write = intent.getStringExtra("value");
                                        if (null == mData2Write) {
                                            mData2Write = string2Write.getBytes();
                                            mWriteOffset = 0;
                                        } else {
                                            ByteArrayOutputStream output = new ByteArrayOutputStream();

                                            output.write(mData2Write);
                                            output.write(string2Write.getBytes());

                                            mData2Write = output.toByteArray();
                                            Log.d("bgx_dbg", "Queued " + string2Write.length() + "bytes Total: " + output.size() + " bytes");
                                        }
                                    } catch (IOException exception) {
                                        Log.e("bgx_dbg", exception.getLocalizedMessage());
                                    }
                                }
                                writeChunkOfData();
                            }
                            break;
                            case ACTION_WRITE_SERIAL_BIN_DATA: {
                                mData2Write = intent.getByteArrayExtra("value");
                                mWriteOffset = 0;
                                writeChunkOfData();
                            }
                            break;
                            case ACTION_READ_BUS_MODE: {
                                assert (null != this.mBGXSSModeCharacteristic);

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_READ_BUS_MODE: BluetoothGatt is null");
                                    return;
                                }

                                boolean result = mBluetoothGatt.readCharacteristic(this.mBGXSSModeCharacteristic);
                                if (!result) {
                                    Log.e("bgx_dbg", "mBluetoothGatt.readCharacteristic failed for ACTION_READ_BUS_MODE.");
                                    queueGattIntent(new Intent(BGXpressService.ACTION_READ_BUS_MODE));
                                }
                            }
                            break;
                            case ACTION_BGX_GET_INFO: {
                                if (null != this.mOTADeviceIDCharacterisitc) {
                                    if (mBluetoothGatt == null) {
                                        Log.e("bgx_dbg", "ACTION_BGX_GET_INFO: BluetoothGatt is null");
                                        return;
                                    }

                                    boolean readResult = mBluetoothGatt.readCharacteristic(this.mOTADeviceIDCharacterisitc);
                                    if (!readResult) {
                                        Log.e("bgx_dbg", "Read initiation failed.");
                                        queueGattIntent(new Intent(BGXpressService.ACTION_BGX_GET_INFO));
                                    } else {
                                        Log.d("bgx_dbg", "Began reading the OTADeviceIDCharacteristic");
                                    }
                                } else {
                                    Log.e("bgx_dbg", "ERROR: ACTION_BGX_GET_INFO failed because mOTADeviceIDCharacteristic is null.");
                                }
                            }
                            break;
                            case ACTION_OTA_WITH_IMAGE: {
                                fOTAUserCanceled = false;
                                fOTAInProgress = true;
                                String image_path = intent.getStringExtra("image_path");
                                String password = intent.getStringExtra("password");
                                int theWriteType = intent.getIntExtra("writeType", WRITE_TYPE_DEFAULT);

                                mOTADataCharacteristic.setWriteType(theWriteType);
                                handleActionOTAWithImage(image_path, password);
                            }
                            break;
                            case ACTION_OTA_FIRMWARE_IMAGE: {
                                fOTAUserCanceled = false;
                                fOTAInProgress = true;
                                String image_path = intent.getStringExtra("image_path");
                                String password = intent.getStringExtra("password");
                                int theWriteType = intent.getIntExtra("writeType", WRITE_TYPE_DEFAULT);

                                mOTADataCharacteristic.setWriteType(theWriteType);
                                handleActionOtaFirmwareImage(image_path, password);
                            }
                            break;
                            case ACTION_ENABLE_MODE_CHANGE_NOTIFICATION: {
                                assert (null != mBGXSSModeCharacteristic);
                                BluetoothGattDescriptor desc = mBGXSSModeCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

                                assert (null != desc);
                                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_ENABLE_MODE_CHANGE_NOTIFICATION: BluetoothGatt is null");
                                    return;
                                }

                                boolean fresult = mBluetoothGatt.writeDescriptor(desc);
                                if (!fresult) {
                                    Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for BGXSSMode.");
                                    queueGattIntent(new Intent(BGXpressService.ACTION_ENABLE_MODE_CHANGE_NOTIFICATION));
                                } else {
                                    fresult = mBluetoothGatt.setCharacteristicNotification(mBGXSSModeCharacteristic, true);
                                    if (!fresult) {
                                        Log.e("bgx_dbg", "Failed to set characterisitic notification for bgxss mode.");
                                        queueGattIntent(new Intent(BGXpressService.ACTION_ENABLE_MODE_CHANGE_NOTIFICATION));
                                    }
                                }
                            }
                            break;
                            case ACTION_ENABLE_TX_CHANGE_NOTIFICATION: {
                                assert (null != mTxCharacteristic);
                                BluetoothGattDescriptor desc = mTxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_ENABLE_TX_CHANGE_NOTIFICATION: BluetoothGatt is null");
                                    return;
                                }

                                boolean fresult = mBluetoothGatt.writeDescriptor(desc);
                                if (!fresult) {
                                    Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for Tx. (1)");
                                    queueGattIntent(new Intent(BGXpressService.ACTION_ENABLE_TX_CHANGE_NOTIFICATION));
                                }
                                mBluetoothGatt.setCharacteristicNotification(mTxCharacteristic, true);
                            }
                            break;
                            case ACTION_SET_2M_PHY: {
                                if (Build.VERSION.SDK_INT >= 26) {
                                    if (mBluetoothGatt == null) {
                                        Log.e("bgx_dbg", "ACTION_SET_2M_PHY: BluetoothGatt is null");
                                        return;
                                    }

                                    mBluetoothGatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED);

                                    m2mphyRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            m2mphyRunnable = null;
                                            clearGattBusyFlagAndExecuteNext();
                                        }
                                    };
                                    mHandler.postDelayed(m2mphyRunnable, 2500);
                                }
                            }
                            break;
                            case ACTION_READ_FIRMWARE_REVISION: {
                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_READ_FIRMWARE_REVISION: BluetoothGatt is null");
                                    return;
                                }

                                if (!mBluetoothGatt.readCharacteristic(mFirmwareRevisionCharacteristic)) {
                                    Log.d("bgx_dbg", "Read FirmwareRevisionCharacteristic failed.");
                                }
                            }
                            break;
                            case ACTION_REQUEST_MTU: {
                                int mtu;
                                mtu = intent.getIntExtra("mtu", 250);

                                if (mBluetoothGatt != null) {
                                    if (!mBluetoothGatt.requestMtu(mtu)) {
                                        Log.d("bgx_dbg", "Error: requestMTU returned false.");
                                    } else {
                                        Log.d("bgx_dbg", "Called mBluetoothGatt.requestMtu(" + mtu + ")");
                                    }
                                } else {
                                    executeAnother = true;
                                }
                            }
                            break;
                            case ACTION_SETUP_FAST_ACK: {
                                if ((mRxCharacteristic.getProperties() & PROPERTY_NOTIFY) != 0) {
                                    Log.d("bgx_fastAck", "fastAck: YES");

                                    BluetoothGattDescriptor desc = mRxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                                    if (mBluetoothGatt == null) {
                                        Log.e("bgx_dbg", "ACTION_SETUP_FAST_ACK: BluetoothGatt is null");
                                        return;
                                    }

                                    boolean fresult = mBluetoothGatt.writeDescriptor(desc);
                                    if (!fresult) {
                                        Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for Rx.");
                                    }

                                    mBluetoothGatt.setCharacteristicNotification(mRxCharacteristic, true);

                                    mFastAck = true;
                                    mFastAckRxBytes = 0;
                                    mFastAckTxBytes = 0;
                                    mFastAckRxBytesToReturn = 0;

                                    updateFastAckRxBytes(0, kInitialFastAckRxBytes);
                                } else {
                                    Log.d("bgx_fastAck", "fastAck: NO");

                                    mFastAck = false;
                                    fGattBusy = false;
                                    executeAnother = true;
                                }
                            }
                            break;
                            case ACTION_UPDATE_FAST_ACK_RX_BYTES: {
                                BluetoothGattCharacteristic txChar;

                                if (null == mTxCharacteristic2) {
                                    txChar = mTxCharacteristic;
                                } else {
                                    txChar = mTxCharacteristic2;
                                }

                                if (null == txChar) {
                                    Log.e("bgx_dbg", "Error: Tx Characteristic could not be found.");
                                }

                                int opcode = intent.getIntExtra("opcode", -1);
                                int rxbytes = intent.getIntExtra("rxbytes", 0);

                                byte[] val = new byte[3];
                                val[0] = (byte) opcode;
                                val[1] = (byte) (rxbytes & 0xFF);
                                val[2] = (byte) ((rxbytes & 0xFF00) >> 8);

                                txChar.setValue(val);

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_UPDATE_FAST_ACK_RX_BYTES: BluetoothGatt is null");
                                    return;
                                }

                                if (mBluetoothGatt.writeCharacteristic(txChar)) {
                                    Log.d("bgx_fastAck", "Write of fastAckRxBytes: SUCCESS.");
                                    switch (opcode) {
                                        case 0x00:
                                            mFastAckRxBytes = rxbytes;
                                            Log.d("bgx_fastAck", "fastAckRxBytes: " + mFastAckRxBytes + " (initial)");
                                            break;
                                        case 0x01:
                                            mFastAckRxBytes += rxbytes;
                                            Log.d("bgx_fastAck", "fastAckRxBytes: " + mFastAckRxBytes + " (added " + rxbytes + ")");
                                            break;
                                    }
                                } else {
                                    Log.d("bgx_fastAck", "Write of fastAckRxBytes: FAIL.");
                                }
                            }
                            break;
                            case ACTION_SET_WRITE_TYPE: {
                                boolean acknowledgedWrites = intent.getBooleanExtra("acknowledgedWrites", false);
                                int writeType;

                                if (acknowledgedWrites && !mFastAck) {
                                    writeType = WRITE_TYPE_DEFAULT;
                                } else {
                                    writeType = WRITE_TYPE_NO_RESPONSE;
                                }

                                mRxCharacteristic.setWriteType(writeType);
                                if (null != mRxCharacteristic2) {
                                    mRxCharacteristic2.setWriteType(writeType);
                                }

                                fGattBusy = false;
                                executeAnother = true;
                            }
                            break;
                            case ACTION_SET_READ_TYPE: {
                                boolean acknowledgedReads = intent.getBooleanExtra("acknowledgedReads", false);

                                BluetoothGattDescriptor desc = mTxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

                                if (acknowledgedReads && !mFastAck) {
                                    desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                } else {
                                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                }

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_SET_READ_TYPE: BluetoothGatt is null");
                                    return;
                                }

                                boolean fresult = mBluetoothGatt.writeDescriptor(desc);
                                if (!fresult) {
                                    Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for Tx. (2)");
                                }

                                mBluetoothGatt.setCharacteristicNotification(mTxCharacteristic, true);
                            }
                            break;
                            case BGX_CONNECTION_STATUS_CHANGE:
                                sendBroadcast(intent);
                                fGattBusy = false;
                                executeAnother = true;
                                break;
                            case ACTION_POLL_BOND_STATUS: {
                                boolean fbonded = false;

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "ACTION_POLL_BOND_STATUS: BluetoothGatt is null");
                                    return;
                                }

                                int bondState = mBluetoothGatt.getDevice().getBondState();
                                switch (bondState) {
                                    case BOND_NONE: {
                                        Log.d("bgx_dbg", "ACTION_POLL_BOND_STATUS: BOND_NONE");

                                        if (mLastBondState != bondState) {
                                            Log.e("bgx_dbg", "Bond state has moved from " + mLastBondState + "to " + bondState + " (might mean bonding totally failed and we need to recover).");
                                        }
                                    }
                                    break;
                                    case BluetoothDevice.BOND_BONDING: {
                                        Log.d("bgx_dbg", "ACTION_POLL_BOND_STATUS: BOND_BONDING");
                                    }
                                    break;
                                    case BluetoothDevice.BOND_BONDED: {
                                        Log.d("bgx_dbg", "ACTION_POLL_BOND_STATUS: BOND_BONDED");
                                        fbonded = true;

                                        Intent broadcastIntent = new Intent();
                                        broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                                        broadcastIntent.putExtra("bgx-connection-status", INTERROGATING);
                                        broadcastIntent.putExtra("device", mBluetoothGatt.getDevice());
                                        broadcastIntent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                                        broadcastIntent.putExtra("bonded", true);
                                        sendBroadcast(broadcastIntent);
                                    }
                                    break;
                                    default:
                                        Log.e("bgx_err", "getBondState returned unknown value.");
                                        break;
                                }
                                mLastBondState = bondState;

                                fGattBusy = false;
                                if (fbonded) {
                                    boolean fResult = mBluetoothGatt.discoverServices();

                                    Log.d("bgx_dbg", "discoverServices: " + (fResult ? "true" : "false"));
                                } else if (!fUserConnectionCanceled) {
                                    mHandler.postAtTime(new Runnable() {
                                        @Override
                                        public void run() {
                                            Intent intent = new Intent();
                                            intent.setAction(ACTION_POLL_BOND_STATUS);
                                            queueGattIntent(intent);
                                        }
                                    }, 500);
                                }
                            }
                            break;
                        }
                    }
                }
            } else {
                Intent nextIntent = null;
                if (mIntentArray.size() > 0) {
                    nextIntent = mIntentArray.get(0);
                }

                String nextAction = "null";

                if (null != nextIntent) {
                    nextAction = nextIntent.getAction();
                }

                Log.d("bgx_dbg", "GattBusy - can't execute. Last intent action: " + mLastExecutedIntent.getAction() + " Next intent action: " + nextAction);
            }

            if (!fGattBusy && executeAnother) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        executeNextGattIntent();
                    }
                });
            }
        }

        /**
         * @param opcode  Either 0x00 or 0x01. 0x00 sends the initial value and 0x01 sends value to add to rxbytes.
         * @param rxbytes The value to send in the second two bytes in the TX backchannel.
         */
        void updateFastAckRxBytes(int opcode, int rxbytes) {
            if (0x01 == opcode) {
                mFastAckRxBytesToReturn += rxbytes;
                if (mFastAckRxBytesToReturn > kFastAckRxReturnThreshold) {
                    Intent intent = new Intent();
                    intent.setAction(ACTION_UPDATE_FAST_ACK_RX_BYTES);
                    intent.putExtra("opcode", opcode);
                    intent.putExtra("rxbytes", mFastAckRxBytesToReturn);
                    queueGattIntent(intent);
                    mFastAckRxBytesToReturn = 0;
                }
            } else if (0x00 == opcode) {
                Intent intent = new Intent();
                intent.setAction(ACTION_UPDATE_FAST_ACK_RX_BYTES);
                intent.putExtra("opcode", opcode);
                intent.putExtra("rxbytes", rxbytes);
                queueGattIntent(intent);
            }
        }

        private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                if (null != m2mphyRunnable) {
                    mHandler.removeCallbacks(m2mphyRunnable);
                    m2mphyRunnable = null;
                }

                if (BluetoothGatt.GATT_SUCCESS == status) {
                    switch (txPhy) {
                        case BluetoothDevice.PHY_LE_1M:
                            Log.d("bgx_dbg", "txPhy: 1M PHY");
                            break;
                        case BluetoothDevice.PHY_LE_2M:
                            Log.d("bgx_dbg", "txPhy: 2M PHY");
                            break;
                        case BluetoothDevice.PHY_LE_CODED:
                            Log.d("bgx_dbg", "txPhy: CODED PHY");
                            break;
                    }

                    switch (rxPhy) {
                        case BluetoothDevice.PHY_LE_1M:
                            Log.d("bgx_dbg", "rxPhy: 1M PHY");
                            break;
                        case BluetoothDevice.PHY_LE_2M:
                            Log.d("bgx_dbg", "rxPhy: 2M PHY");
                            break;
                        case BluetoothDevice.PHY_LE_CODED:
                            Log.d("bgx_dbg", "rxPhy: CODED PHY");
                            break;
                    }

                } else {
                    Log.e("bgx_dbg", "onPhyUpdate: ERROR");
                }

                clearGattBusyFlagAndExecuteNext();
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                if (0 == status) {
                    mMTUInitialReadComplete = true;
                    deviceWriteChunkSize = mtu - 3;
                }

                Intent intent = new Intent();
                intent.setAction(BGX_MTU_CHANGE);
                intent.putExtra("mtu", mtu);
                intent.putExtra("status", status);
                intent.putExtra("deviceAddress", gatt.getDevice().getAddress());
                sendBroadcast(intent);

                clearGattBusyFlagAndExecuteNext();
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                String snewstate = "?";
                String sprevstate = "?";

                switch (newState) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        snewstate = "DISCONNECTED";
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        snewstate = "CONNECTING";
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        snewstate = "CONNECTED";
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        snewstate = "DISCONNECTING";
                        break;
                }

                switch (mDeviceConnectionState) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        sprevstate = "DISCONNECTED";
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        sprevstate = "CONNECTING";
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        sprevstate = "CONNECTED";
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        sprevstate = "DISCONNECTING";
                        break;
                }

                if (BluetoothGatt.GATT_SUCCESS != status && BluetoothProfile.STATE_DISCONNECTED == newState) {
                    Intent errorIntent = new Intent();
                    errorIntent.setAction(BGX_CONNECTION_ERROR);
                    errorIntent.putExtra("status", status);
                    sendBroadcast(errorIntent);
                }

                Log.d("bgx_dbg", "onConnectionStateChanged for " + gatt.getDevice().getName() + " newState: " + snewstate + "(" + newState + ")" + " prevState: " + sprevstate + "(" + mDeviceConnectionState + ")" + " status: " + status);

                DeviceProperties dp = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (mDeviceConnectionState != newState) {
                    mDeviceConnectionState = newState;

                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                    broadcastIntent.putExtra("device", gatt.getDevice());
                    broadcastIntent.putExtra("DeviceAddress", gatt.getDevice().getAddress());

                    if (null != mBroadcastReceiver && (BluetoothProfile.STATE_DISCONNECTING == mDeviceConnectionState || BluetoothProfile.STATE_DISCONNECTED == mDeviceConnectionState)) {
                        unregisterReceiver(mBroadcastReceiver);
                        mBroadcastReceiver = null;
                    }

                    switch (mDeviceConnectionState) {
                        case BluetoothProfile.STATE_CONNECTING:
                            mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.CONNECTING;
                            broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.CONNECTING);
                            Log.d("bgx_dbg", "connection state: CONNECTING.");
                            if (!dp.fUserConnectionCanceled) {
                                sendBroadcast(broadcastIntent);
                            }
                            break;
                        case BluetoothProfile.STATE_CONNECTED: {
                            if (!dp.fUserConnectionCanceled) {

                                if (mBluetoothGatt == null) {
                                    Log.e("bgx_dbg", "onConnectionStateChange(): BluetoothGatt is null");
                                    return;
                                }

                                if (null != mBroadcastReceiver) {
                                    Log.e("bgx_dbg", "Error: mBroadcastReceiver should not be null when entering the connected state.");
                                }

                                mBroadcastReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        switch (intent.getAction()) {
                                            case ACTION_BOND_STATE_CHANGED: {
                                                String sbondstate = "?";
                                                int bondState = mBluetoothGatt.getDevice().getBondState();
                                                switch (bondState) {
                                                    case BOND_NONE:
                                                        sbondstate = "BOND_NONE";
                                                        break;
                                                    case BOND_BONDING:
                                                        sbondstate = "BOND_BONDING";
                                                        break;
                                                    case BOND_BONDED:
                                                        sbondstate = "BOND_BONDED";
                                                        break;
                                                }
                                                Log.d("bgx_dbg", "Bond state changed to " + sbondstate + ".");

                                                if (BOND_BONDED == bondState) {
                                                    Intent broadcastIntent = new Intent();
                                                    broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                                                    broadcastIntent.putExtra("device", mBluetoothGatt.getDevice());
                                                    broadcastIntent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                                                    broadcastIntent.putExtra("bgx-connection-status", INTERROGATING);
                                                    broadcastIntent.putExtra("bonded", true);

                                                    int discoverServicesDelay = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 ? 1500 : 0;
                                                    mHandler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (mBluetoothGatt != null) {
                                                                mBluetoothGatt.discoverServices();
                                                            }
                                                        }
                                                    }, discoverServicesDelay);
                                                } else if (BOND_NONE == bondState) {
                                                    mHandler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (mBluetoothGatt != null) {
                                                                mBluetoothGatt.getDevice().createBond();
                                                            }
                                                        }
                                                    }, 3000);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                };

                                IntentFilter filter = new IntentFilter(ACTION_BOND_STATE_CHANGED);
                                registerReceiver(mBroadcastReceiver, filter);

                                mBGXDeviceConnectionState = INTERROGATING;
                                broadcastIntent.putExtra("bgx-connection-status", INTERROGATING);
                                broadcastIntent.putExtra("bonded", false);
                                sendBroadcast(broadcastIntent);

                                int bondState = mBluetoothGatt.getDevice().getBondState();
                                if (bondState == BOND_BONDED) {
                                    Log.d("bgx_dbg", "Bluetooth device is already bonded");

                                    broadcastIntent = new Intent();
                                    broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                                    broadcastIntent.putExtra("device", mBluetoothGatt.getDevice());
                                    broadcastIntent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                                    broadcastIntent.putExtra("bgx-connection-status", INTERROGATING);
                                    broadcastIntent.putExtra("bonded", true);

                                    int discoverServicesDelay = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 ? 1500 : 0;
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mBluetoothGatt != null) {
                                                mBluetoothGatt.discoverServices();
                                            }
                                        }
                                    }, discoverServicesDelay);

                                    return;
                                } else if (bondState == BOND_BONDING) {
                                    Log.d("bgx_dbg", "Bonding in progress...");
                                    return;
                                }

                                boolean fBond = mBluetoothGatt.getDevice().createBond();
                                if (!fBond) {
                                    Log.d("bgx_dbg", "BluetoothDevice.createBond() returned false, checking bondState.");

                                    bondState = mBluetoothGatt.getDevice().getBondState();
                                    if (BOND_BONDED == bondState) {
                                        Log.d("bgx_dbg", "BondState: BONDED.");
                                        mBGXDeviceConnectionState = INTERROGATING;
                                        broadcastIntent.putExtra("bgx-connection-status", INTERROGATING);
                                        broadcastIntent.putExtra("bonded", true);
                                        sendBroadcast(broadcastIntent);

                                        if (mBluetoothGatt != null) {
                                            mBluetoothGatt.discoverServices();
                                        }
                                    } else {
                                        String sbondState = "?";
                                        if (BOND_NONE == bondState) {
                                            sbondState = "BOND_NONE";
                                        } else if (BOND_BONDING == bondState) {
                                            sbondState = "BOND_BONDING";
                                        }

                                        Log.d("bgx_dbg", "BondState: " + sbondState + ".");
                                        queueGattIntent(new Intent(ACTION_POLL_BOND_STATUS));
                                    }
                                } else {
                                    Log.d("bgx_dbg", "BluetoothDevice.createBond() returned true.");
                                }
                            } else {
                                mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTED;
                            }
                        }
                        break;
                        case BluetoothProfile.STATE_DISCONNECTING:
                            mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTING;
                            broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.DISCONNECTING);
                            Log.d("bgx_dbg", "connection state: DISCONNECTING.");
                            sendBroadcast(broadcastIntent);
                            break;
                        case BluetoothProfile.STATE_DISCONNECTED:
                            mMTUInitialReadComplete = false;
                            mPlatformString = null;
                            mFirmwareRevisionString = null;

                            clearGattQueue();

                            if (null != mBluetoothGatt) {
                                mBluetoothGatt.close();
                                mBluetoothGatt = null;
                            }

                            mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTED;
                            broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.DISCONNECTED);
                            broadcastIntent.putExtra("status", status);

                            Log.d("bgx_dbg", "connection state: DISCONNECTED.");
                            sendBroadcast(broadcastIntent);

                            clearGattQueue();
                            break;
                        default:
                            Log.d("bgx_dbg", "connection state: OTHER.");
                            break;
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                boolean fServicesOK = false;
                DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                Intent setupIntent;

                super.onServicesDiscovered(gatt, status);
                Log.d("bgx_dbg", "onServicesDiscovered.");
                // look for BGX Streaming Service (BGXSS).

                dps.mDeviceInfoService = gatt.getService(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"));
                if (null != dps.mDeviceInfoService) {
                    Log.d("bgx_dbg", "DeviceInfo Service found.");

                    dps.mFirmwareRevisionCharacteristic = mDeviceInfoService.getCharacteristic(UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"));
                    if (null != dps.mFirmwareRevisionCharacteristic) {
                        Log.d("bgx_dbg", "FirmwareRevisionCharacteristic");
                    }
                } else {
                    Log.e("bgx_dbg", "DeviceInfo Service Not Found");
                }

                dps.mBGXSS = gatt.getService(UUID.fromString("331a36f5-2459-45ea-9d95-6142f0c4b307"));
                if (null != dps.mBGXSS) {
                    Log.d("bgx_dbg", "BGXSS found.");

                    dps.mRxCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("a9da6040-0823-4995-94ec-9ce41ca28833"));
                    if (null != mRxCharacteristic) {
                        Log.d("bgx_dbg", "RxCharacteristic discovered.");

                        /*
                         * For SDK < 26, the connectGatt method that takes a handler
                         * is not available. To prevent dataloss due to characteristic
                         * value collisions, we create a copy of the characteristic for
                         * handling fastAck back channel data.
                         */
                        if (Build.VERSION.SDK_INT < 26) {
                            Parcel parcel = null;

                            try {
                                parcel = Parcel.obtain();
                                parcel.writeParcelable(mRxCharacteristic, 0);
                                parcel.setDataPosition(0);
                                mRxCharacteristic2 = parcel.readParcelable(BluetoothGattCharacteristic.class.getClassLoader());

                                try {
                                    Method setServiceMethod = BluetoothGattCharacteristic.class.getDeclaredMethod("setService", BluetoothGattService.class);
                                    setServiceMethod.setAccessible(true);
                                    setServiceMethod.invoke(mRxCharacteristic2, mRxCharacteristic.getService());
                                } catch (NoSuchMethodException nosuchmethodException) {
                                    Log.e("bgx_dbg", "NoSuchMethodException caught.");
                                    mRxCharacteristic2 = null;

                                } catch (IllegalAccessException illegalAccessException) {
                                    Log.e("bgx_dbg", "IllegalAccessException caught.");
                                    mRxCharacteristic2 = null;

                                } catch (InvocationTargetException invocationTargetException) {
                                    Log.e("bgx_dbg", "InvocationTargetException caught.");
                                    mRxCharacteristic2 = null;
                                }

                            } finally {
                                if (parcel != null) {
                                    parcel.recycle();
                                }
                            }
                        }
                    }

                    dps.mTxCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("a73e9a10-628f-4494-a099-12efaf72258f"));
                    if (null != mTxCharacteristic) {
                        Log.d("bgx_dbg", "TxCharacteristic discovered.");

                        /*
                         * For SDK < 26, the connectGatt method that takes a handler
                         * is not available. To prevent dataloss due to characteristic
                         * value collisions, we create a copy of the characteristic for
                         * handling fastAck back channel data.
                         */
                        if (Build.VERSION.SDK_INT < 26) {
                            Parcel parcel = null;

                            try {
                                parcel = Parcel.obtain();
                                parcel.writeParcelable(mTxCharacteristic, 0);
                                parcel.setDataPosition(0);
                                mTxCharacteristic2 = parcel.readParcelable(BluetoothGattCharacteristic.class.getClassLoader());

                                try {
                                    Method setServiceMethod = BluetoothGattCharacteristic.class.getDeclaredMethod("setService", BluetoothGattService.class);
                                    setServiceMethod.setAccessible(true);
                                    setServiceMethod.invoke(mTxCharacteristic2, mTxCharacteristic.getService());
                                } catch (NoSuchMethodException nosuchmethodException) {
                                    Log.e("bgx_dbg", "NoSuchMethodException caught.");
                                    mTxCharacteristic2 = null;
                                } catch (IllegalAccessException illegalAccessException) {
                                    Log.e("bgx_dbg", "IllegalAccessException caught.");
                                    mTxCharacteristic2 = null;
                                } catch (InvocationTargetException invocationTargetException) {
                                    Log.e("bgx_dbg", "InvocationTargetException caught.");
                                    mTxCharacteristic2 = null;
                                }

                            } finally {
                                if (parcel != null) {
                                    parcel.recycle();
                                }
                            }
                        }
                    }

                    dps.mBGXSSModeCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("75a9f022-af03-4e41-b4bc-9de90a47d50b"));
                    if (null != mBGXSSModeCharacteristic) {
                        Log.d("bgx_dbg", "BGXSS Mode Characteristic discovered.");
                        dps.mBGXSSModeCharacteristic.setWriteType(WRITE_TYPE_DEFAULT);
                    }
                } else {
                    Log.e("bgx_dbg", "BGXStreamService Not Found");
                }

                dps.mOTAService = gatt.getService(UUID.fromString("169b52a0-b7fd-40da-998c-dd9238327e55"));
                if (null != dps.mOTAService) {
                    Log.d("bgx_dbg", "Found the OTA Service.");

                    dps.mOTAControlCharacteristic = mOTAService.getCharacteristic(UUID.fromString("902ee692-6ef9-48a8-a430-5212eeb3e5a2"));
                    if (null != dps.mOTAControlCharacteristic) {
                        Log.d("bgx_dbg", "Found the OTA Control Characteristic");
                    }

                    dps.mOTADataCharacteristic = mOTAService.getCharacteristic(UUID.fromString("503a5d70-b443-466e-9aeb-c342802b184e"));
                    if (null != dps.mOTADataCharacteristic) {
                        Log.d("bgx_dbg", "Found the OTADataCharacteristic");
                    }

                    dps.mOTADeviceIDCharacterisitc = mOTAService.getCharacteristic(UUID.fromString("12e868e7-c926-4906-96c8-a7ee81d4b1b3"));
                    if (null != dps.mOTADeviceIDCharacterisitc) {
                        Log.d("bgx_dbg", "Found OTADeviceIDCharaceteristic");
                    }

                } else {
                    Log.e("bgx_dbg", "OTA Service Not Found");
                }

                if (null != dps.mBGXSS && null != dps.mOTAService) {
                    fServicesOK = true;
                }

                if (null != dps.mBGXSS) {
                    Intent setupFastAck = new Intent();
                    setupFastAck.setAction(ACTION_SETUP_FAST_ACK);
                    queueGattIntent(setupFastAck);
                }

                if (null != dps.mFirmwareRevisionCharacteristic) {
                    setupIntent = new Intent();
                    setupIntent.setAction(ACTION_READ_FIRMWARE_REVISION);
                    queueGattIntent(setupIntent);
                }

                if (null != dps.mBGXSS) {
                    setupIntent = new Intent();
                    setupIntent.setAction(ACTION_ENABLE_MODE_CHANGE_NOTIFICATION);
                    queueGattIntent(setupIntent);

                    setupIntent = new Intent();
                    setupIntent.setAction(ACTION_ENABLE_TX_CHANGE_NOTIFICATION);
                    queueGattIntent(setupIntent);
                }

                if (Build.VERSION.SDK_INT >= 26) {
                    if (BluetoothAdapter.getDefaultAdapter().isLe2MPhySupported()) {
                        Log.d("bgx_dbg", "Queuing the 2m phy action");
                        queueGattIntent(new Intent(ACTION_SET_2M_PHY));
                    }
                }

                if (null != dps.mBGXSS) {
                    mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.CONNECTED;
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                    broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.CONNECTED);
                    broadcastIntent.putExtra("device", mBluetoothGatt.getDevice());
                    broadcastIntent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                    broadcastIntent.putExtra("bonded", true);
                    queueGattIntent(broadcastIntent); // queue this so it will be sent after the setup finishes instead of right now.
                }

                if (!fServicesOK) {
                    mBluetoothGatt.disconnect();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d("bgx_dbg", "OnCharacteristicChanged called " + characteristic.getUuid().toString());

                if (characteristic == mBGXSSModeCharacteristic) {
                    int BusMode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Intent intent = new Intent();
                    intent.setAction(BGX_MODE_STATE_CHANGE);
                    intent.putExtra("busmode", BusMode);
                    intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                    sendBroadcast(intent);
                    Log.d("bgx_dbg", "BusMode: " + BusMode);
                } else if (mTxCharacteristic == characteristic || mTxCharacteristic2 == characteristic) {
                    final String myValue = mTxCharacteristic.getStringValue(0);

                    int bytesReceived = myValue.length();

                    if (mFastAck) {
                        mFastAckRxBytes -= bytesReceived;
                    }

                    Intent intent = new Intent(BGX_DATA_RECEIVED);
                    intent.putExtra("data", myValue);
                    intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                    sendBroadcast(intent);

                    if (mFastAck) {
                        updateFastAckRxBytes(1, bytesReceived);
                    }

                } else if (mRxCharacteristic == characteristic || mRxCharacteristic2 == characteristic) {
                    Log.d("bgx_fastAck", "FastAck backchannel data received.");
                    if (!mFastAck) {
                        Log.d("bgx_fastAck", "Warning: FastAck backchannel received but fastAck is not enabled.");
                    }

                    byte[] val = characteristic.getValue();

                    Log.d("bgx_fastAck", "Received " + val.length + " bytes for RxCharacteristic.");

                    if (3 == val.length) {
                        byte opcode = val[0];

                        byte b2 = val[1];
                        byte b3 = val[2];

                        int i2 = b2 & 0xFF;
                        int i3 = b3 & 0xFF;

                        int txbytes = (i3 << 8) | i2;

                        switch (opcode) {
                            case 0x00:
                                mFastAckTxBytes = txbytes;
                                Log.d("bgx_fastAck", "fastAckTxBytes: " + mFastAckTxBytes + " (assigned value)");
                                break;
                            case 0x01:
                                boolean fQueueAWrite = false;

                                if (0 >= mFastAckTxBytes) {
                                    fQueueAWrite = true;
                                }

                                mFastAckTxBytes += txbytes;
                                Log.d("bgx_fastAck", "fastAckTxBytes: " + mFastAckTxBytes + " (added " + txbytes + " bytes)");

                                if (fQueueAWrite) {
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            writeChunkOfData();
                                        }
                                    }, 10);
                                    break;
                                }
                        }
                    }
                }
                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (BluetoothGatt.GATT_SUCCESS == status) {
                    if (dps.mBGXSSModeCharacteristic == characteristic) {
                        int busMode = dps.mBGXSSModeCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                        Intent sintent = new Intent();
                        sintent.setAction(BGX_MODE_STATE_CHANGE);
                        sintent.putExtra("busmode", busMode);
                        sintent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                        sendBroadcast(sintent);
                    } else if (dps.mOTADeviceIDCharacterisitc == characteristic) {
                        byte[] deviceIDValue = dps.mOTADeviceIDCharacterisitc.getValue();
                        char[] hexChars = new char[deviceIDValue.length * 2];

                        for (int i = 0; i < deviceIDValue.length; ++i) {
                            int v = deviceIDValue[i] & 0xFF;
                            hexChars[i * 2] = kHexArray[v >>> 4];
                            hexChars[1 + (i * 2)] = kHexArray[v & 0x0F];
                        }

                        String bgxDeviceUUID = new String(hexChars);

                        dps.partIdentifier = bgxDeviceUUID.substring(0, 8);
                        dps.deviceIdentifier = bgxDeviceUUID;

                        Intent intent = new Intent(BGX_DEVICE_INFO);
                        intent.putExtra("bgx-device-uuid", deviceIdentifier);
                        intent.putExtra("bgx-part-identifier", partIdentifier);
                        intent.putExtra("bgx-platform-identifier", mPlatformString);

                        BGXPartID devicePartID;
                        if (bgxDeviceUUID.startsWith(BGX13S_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX13S;
                        } else if (bgxDeviceUUID.startsWith(BGX13P_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX13P;
                        } else if (bgxDeviceUUID.startsWith(BGX_Invalid_Device_Prefix)) {
                            devicePartID = BGXPartID.BGXInvalid;
                        } else if (bgxDeviceUUID.startsWith(BGXV3S_Device_Prefix)) {
                            devicePartID = BGXPartID.BGXV3S;
                        } else if (bgxDeviceUUID.startsWith(BGXV3P_Device_Prefix)) {
                            devicePartID = BGXPartID.BGXV3P;
                        } else if (bgxDeviceUUID.startsWith(BGX220P_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX220P;
                        } else if (bgxDeviceUUID.startsWith(BGX220S_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX220S;
                        } else {
                            Log.e("bgx_dbg", "Unknown BGX PartID");
                            devicePartID = BGXPartID.BGXUnknownPartID;
                        }

                        intent.putExtra("bgx-part-id", devicePartID);
                        intent.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());

                        Log.d("bgx_dbg", "**** Read the BGX Device UUID: " + bgxDeviceUUID + " ****");

                        sendBroadcast(intent);
                    } else if (dps.mFirmwareRevisionCharacteristic == characteristic) {
                        String firmwareRevision = characteristic.getStringValue(0);

                        String[] pieces = firmwareRevision.split("-");
                        if (3 == pieces.length) {
                            mBootloaderVersion = Integer.parseInt(pieces[1]);
                        } else {
                            mBootloaderVersion = -2;
                        }

                        String firmwareVers = pieces[0];
                        String platformid = firmwareVers.substring(0, firmwareVers.indexOf('.')).toLowerCase();

                        if (platformid.endsWith("p") || platformid.endsWith("s")) {
                            mPlatformString = platformid.substring(0, platformid.length() - 1);
                        } else {
                            mPlatformString = platformid;
                        }

                        if (firmwareVers.startsWith("BGX")) {
                            mFirmwareRevisionString = firmwareVers.substring(firmwareVers.indexOf('.') + 1);

                            if (!mMTUInitialReadComplete) {
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent mtuIntent = new Intent(ACTION_REQUEST_MTU);
                                        mtuIntent.putExtra("mtu", 247);
                                        queueGattIntent(mtuIntent);
                                    }
                                }, 1000);
                            }
                        } else {
                            // this is probably invalid gatt handles

                            Intent bi = new Intent();
                            bi.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());
                            bi.putExtra("DeviceName", dps.mBluetoothGatt.getDevice().getName());

                            bi.setAction(BGX_INVALID_GATT_HANDLES);
                            sendBroadcast(bi);
                        }
                    }
                }
                dps.clearGattBusyFlagAndExecuteNext();
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                final int final_status = status;
                final DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (dps.mOTAControlCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite OTAControlCharacteristic");
                    if (OTA_State.WrittenZeroToControlCharacteristic == dps.mOTAState) {

                        if (BluetoothGatt.GATT_SUCCESS == status) {
                            dps.mOTAState = OTA_State.WritingOTAImage;
                            dps.writeOTAImageChunk();
                        } else {
                            // A password is required (probably).
                            Log.e("bgx_dbg", "OTA Failed. Bad status on write to OTAControlCharacteristic.");
                            Intent intent = new Intent();
                            intent.setAction(OTA_STATUS_MESSAGE);
                            intent.putExtra("ota_status", OTA_Status.Password_Required);
                            intent.putExtra("ota_failed", false);
                            intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                            sendBroadcast(intent);
                            dps.fOTAInProgress = false;
                            dps.clearGattBusyFlagAndExecuteNext();
                        }

                    } else if (OTA_State.WriteThreeToControlCharacteristic == dps.mOTAState) {

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent();
                                intent.setAction(OTA_STATUS_MESSAGE);

                                if (BluetoothGatt.GATT_SUCCESS == final_status) {
                                    intent.putExtra("ota_status", OTA_Status.Finished);
                                } else {
                                    intent.putExtra("ota_status", OTA_Status.Failed);
                                    intent.putExtra("ota_failed", true);
                                }

                                intent.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());
                                sendBroadcast(intent);

                                dps.fOTAInProgress = false;
                                dps.mOTAState = OTA_State.OTA_Idle;
                                dps.clearGattBusyFlagAndExecuteNext();
                            }
                        }, 15000);
                    }
                } else if (dps.mOTADataCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite OTADataCharacteristic");

                    if (BluetoothGatt.GATT_SUCCESS == status) {
                        if (OTA_State.WritingOTAImage == dps.mOTAState) {
                            writeOTAImageChunk();
                        } else if (OTA_State.WriteThreeToControlCharacteristic == dps.mOTAState) {
                            finishOTA();
                        }
                    } else {
                        Log.e("bgx_dbg", "OTA Failed. onWrite failed for OTA_Data_Characteristic.");
                        Intent intent = new Intent();
                        intent.setAction(OTA_STATUS_MESSAGE);
                        intent.putExtra("ota_status", OTA_Status.Idle);
                        intent.putExtra("ota_failed", true);
                        intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                        sendBroadcast(intent);
                    }
                } else if (dps.mRxCharacteristic == characteristic || dps.mRxCharacteristic2 == characteristic) {
                    if (null != dps.mData2Write) {
                        writeChunkOfData();
                    } else {
                        dps.clearGattBusyFlagAndExecuteNext();
                    }
                } else if (dps.mBGXSSModeCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite - mode characteristic. Status: " + status);
                    dps.clearGattBusyFlagAndExecuteNext();

                    if (0 != status) {
                        // Treat this as a password error.
                        Intent intent = new Intent();
                        intent.setAction(BUS_MODE_ERROR_PASSWORD_REQUIRED);
                        intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                        sendBroadcast(intent);
                    }

                } else if (dps.mTxCharacteristic == characteristic || dps.mTxCharacteristic2 == characteristic) {
                    Log.d("bgx_fastAck", "onCharacteristicWrite - txCharacteristic. Status: " + status);
                    String txval = dps.mTxCharacteristic.getStringValue(0);
                    Log.d("bgx_dbg", "txval: " + txval);

                    dps.clearGattBusyFlagAndExecuteNext();
                } else {
                    Log.d("bgx_dbg", "onCharacteristicWrite - other");
                    dps.clearGattBusyFlagAndExecuteNext();
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt,
                                          BluetoothGattDescriptor descriptor,
                                          int status) {

                DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (BluetoothGatt.GATT_SUCCESS != status) {
                    Log.e("bgx_dbg", "onDescriptorWrite failed. DescID: " + descriptor.getUuid().toString() + " Last Executed Action: " + dps.mLastExecutedIntent.getAction());

                    if (INTERROGATING == mBGXDeviceConnectionState) {
                        Intent intent = new Intent(BGX_CONNECTION_ERROR);
                        intent.putExtra("status", status);
                        sendBroadcast(intent);
                    }
                }
                dps.clearGattBusyFlagAndExecuteNext();
            }
        };

        private static final int kDataWriteChunkDefaultSize = 20;

        /**
         * This is called to write a bit of data to the Rx characteristic.
         * and will be called again from OnCharacteristicWrite until all
         * of the string has been written. The last call to this function
         * clears mString2Write which indicates to OnCharacteristicWrite
         * that no further calls to this function are needed in which case
         * it calls clearGattBusyFlagAndExecuteNext().
         */
        private void writeChunkOfData() {
            int ibegin, iend;

            if (mBluetoothGatt == null) {
                Log.e("bgx_dbg", "writeChunkOfData(): BluetoothGatt is null");
                return;
            }

            if (mFastAck && mFastAckTxBytes <= 0) {
                Log.d("bgx_fastAck", "FastAck Stall: mFastAckTxBytes = " + mFastAckTxBytes);
                return;
            }

            BluetoothGattCharacteristic rxChar;
            if (this.mRxCharacteristic2 != null) {
                rxChar = this.mRxCharacteristic2;
            } else {
                rxChar = this.mRxCharacteristic;
            }

            if (null == rxChar) {
                Log.e("bgx_dbg", "Error, no RxCharacteristic available.");
            }

            synchronized (this.dataWriteSync) {
                if (null != this.mData2Write) {

                    ibegin = this.mWriteOffset;
                    iend = this.mData2Write.length;

                    if (iend - ibegin > this.deviceWriteChunkSize) {
                        iend = ibegin + this.deviceWriteChunkSize;
                    }

                    if (mFastAck) {
                        for (int amt2write = (iend - ibegin); amt2write > mFastAckTxBytes; amt2write = (iend - ibegin)) {
                            iend = ibegin + mFastAckTxBytes;
                        }
                    }

                    rxChar.setValue(Arrays.copyOfRange(this.mData2Write, ibegin, iend));

                    boolean writeResult = mBluetoothGatt.writeCharacteristic(rxChar);
                    if (writeResult) {
                        if (mFastAck) {
                            mFastAckTxBytes -= (iend - ibegin);
                            Log.d("bgx_fastAck", "mFastAckTxBytes: " + mFastAckTxBytes + " (subtracted " + (iend - ibegin) + " bytes)");
                        }

                        Log.d("bgx_dbg", "Rx Char Write success.");

                        if (this.mData2Write.length > iend) {
                            this.mWriteOffset = iend;
                        } else {
                            this.mWriteOffset = 0;
                            this.mData2Write = null;
                        }
                    }
                }
            }
        }


        private InputStream mOTAImageInputStream = null;

        private final int kChunkSize = 244;

        private int ota_bytes_sent;
        private int ota_image_size;

        /**
         * Writes OTA image chunk of data to device.
         */
        private void writeOTAImageChunk() {
            if (mBluetoothGatt == null) {
                Log.e("bgx_dbg", "writeOTAImageChunk(): BluetoothGatt is null");
                return;
            }

            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            /*
             *  Chunk Size should be evenly divisible by four because this is required by some hardware.
             *  An odd chunk size will cause the update to fail.
             */
            try {
                byte[] buffer = new byte[kChunkSize];
                int bytesRead = mOTAImageInputStream.read(buffer);

                Log.d("bgx_dbg", "mOTAImageFileInputStream.read(buffer) called returned " + bytesRead);

                if (-1 == bytesRead) {
                    // this indicates an error or EOF which in this case
                    // can be treated as an error.
                    Log.e("bgx_dbg", "Error: An error occurred while trying to read the OTA image " + ota_bytes_sent + "bytes sent.");
                    Intent intent = new Intent();
                    intent.setAction(OTA_STATUS_MESSAGE);
                    intent.putExtra("ota_status", OTA_Status.Idle);
                    intent.putExtra("ota_failed", true);
                    intent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                    sendBroadcast(intent);
                    return;
                }

                ota_bytes_sent += bytesRead;

                Intent intent = new Intent();
                intent.setAction(OTA_STATUS_MESSAGE);
                intent.putExtra("ota_status", OTA_Status.Installing);
                intent.putExtra("bytes_sent", ota_bytes_sent);
                intent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                sendBroadcast(intent);

                if (bytesRead < kChunkSize || ota_image_size == ota_bytes_sent) {
                    Log.d("bgx_dbg", "Writing the final chunk (" + bytesRead + " bytes) of the OTA image. " + ota_bytes_sent + " bytes written");

                    this.mOTAState = OTA_State.WriteThreeToControlCharacteristic;
                    Log.d("bgx_dbg", "Change OTA_State to WriteThreeToControlCharacteristic.");
                    byte[] tempBuffer = new byte[bytesRead];
                    System.arraycopy(buffer, 0, tempBuffer, 0, bytesRead);
                    this.mOTADataCharacteristic.setValue(tempBuffer);
                } else {
                    Log.d("bgx_dbg", "Writing 244 bytes of the OTA image. " + ota_bytes_sent + " bytes written");
                    this.mOTADataCharacteristic.setValue(buffer);
                }

                if (!mBluetoothGatt.writeCharacteristic(this.mOTADataCharacteristic)) {
                    Log.e("bgx_dbg", "Failed to write to OTADataCharacteristic.");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Writes the final three to the OTA control characteristic.
         */
        private void finishOTA() {
            if (mBluetoothGatt == null) {
                Log.e("bgx_dbg", "finishOTA(): BluetoothGatt is null");
                return;
            }

            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            Intent intent = new Intent();
            intent.setAction(OTA_STATUS_MESSAGE);
            intent.putExtra("ota_status", OTA_Status.Finishing);
            intent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
            sendBroadcast(intent);

            Log.d("bgx_dbg", "finishOTA called.");
            byte[] finalThree = new byte[1];
            finalThree[0] = 3;
            this.mOTAControlCharacteristic.setValue(finalThree);

            if (!mBluetoothGatt.writeCharacteristic(this.mOTAControlCharacteristic)) {
                Log.e("bgx_dbg", "Failed to write to OTAControlCharacteristic.");
            }
        }

        /**
         * Reports that OTA has been canceled by user.
         */
        void ReportOTACanceled() {
            Intent intent = new Intent();
            intent.setAction(OTA_STATUS_MESSAGE);
            intent.putExtra("ota_status", OTA_Status.UserCanceled);
            intent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
            sendBroadcast(intent);

            this.fOTAInProgress = false;
            this.mOTAState = OTA_State.OTA_Idle;
            this.clearGattBusyFlagAndExecuteNext();
        }

        /**
         * Perform the OTA.
         *
         * <p>It is recommended to use {@link #ACTION_OTA_FIRMWARE_IMAGE} instead of {@link #ACTION_OTA_WITH_IMAGE} due to deprecation.
         *
         * @param ota_image_path The path to the OTA image file used during OTA process.
         */
        @Deprecated
        private void handleActionOTAWithImage(String ota_image_path, String password) {
            // First verify the assumptions:
            // 1. A connection to a device exists.
            // 2. The path passed as an argument is to a file that exists.

            File f = new File(ota_image_path);
            ota_image_size = toIntExact(f.length());

            Log.d("bgx_dbg", "OTA Image Size: " + ota_image_size);

            ota_bytes_sent = 0;

            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            // Set up writes for OTA image.
            try {
                mOTAImageInputStream = new FileInputStream(ota_image_path);
            } catch (FileNotFoundException exception) {
                exception.printStackTrace();
                Log.e("bgx_dbg", "Error OTA Image file not found: " + ota_image_path);
                return;
            }

            // Write a zero to the OTA Control characteristic.
            this.mOTAState = OTA_State.WrittenZeroToControlCharacteristic;

            if (null == password) {
                password = "";
            }

            byte[] zeroValue = new byte[0 == password.length() ? 1 : 2 + password.length()];
            zeroValue[0] = (byte) 0;
            if (password.length() > 0) {
                for (int i = 0; i < password.length(); ++i) {
                    zeroValue[i + 1] = password.getBytes()[i];
                }
                zeroValue[password.length() + 1] = 0;
            }

            this.mOTAControlCharacteristic.setValue(zeroValue);

            if (mBluetoothGatt == null) {
                Log.e("bgx_dbg", "handleActionOTAWithImage(): BluetoothGatt is null");
                return;
            }

            if (!mBluetoothGatt.writeCharacteristic(this.mOTAControlCharacteristic)) {
                Log.e("bgx_dbg", "Failed to write to OTAControlCharacteristic");
            }
        }

        /**
         * Perform the OTA.
         *
         * @param ota_image_path The path to the OTA image file used during OTA process.
         */
        private void handleActionOtaFirmwareImage(String ota_image_path, String password) {

            try {
                mOTAImageInputStream = getAssets().open("firmware_files/" + ota_image_path);
                ota_image_size = mOTAImageInputStream.available();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("bgx_dbg", "OTA file error with: " + ota_image_path);
                return;
            }

            ota_bytes_sent = 0;

            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            // Write a zero to the OTA Control characteristic.
            this.mOTAState = OTA_State.WrittenZeroToControlCharacteristic;

            if (null == password) {
                password = "";
            }

            byte[] zeroValue = new byte[0 == password.length() ? 1 : 2 + password.length()];
            zeroValue[0] = (byte) 0;
            if (password.length() > 0) {
                for (int i = 0; i < password.length(); ++i) {
                    zeroValue[i + 1] = password.getBytes()[i];
                }
                zeroValue[password.length() + 1] = 0;
            }

            this.mOTAControlCharacteristic.setValue(zeroValue);

            if (mBluetoothGatt == null) {
                Log.e("bgx_dbg", "handleActionOtaFirmwareImage(): BluetoothGatt is null");
                return;
            }

            if (!mBluetoothGatt.writeCharacteristic(this.mOTAControlCharacteristic)) {
                Log.e("bgx_dbg", "Failed to write to OTAControlCharacteristic");
            }
        }
    }

    static private Map<String, DeviceProperties> mDeviceProperties = null;

    static private HandlerThread mHandlerThread = null;
    static public Handler mHandler = null;


    public void onCreate() {
        super.onCreate();

        if (null == mHandlerThread) {
            mHandlerThread = new HandlerThread("BGXpress");
            mHandlerThread.start();
        }

        if (null == mHandler) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        if (null == mScanProperties) {
            mScanProperties = new ScanProperties();
        }

        if (null == mDeviceProperties) {
            mDeviceProperties = new HashMap<>();
        }
    }

    /**
     * Starts this service to perform action Start Scan. If
     * the service is already performing a task this action will be queued.
     *
     * @param context Interface to global information about an Android application environment.
     */
    public static void startActionStartScan(Context context) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_START_SCAN);
        enqueueWork(context, intent);
    }

    /**
     * Starts this service to perform action Stop Scan. If
     * the service is already performing a task this action will be queued.
     *
     * @param context Interface to global information about an Android application environment.
     */
    public static void startActionStopScan(Context context) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_STOP_SCAN);
        enqueueWork(context, intent);
    }

    /**
     * Attempt to connect to the specified device. The process of the connection
     * can be tracked by receiving a series of BGX_CONNECTION_STATUS_CHANGE intents.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The Bluetooth address of the device to which to connect.
     */
    public static void startActionBGXConnect(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_CONNECT);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Attempt to cancel an in-progress connection operation.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The Bluetooth address of the device to which
     *                      to cancel the connection in progress.
     */
    public static void startActionBGXCancelConnect(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_CANCEL_CONNECTION);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Disconnect from a BGX Device.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The Bluetooth address of the device to which you wish to disconnect from.
     */
    public static void startActionBGXDisconnect(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_DISCONNECT);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Write message to BGX Device.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param message       A message you want to write to BGX device.
     * @param deviceAddress The device to which the operation pertains.
     */
    public static void startActionBGXWriteMessage(Context context, String message, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_WRITE_SERIAL_DATA);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.putExtra("value", message);
        enqueueWork(context, intent);
    }

    /**
     * Write array of bytes to BGX Device.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param byteArray     Array of bytes you want to write to BGX device.
     * @param deviceAddress The device to which the operation pertains.
     */
    public static void startActionBGXWriteByteArray(Context context, byte[] byteArray, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(BGXpressService.ACTION_WRITE_SERIAL_BIN_DATA);
        intent.putExtra("value", byteArray);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Read Bus Mode.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     */
    public static void startActionBGXReadBusMode(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_READ_BUS_MODE);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Write Bus Mode.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     * @param busMode       One of the values from BusMode: STREAM_MODE, LOCAL_COMMAND_MODE, or REMOTE_COMMAND_MODE.
     * @param password      If supplied, this value will be used as the password for REMOTE_COMMAND_MODE.
     */
    public static void startActionBGXWriteBusMode(Context context, String deviceAddress, int busMode, String password) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_WRITE_BUS_MODE);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.putExtra("busmode", busMode);
        intent.putExtra("password", password);
        enqueueWork(context, intent);
    }

    /**
     * Attempt to cancel an OTA operation in progress.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     */
    public static void startActionBGXCancelOta(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(BGXpressService.ACTION_OTA_CANCEL);
        intent.putExtra("DeviceAddress", deviceAddress);
        enqueueWork(context, intent);
    }

    /**
     * Start Ota process.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     * @param imagePath     Path to the image file.
     * @param writeType     (Optional) Either BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT or WRITE_TYPE_NO_RESPONSE.
     * @param password      The password to use for the OTA update if one is set.
     */
    @Deprecated
    public static void startActionBGXOtaWithImage(Context context, String deviceAddress, String imagePath, int writeType, String password) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(BGXpressService.ACTION_OTA_WITH_IMAGE);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.putExtra("image_path", imagePath);
        intent.putExtra("writeType", writeType);
        intent.putExtra("password", password);
        enqueueWork(context, intent);
    }

    /**
     * Start Ota process.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     * @param imagePath     Path to the image file.
     * @param writeType     (Optional) Either BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT or WRITE_TYPE_NO_RESPONSE.
     * @param password      The password to use for the OTA update if one is set.
     */
    public static void startActionBGXOtaFirmwareImage(Context context, String deviceAddress, String imagePath, int writeType, String password) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(BGXpressService.ACTION_OTA_FIRMWARE_IMAGE);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.putExtra("image_path", imagePath);
        intent.putExtra("writeType", writeType);
        intent.putExtra("password", password);
        enqueueWork(context, intent);
    }

    /**
     * Get available firmware versions.
     *
     * @param context        Interface to global information about an Android application environment.
     * @param partIdentifier An 8 character string identifying the type of BGX part.
     */
    public static void startActionBGXGetFirmwareVersions(Context context, String partIdentifier) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_GET_FIRMWARE_VERSIONS);
        intent.putExtra("bgx-part-identifier", partIdentifier);
        enqueueWork(context, intent);
    }

    /**
     * Starts an operation to get the device info which
     * is the part id and the device uuid.
     *
     * @param context       Interface to global information about an Android application environment.
     * @param deviceAddress The device to which the operation pertains.
     */
    public static void getBGXDeviceInfo(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.setAction(ACTION_BGX_GET_INFO);
        enqueueWork(context, intent);
    }

    public static boolean setBGXAcknowledgedWrites(String deviceAddress, boolean acknowledgedWrites) {
        boolean result = false;
        Intent intent = new Intent();
        intent.setAction(ACTION_SET_WRITE_TYPE);
        intent.putExtra("acknowledgedWrites", acknowledgedWrites);

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            dps.queueGattIntent(intent);
            result = true;
        }

        return result;
    }

    public static boolean setBGXAcknowledgedReads(String deviceAddress, boolean acknowledgedReads) {
        boolean result = false;
        Intent intent = new Intent();
        intent.setAction(ACTION_SET_READ_TYPE);
        intent.putExtra("acknowledgedReads", acknowledgedReads);

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            dps.queueGattIntent(intent);
            result = true;
        }

        return result;
    }

    public static BGX_CONNECTION_STATUS getBGXDeviceConnectionStatus(String deviceAddress) {
        BGX_CONNECTION_STATUS cs = BGX_CONNECTION_STATUS.DISCONNECTED;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            cs = dps.mBGXDeviceConnectionState;
        }

        return cs;
    }

    /**
     * Gets the BGXBootloader version if possible to do so.
     * This value is available only after the device is connected.
     *
     * @param deviceAddress Address of the device to retrieve the bootloader version
     * @return -1 on error, or the bootloader version (a positive integer).
     */
    public static Integer getBGXBootloaderVersion(String deviceAddress) {
        Integer result = -3;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (null != dps) {
            result = dps.mBootloaderVersion;
        } else {
            Log.d("bgx_dbg", "dps is null here.");
        }

        return result;
    }

    /**
     * @param deviceAddress Address of the device to retrieve the firmware revision
     * @return String containing the firmware version if available, otherwise NULL.
     */

    public static String getFirmwareRevision(String deviceAddress) {
        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (null != dps) {
            return dps.mFirmwareRevisionString;
        }

        return null;
    }

    public static String getPlatformIdentifier(String deviceAddress) {
        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (null != dps) {
            return dps.mPlatformString;
        }

        return null;
    }

    private static final int JOB_ID = 1000;

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, BGXpressService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if (intent != null) {
            String bgxDeviceAddress = intent.getStringExtra("DeviceAddress");
            DeviceProperties dps = mDeviceProperties.get(bgxDeviceAddress);

            final String action = intent.getAction();

            if (ACTION_START_SCAN.equals(action)) {
                handleActionStartScan();
            } else if (ACTION_STOP_SCAN.equals(action)) {
                handleActionStopScan();
            } else if (ACTION_BGX_GET_INFO.equals(action)) {
                dps.queueGattIntent(intent);
            } else if (ACTION_DMS_REQUEST_VERSION.equals(action)) {
                String dmsVersion = intent.getStringExtra("dms-version");
                String apiKey = getDmsAPIKey();
                String deviceAddress = intent.getStringExtra("DeviceAddress");
                Log.d("bgx_dbg", "Version Record: " + dmsVersion);

                handleActionGetDMSVersion(apiKey, deviceAddress, dmsVersion);
            } else if (ACTION_BGX_CONNECT.equals(action)) {
                handleActionBGXConnect(bgxDeviceAddress);
            } else if (ACTION_DMS_GET_VERSIONS.equals(action)) {
                String apiKey = getDmsAPIKey();
                BGXPartID partID = (BGXPartID) intent.getSerializableExtra("bgx-part-id");
                String partIdentifier = intent.getStringExtra("bgx-part-identifier");
                String platFormID = intent.getStringExtra("bgx-platform-identifier");

                handleActionGetDMSVersions(apiKey, partID, partIdentifier, platFormID);
            } else if (ACTION_GET_FIRMWARE_VERSIONS.equals(action)) {
                String partIdentifier = intent.getStringExtra("bgx-part-identifier");
                handleActionGetFirmwareVersions(partIdentifier);
            } else if (null != action) {
                if (null == dps) {
                    Log.d("bgx_dbg", "dps is null (will crash).");
                }

                if (ACTION_BGX_DISCONNECT.equals(action)) {
                    handleActionBGXDisconnect(bgxDeviceAddress);
                } else if (ACTION_WRITE_BUS_MODE.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_WRITE_SERIAL_DATA.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_WRITE_SERIAL_BIN_DATA.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_READ_BUS_MODE.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_BGX_CANCEL_CONNECTION.equals(action)) {
                    mHandler.removeCallbacksAndMessages(null);
                    dps.fUserConnectionCanceled = true;
                    if (null != dps.mBluetoothGatt) {
                        dps.mBluetoothGatt.disconnect();
                    }
                } else if (ACTION_OTA_WITH_IMAGE.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_OTA_FIRMWARE_IMAGE.equals(action)) {
                    dps.queueGattIntent(intent);
                } else if (ACTION_OTA_CANCEL.equals(action)) {
                    if (dps.fOTAInProgress) {
                        // cancel it.
                        dps.fOTAUserCanceled = true;
                    }
                } else if (ACTION_REQUEST_MTU.equals(action)) {
                    int mtuValue = intent.getIntExtra("mtu", 250);
                    if (mtuValue >= 23) {
                        dps.queueGattIntent(intent);
                    }
                }
            }
        }
    }

    /**
     * Handle action StartScan in the provided background thread.
     */
    private void handleActionStartScan() {
        try {
            if (null != mScanProperties && null != mScanProperties.mLEScanner) {
                mScanProperties.mScanResults = new ArrayList<>();
                mScanProperties.mLEScanner.startScan(mScanProperties.filters, mScanProperties.settings, mScanProperties.mScanCallback);
                Intent intent = new Intent();
                intent.setAction(BGX_SCAN_MODE_CHANGE);
                intent.putExtra("isscanning", true);
                sendBroadcast(intent);
                Log.d("bgx_dbg", "BGXpressService::StartScan");
            }
        } catch (IllegalStateException e) {
            Log.e("bgx_dbg", "Cannot start scanning when BT adapter is disabled");
        }
    }

    /**
     * Handle action StopScan in the provided background thread.
     */
    private void handleActionStopScan() {
        try {
            if (null != mScanProperties && null != mScanProperties.mLEScanner) {
                mScanProperties.mLEScanner.stopScan(mScanProperties.mScanCallback);
                Intent intent = new Intent();
                intent.setAction(BGX_SCAN_MODE_CHANGE);
                intent.putExtra("isscanning", false);
                sendBroadcast(intent);
                Log.d("bgx_dbg", "BGXpressService::StopScan");
            }
        } catch (IllegalStateException e) {
            Log.e("bgx_dbg", "Cannot stop scanning when BT adapter is disabled");
        }
    }

    /**
     * Handle BGX Connect in the provided background thread.
     * As it connects, we will broadcast various state changes
     * the caller will receive these and respond to them.
     *
     * @param deviceAddress Address of the device to which to connect.
     */
    private void handleActionBGXConnect(String deviceAddress) {
        BluetoothDevice btDevice = null;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (null == dps) {
            dps = new DeviceProperties();
            mDeviceProperties.put(deviceAddress, dps);
        } else {
            dps.fUserConnectionCanceled = false;
            assert (!dps.fGattBusy);
            assert (0 == dps.mIntentArray.size());
        }

        for (int i = 0; i < mScanProperties.mScanResults.size(); ++i) {
            BluetoothDevice iDevice = mScanProperties.mScanResults.get(i);
            if (0 == iDevice.getAddress().compareTo(deviceAddress)) {
                // found it.
                btDevice = iDevice;
                break;
            }
        }

        if (null != btDevice && !dps.fUserConnectionCanceled) {
            // connect to it.
            Log.d("bgx_dbg", "Found the device. Connect now.");
            if (null == dps.mBluetoothGatt) {
                if (Build.VERSION.SDK_INT >= 26) {
                    dps.mBluetoothGatt = btDevice.connectGatt(this, false, dps.mGattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK | BluetoothDevice.PHY_LE_2M_MASK, mHandler);
                } else {
                    dps.mBluetoothGatt = btDevice.connectGatt(this, false, dps.mGattCallback);
                }
            } else {
                dps.mBluetoothGatt.connect();
            }
        } else {
            Log.e("bgx_dbg", "Error: handleActionBGXConnect Failed to find the device " + deviceAddress + ".");

            Intent intent = new Intent();
            intent.setAction(BGX_CONNECTION_ERROR);
            intent.putExtra("status", -1);
            sendBroadcast(intent);
        }
    }

    /**
     * Handle BGX Disconnect.
     */
    private void handleActionBGXDisconnect(String deviceAddress) {
        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            if (dps.mBluetoothGatt != null) {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                broadcastIntent.putExtra("device", dps.mBluetoothGatt.getDevice());
                broadcastIntent.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());
                broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.DISCONNECTED);
                dps.mBluetoothGatt.disconnect();
                dps.mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTED;
                sendBroadcast(broadcastIntent);
            }
        }
    }

    /**
     * Handle getting available DMS versions.
     */
    @Deprecated
    private void handleActionGetDMSVersions(String apiKey, BGXPartID partID, String partIdentifier, String platformID) {
        HttpsURLConnection versConnection = null;
        try {
            URL dmsVersionsURL;

            if (null != partIdentifier && 8 == partIdentifier.length()) {
                // determine if it is BGX13 or BGX220
                String bgx_platform_id = platformID;

                if ((partIdentifier.equals(BGX13P_Device_Prefix) ||
                        partIdentifier.equals(BGX13S_Device_Prefix) ||
                        partIdentifier.equals(BGXV3P_Device_Prefix) ||
                        partIdentifier.equals(BGXV3S_Device_Prefix))) {
                    // bgx 13
                    bgx_platform_id = "bgx13";
                } else if ((partIdentifier.equals(BGX220P_Device_Prefix) || partIdentifier.equals(BGX220S_Device_Prefix))) {
                    // bgx 220
                    bgx_platform_id = "bgx220";
                } else if (null == bgx_platform_id) {
                    Log.d("bgx_dbg", "Error: unable to determine BGX platform, using bgx13.");
                    bgx_platform_id = "bgx13";
                }
                dmsVersionsURL = new URL(String.format("https://xpress-api.zentri.com/platforms/%s/products/%s/versions", partIdentifier, bgx_platform_id));
            } else {
                if (BGXPartID.BGX13P == partID) {
                    dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_P);
                } else if (BGXPartID.BGX13S == partID) {
                    dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_S);
                } else {
                    Log.e("bgx_dbg", "Invalid partID");
                    return;
                }
            }

            versConnection = (HttpsURLConnection) dmsVersionsURL.openConnection();
            versConnection.addRequestProperty("x-api-key", apiKey);

            InputStream sin = new BufferedInputStream(versConnection.getInputStream());

            int response = versConnection.getResponseCode();
            if (HttpURLConnection.HTTP_OK == response) {
                Log.d("bgx_dbg", "HTTP OK");

                byte[] contents = new byte[1024];
                String dmsResponse = new String();
                int bytesRead = 0;

                while (-1 != (bytesRead = sin.read(contents))) {
                    dmsResponse += new String(contents, 0, bytesRead);
                }

                Intent intent = new Intent();
                intent.setAction(DMS_VERSIONS_AVAILABLE);
                intent.putExtra("versions-available-json", dmsResponse);
                sendBroadcast(intent);
            } else {
                Log.e("bgx_dbg", "HTTP Error occurred while getting DMS versions: " + response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != versConnection) {
                versConnection.disconnect();
            }
        }
    }

    @Deprecated
    private void handleActionGetDMSVersion(String apiKey, String deviceAddress, String dmsVersion) {
        HttpsURLConnection versConnection = null;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);

        File partFolder;
        File versionFile;
        boolean fResult;

        Intent intent = new Intent();
        intent.setAction(OTA_STATUS_MESSAGE);
        intent.putExtra("ota_status", OTA_Status.Downloading);
        sendBroadcast(intent);

        partFolder = new File(this.getFilesDir(), dps.partIdentifier);

        if (!partFolder.exists()) {
            fResult = partFolder.mkdir();
            if (fResult) {
                Log.d("bgx_dbg", "Create directory: success.");
            } else {
                Log.d("bgx_dbg", "Create directory: failed.");
            }
        }

        versionFile = new File(partFolder, dmsVersion);

        Log.d("bgx_dbg", "File for dmsversion: " + versionFile.toString());

        if (!versionFile.exists()) {
            try {
                URL dmsVersionURL = new URL(String.format("https://xpress-api.zentri.com/platforms/%s/products/%s/versions/%s", dps.partIdentifier, dps.mPlatformString, dmsVersion));

                versConnection = (HttpsURLConnection) dmsVersionURL.openConnection();
                versConnection.addRequestProperty("x-api-key", apiKey);

                InputStream sin = new BufferedInputStream(versConnection.getInputStream());

                int response = versConnection.getResponseCode();
                if (HttpURLConnection.HTTP_OK == response) {
                    Log.d("bgx_dbg", "HTTP OK");

                    versionFile.createNewFile();
                    FileOutputStream fos = new FileOutputStream(versionFile);

                    byte[] contents = new byte[1024];

                    int bytesRead = 0;

                    while (-1 != (bytesRead = sin.read(contents))) {
                        fos.write(contents, 0, bytesRead);
                    }

                    fos.flush();
                    fos.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != versConnection) {
                    versConnection.disconnect();
                }
            }
        }

        intent = new Intent();
        intent.setAction(DMS_VERSION_LOADED);
        intent.putExtra("file_path", versionFile.toString());
        sendBroadcast(intent);
    }

    /**
     * Handle getting available firmware versions for device by passing its partIdentifier.
     */
    private void handleActionGetFirmwareVersions(String partIdentifier) {
        String firmwareJson = readFirmwareJsonFromAssets();
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;

        try {
            if (firmwareJson != null) {
                jsonObject = new JSONObject(firmwareJson);
            }

            if (jsonObject != null) {
                jsonArray = jsonObject.getJSONArray(partIdentifier);
            }

            if (jsonArray != null) {
                jsonArray = getExistingFirmwareFiles(jsonArray);

                Intent intent = new Intent();
                intent.setAction(FIRMWARE_VERSIONS_AVAILABLE);
                intent.putExtra("versions-available-json", jsonArray.toString());
                sendBroadcast(intent);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONArray getExistingFirmwareFiles(JSONArray array) {
        JSONArray tmpArray = new JSONArray();

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                String[] pathParts = obj.getString("file").split("/");
                String[] filesList = getAssets().list("firmware_files/" + pathParts[0] + "/" + pathParts[1] + "/");

                if (filesList != null && Arrays.asList(filesList).contains(pathParts[2])) {
                    tmpArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return tmpArray;
    }

    /**
     * Reads firmware.json file containing all available firmwares.
     */
    private String readFirmwareJsonFromAssets() {
        try {
            InputStream is = getAssets().open("firmware_files/firmware.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e("bgx_dbg", "Error occurred while reading firmware.json file.");
            return null;
        }
    }
}
