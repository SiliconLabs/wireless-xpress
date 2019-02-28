/*
 * Copyright 2018 Silicon Labs
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

package com.silabs.bgxmulticonnect;

import android.app.IntentService;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.HashMap;
import java.net.URL;
import java.lang.String;
import java.net.MalformedURLException;
import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.net.ssl.HttpsURLConnection;

import static android.bluetooth.BluetoothProfile.GATT;


/**
 * @addtogroup bgx_service BGXpressService
 *
 * The Silicon Labs BGX device provides a bridge between Bluetooth Low Energy (BLE) and Serial communication.
 * BGXpressService is an Android Intent Service which makes it easier to write an Android app which interacts with BGX.
 * It supports discovery of BGX devices, connecting and disconnecting, setting and detecting changes to the bus mode of the BGX,
 * sending and receiving serial data, and OTA (Over The Air) Firmware Updates of the BGX accessed through
 * the Silicon Labs DMS (Device Management Service).
 *
 * @remark BGXpressService is designed to target Android SDK 27 and supports a minimum SDK version 23.
 * @remark BGXpressService is currently designed for connecting to one BGX at a time.
 *
 *
 * @{
 */

/**
 * This enum is the Connection Status of the BGXpressService instance.
 */
enum BGX_CONNECTION_STATUS {
     DISCONNECTED       ///< The BGXpressService is not connected to a BGX.
    ,CONNECTING         ///< The BGXpressService is connecting to a BGX device.
    ,INTERROGATING      ///< The BGXpressService has connected to a BGX device and is discovering bluetooth services and characteristics.
    ,CONNECTED          ///< The BGXpressService is connected to a BGX device.
    ,DISCONNECTING      ///< The BGXpressService is in the process of disconnecting from a BGX device.
    ,CONNECTIONTIMEDOUT ///< A connection that was in progress has timed out.
}



/**
 * This is the OTA status that can be used to
 * drive a user interface as part of the
 * OTA_STATUS_MESSAGE.
 */
enum OTA_Status {
    Invalid         /**< should never see this. It indicates an error has occured */
    ,Idle           /**< no OTA is happening */
    ,Downloading    /**< the firmware image is being downloaded through DMS */
    ,Installing     /**< the firmware image is being sent to the BGX device */
    ,Finishing      /**< the firmware image has been written and the bgx is being commanded to load it */
    ,Finished       /**< the BGX has acknowledged the command to load the firmware. The BGX is being rebooted */
    ,UserCanceled   /**< the intent ACTION_OTA_CANCEL has been received and the OTA operation is being canceled */
}

/**
 * An intent service which is used to control and communicate with a BGX device.
 */
public class BGXpressService extends IntentService {

    /* BGX Scanning Actions */
    /**
     * The BGXpressService receives this intent and it begins scanning.
     * There is no need to directly send this. You can call startActionStartScan() instead.
     */
    public static final String ACTION_START_SCAN = "com.silabs.bgx.action.StartScan";

    /**
     * The BGXpressService receives this intent and it stops scanning.
     * There is no need to directly send this. You can call startActionStopScan() instead.
     */
    public static final String ACTION_STOP_SCAN = "com.silabs.bgx.action.StopScan";

    /* BGX Connection Actions */
    /**
     * This is sent to cause the BGXpressService to connect to a BGX. Extras:
     *
     * DeviceAddress - String - The bluetooth address of the device to which to connect.
     *
     *
     * There is no need to directly send this. You can call startActionBGXConnect() using the device
     * address instead.
     */
    public static final String ACTION_BGX_CONNECT = "com.silabs.bgx.action.Connect";

    /**
     * This is sent to cause the BGXpressService to disconnect from a connected BGX.
     * TODO: Add a static method for this to send the intent.
     */
    public static final String ACTION_BGX_DISCONNECT = "com.silabs.bgx.action.Disconnect";

    /**
     * Attempt to cancel an in-progress connection operation.
     *
     * There is no need to call this directly. Use startActionBGXCancelConnect() instead.
     */
    public static final String ACTION_BGX_CANCEL_CONNECTION = "com.silabs.bgx.action.CancelConnection";

    /* BGX Serial Actions */
    /**
     * Reads the bus mode. When the read operation completes, a broadcast intent
     * will be sent with the action BGX_MODE_STATE_CHANGE.
     */
    public static final String ACTION_READ_BUS_MODE = "com.silabs.bgx.action.ReadBusMode";

    /**
     * Writes the bus mode. The service will not send a notification to indicate that
     * it succeeded at this time.
     *
     * Extras:
     * busmode - int - one of the values from BusMode: STREAM_MODE, LOCAL_COMMAND_MODE, or REMOTE_COMMAND_MODE.
     */
    public static final String ACTION_WRITE_BUS_MODE = "com.silabs.bgx.action.WriteBusMode";


    /**
     * Writes data to the BGX.
     *
     * value - String - the data to be written.
     */
    public static final String ACTION_WRITE_SERIAL_DATA = "com.silabs.bgx.action.WriteSerialData";

    /**
     * Writes byte data to the BGX
     *
     * value - byte [] - the byte array to be written.
     *
     */
    public static final String ACTION_WRITE_SERIAL_BIN_DATA = "com.silabs.bgx.action.WriteSerialBinData";

    /* BGX Misc Actions */

    /**
     * This request causes the BGXpressService to read the device uuid
     * and determine the part id. The result is sent out as a broadcast
     * intent BGX_DEVICE_INFO with the following extras:
     *    bgx-device-uuid - A string containing the device uuid.
     *    bgx-part-id - An enum value from BGXPartID.
     */
    public static final String ACTION_BGX_GET_INFO = "com.silabs.bgx.action.GetInfo";

    /* BGX DMS Actions */

    /**
     * Gets the firmware versions available from DMS.
     *
     * Extras:
     * bgx-part-id - BGXPartID - one of the following values: BGX13S or BGX13P
     *
     */
    public static final String ACTION_DMS_GET_VERSIONS      = "com.silabs.bgx.action.GetDMSVersions";

    /**
     * This request causes the specified DMS version to be loaded onto the phone.
     * When finished, the broadcast intent DMS_VERSION_LOADED is sent.
     *
     * Extras:
     * bgx-part-id - BGXPartID - one of the following values: BGX13S or BGX13P
     * dms-version - String - the DMS version number you want to retrieve.
     *
     * TO DO: Handle fail cases (e.g. specify a version number that doesn't exist as right now the calling app gets no reply.)
     */
    public static final String ACTION_DMS_REQUEST_VERSION   = "com.silabs.bgx.action.RequestDMSVersion";


    /* BGX OTA Actions */

    /**
     * image_path - String - path to the image file. You could get this from a DMS_VERSION_LOADED broadcast intent.
     */
    public static final String ACTION_OTA_WITH_IMAGE = "com.silabs.bgx.action.OTA";

    /**
     * Attempts to cancel an OTA operation in progress. If the cancellation is successful you will
     * get an OTA_STATUS_MESSAGE where the ota_status is UserCanceled
     *
     */
    public static final String ACTION_OTA_CANCEL     = "com.silabs.bgx.action.OTA.cancel";

    /**
     * Extras:
     * bgx-connection-status - a value from the BGX_CONNECTION_STATUS enum which is the new status.
     * device - a BluetoothDevice (not present for DISCONNECTED).
     */
    public static final String BGX_CONNECTION_STATUS_CHANGE  = "com.silabs.bgx.intent.connection-status-change";

    /**
     * Broadcast Intent which indicates a state change in the bgxss mode.
     *
     * Extras:
     * busmode - int - one of the values from BusMode: STREAM_MODE, LOCAL_COMMAND_MODE, or REMOTE_COMMAND_MODE.
     *
     */
    public static final String BGX_MODE_STATE_CHANGE        = "com.silabs.bgx.intent.mode-state-change";

    /**
     * Extras:
     * data - String - contains the data that was received from the BGX.
     */
    public static final String BGX_DATA_RECEIVED            = "com.silabs.bgx.intent.data-received";

    /**
     * This change is sent when scanning begins or ends. It has one extra.
     *
     * isscanning - boolean - indicates whether scanning is happening.
     */
    public static final String BGX_SCAN_MODE_CHANGE         = "com.silabs.bgx.intent.scan-mode-change";


    /**
     * This is sent as a broadcast intent when a BGX device is discovered during scanning.
     *
     */
    public static final String BGX_SCAN_DEVICE_DISCOVERED   = "com.silabs.bgx.intent.scan-device-discovered";


    /**
     * Notification that a specific version has been loaded and is attached to the intent as "file_path".
     *
     * file_path - string of the path to the file containing the version.
     */
    public static final String DMS_VERSION_LOADED           = "com.silabs.bgx.intent.dms-version-loaded";


    /**
     * This contains the following extras:
     * ota_failed : boolean. True means it failed. Use false as the default.
     * ota_status : OTA_Status enum. Tells the current status of the ota. Use Invalid as the default.
     * bytes_sent : int - number of bytes transfered in the firmware image. Not always present.
     *
     */
    public static final String OTA_STATUS_MESSAGE           = "com.silabs.bgx.ota.status";

   /**
     * a value that indicates the type of part.
     */
    public enum BGXPartID {
        BGX13S
        ,BGX13P
        ,BGXInvalid
    }

    /** UUID prefix for the BGX13S. */
    private static final String BGX13S_Device_Prefix = "080447D0";
    /** UUID prefix for the BGX13P. */
    private static final String BGX13P_Device_Prefix = "4C892A6A";
    /** UUID prefix for invalid device. */
    private static final String BGX_Invalid_Device_Prefix = "BAD1DEAD";

    /**
     *  This is sent when connection state becomes connected for the device.
     *
     *  param: bgx-device-uuid - a String
     *  param: bgx-part-id - a BGXPartID
     */
    public static final String BGX_DEVICE_INFO              = "com.silabs.bgx.intent.device-info";

    /**
     *  This is sent when the BGXConnectionState moves from Connecting to Disconnecting
     *  due to an error.
     *
     *  param: status - a GATT Status value as returned by the Android GATT API.
     */
    public static final String BGX_CONNECTION_ERROR         = "com.silabs.bgx.intent.connection_error";

    /**
     * Broadcast intent sent when the list of versions is retrieved from DMS.
     *
     * Extras:
     * versions-available-json - String - Contains JSON response from DMS with the list of firmware versions available.
     */
    public static final String DMS_VERSIONS_AVAILABLE       = "com.silabs.bgx.dms.versions-available";


    /** DMS URL for BGX13S firmware versions. */
    private static final String DMS_VERSIONS_URL_S       = "https://bgx13.zentri.com/products/bgx13s/versions";
    /** DMS URL for BGX13P firmware versions. */
    private static final String DMS_VERSIONS_URL_P       = "https://bgx13.zentri.com/products/bgx13p/versions";
    /** Brief description of whatever this is. */
    private final static char[] kHexArray =  "0123456789ABCDEF".toCharArray();


    /**
     * A private enum used to track the internal state of an OTA update operation.
     */
    private enum OTA_State {

         OTA_Idle
        ,WrittenZeroToControlCharacteristic
        ,WritingOTAImage
        ,WriteThreeToControlCharacteristic
    };

    /**
     * These are internal actions for intents that are queued internally to handle BGX setup operations.
     */
    private static final String ACTION_ENABLE_MODE_CHANGE_NOTIFICATION = "com.silabs.bgx.mode.notification.setup";
    private static final String ACTION_ENABLE_TX_CHANGE_NOTIFICATION = "com.silabs.bgx.tx.notification.setup";
    private static final String ACTION_SET_2M_PHY = "com.silabs.bgx.set2mphy";

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

                    // send broadcast intent
                    Intent broadcastIntent = new Intent();
                    HashMap<String, String> deviceRecord = new HashMap<String, String>();

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
                sendBroadcast(intent);
            }
        };


        ScanProperties() {

            this.mScanResults = new ArrayList<BluetoothDevice>();

            this.mLEScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();

            this.filters = new ArrayList<ScanFilter>();
            ScanFilter.Builder sb = new ScanFilter.Builder();
            sb.setServiceUuid(new ParcelUuid(UUID.fromString("331a36f5-2459-45ea-9d95-6142f0c4b307")));

            this.filters.add(sb.build());

            this.settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        }

    };


    static private ScanProperties mScanProperties = null;

    public class DeviceProperties {

        DeviceProperties() {


            this.fUserConnectionCanceled = false;
            this.fGattBusy = false;
            this.mIntentArray = new ArrayList<Intent>();
            this.mOTAState = OTA_State.OTA_Idle;
            this.fOTAUserCanceled = false;
            this.mConnectionTimer = null;
        }

        private BluetoothGatt mBluetoothGatt;

        /*
         * mConnectionTimer is being added in order to periodically check
         * the Bond state for a device during Interrogation. The reason
         * is that we want to be able to detect bond errors. So far I have
         * been unable to capture an ACTION_BOND_STATE_CHANGE intent either
         * using a broadcast receiver with an intent filter inside this service
         * or inside an activity. So we will try polling for it and see how that
         * works.
         */
        private Timer mConnectionTimer;

        private BGX_CONNECTION_STATUS mBGXDeviceConnectionState;
        private int mDeviceConnectionState;

        private BluetoothGattService mBGXSS;
        private BluetoothGattCharacteristic mRxCharacteristic;
        private BluetoothGattCharacteristic mTxCharacteristic;
        private BluetoothGattCharacteristic mBGXSSModeCharacteristic;


        private BluetoothGattService mOTAService;
        private BluetoothGattCharacteristic mOTAControlCharacteristic;
        private BluetoothGattCharacteristic mOTADataCharacteristic;
        private BluetoothGattCharacteristic mOTADeviceIDCharacterisitc;



        private volatile Boolean fUserConnectionCanceled;

        /**
         *  Variables related to OTA.
         */
        private OTA_State mOTAState;
        private boolean fOTAInProgress;
        private boolean fOTAUserCanceled;

        /**
         * These variables are used to write data a bit at a time.
         */
        private byte[] mData2Write;
        private int mWriteOffset;

        /**
         * mIntentQueue
         * The purpose of this is to solve a problem where the
         * BluetoothGatt cannot perform more than one asynchronous
         * operation at a time.
         *
         * Some intents are for operations that require either a read
         * or write operation on the BluetoothGatt object. Handling
         * more than one at a time will result in failure. So we will
         * queue intents that require a read or write.
         *
         * queueGattIntent()
         * Queues an intent for asynchronous execution when exclusive access
         * of the Gatt is guarenteed.
         *
         * clearGattBusyFlagAndExecuteNext()
         * Called when the current operation is finished with the gatt.
         *
         * executeNextGattIntent()
         * Retrieves a gatt intent from the queue and then executes the operation
         * and guarantees exclusive access to the gatt.
         */

        private ArrayList<Intent> mIntentArray;
        private volatile boolean fGattBusy;
        private Intent mLastExecutedIntent;

        private void clearGattBusyFlagAndExecuteNext() {
            if (fOTAInProgress) {
                Log.d("bgx_dbg", "clearGattBusyFlagAndExecuteNext called during fOTAInProgress");
                return;
            }
            if (fGattBusy) {
                fGattBusy = false;

                int sz = 0;
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

        private void queueGattIntent(Intent intent) {

            synchronized (this) {
                mIntentArray.add(intent);
            }

            Boolean fresult = mHandler.post(new Runnable() {
                @Override
                public void run() {
                    executeNextGattIntent();
                }
            });

            if (!fresult) {
                Log.e("bgx_dbg", "Error posting runnable.");
            } else {
                Log.d("bgx_dbg", "Post succeeded.");
            }
        }

        private void executeNextGattIntent() {
            if (!fGattBusy) {
                synchronized (this) {
                    if (mIntentArray.size() > 0) {
                        Intent intent = mIntentArray.remove(0);
                        mLastExecutedIntent = intent;
                        fGattBusy = true;
                        switch (intent.getAction()) {

                            case ACTION_WRITE_BUS_MODE: {
                                int busMode = intent.getIntExtra("busmode", BusMode.UNKNOWN_MODE);
                                byte[] modevalue = new byte[1];
                                modevalue[0] = (byte) busMode;
                                this.mBGXSSModeCharacteristic.setValue(modevalue);

                                boolean result = this.mBluetoothGatt.writeCharacteristic(this.mBGXSSModeCharacteristic);
                                if (!result) {
                                    Log.e("bgx_dbg", "mBGXSSModeCharacteristic write failed.");
                                }

                            }
                            break;

                            case ACTION_WRITE_SERIAL_DATA: {
                                String string2Write = intent.getStringExtra("value");
                                mData2Write = string2Write.getBytes();
                                mWriteOffset = 0;
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
                                assert(null != this.mBGXSSModeCharacteristic);
                                boolean result = this.mBluetoothGatt.readCharacteristic(this.mBGXSSModeCharacteristic);
                                if (!result) {
                                    Log.e("bgx_dbg", "mBluetoothGatt.readCharacteristic failed.");
                                }
                            }
                            break;
                            case ACTION_BGX_GET_INFO: {
                                assert(null != this.mOTADeviceIDCharacterisitc);
                                boolean readResult = this.mBluetoothGatt.readCharacteristic(this.mOTADeviceIDCharacterisitc);
                                if (!readResult) {
                                    Log.e("bgx_dbg", "Read initiation failed.");
                                } else {
                                    Log.d("bgx_dbg", "Began reading the OTADeviceIDCharacteristic");
                                }
                            }
                            break;

                            case ACTION_OTA_WITH_IMAGE: {

                                fOTAUserCanceled = false;
                                fOTAInProgress = true;
                                String image_path = intent.getStringExtra("image_path");
                                handleActionOTAWithImage(image_path);
                            }
                            break;

                            case ACTION_ENABLE_MODE_CHANGE_NOTIFICATION: {
                                assert(null != mBGXSSModeCharacteristic);
                                BluetoothGattDescriptor desc = mBGXSSModeCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                assert(null != desc);
                                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean fresult = mBluetoothGatt.writeDescriptor(desc);

                                if (!fresult) {
                                    Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for BGXSSMode.");
                                }

                                fresult = mBluetoothGatt.setCharacteristicNotification(mBGXSSModeCharacteristic, true);
                                if (!fresult) {
                                    Log.e("bgx_dbg", "Failed to set characterisitic notification for bgxss mode.");
                                }

                            }
                            break;
                            case ACTION_ENABLE_TX_CHANGE_NOTIFICATION: {
                                assert(null != mTxCharacteristic);
                                BluetoothGattDescriptor desc = mTxCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean fresult = mBluetoothGatt.writeDescriptor(desc);

                                if (!fresult) {
                                    Log.e("bgx_dbg", "An error occurred writing to the characteristic descriptor for Tx.");
                                }
                                mBluetoothGatt.setCharacteristicNotification(mTxCharacteristic, true);
                               // queueGattIntent(new Intent(BGXpressService.ACTION_READ_BUS_MODE));

                            }
                            break;
                            case ACTION_SET_2M_PHY: {
                                if (Build.VERSION.SDK_INT >= 26) {
                                    mBluetoothGatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
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
        }


        private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
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
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                Log.d("bgx_dbg", "onConnectionStateChanged for "+gatt.getDevice().getName()+" newState: "+newState);

                DeviceProperties dp = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (mDeviceConnectionState != newState){

                    mDeviceConnectionState = newState;

                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                    broadcastIntent.putExtra("device",  gatt.getDevice());
                    broadcastIntent.putExtra("DeviceAddress", gatt.getDevice().getAddress());


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
                                Boolean fBond = mBluetoothGatt.getDevice().createBond();
                                if (!fBond) {
                                    Log.e("bgx_dbg", "BluetoothDevice.createBond() returned false.");
                                }
                                mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.INTERROGATING;
                                broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.INTERROGATING);
                                startConnectionTimer();


                                sendBroadcast(broadcastIntent);
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
                            cancelConnectionTimer();
                            if (null != mBluetoothGatt) {
                                mBluetoothGatt.disconnect();
                            }

                            mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTED;
                            broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.DISCONNECTED);
                            Log.d("bgx_dbg", "connection state: DISCONNECTED.");
                            sendBroadcast(broadcastIntent);
                            break;
                        default:
                            Log.d("bgx_dbg", "connection state: OTHER.");
                            break;
                    }
                }
            }




            @Override
            public void onServicesDiscovered(BluetoothGatt gatt_param, int status) {

                final BluetoothGatt gatt = gatt_param;
                DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                super.onServicesDiscovered(gatt, status);
                Log.d("bgx_dbg", "onServicesDiscovered.");
                // look for BGX Streaming Service (BGXSS).

                dps.mBGXSS = gatt.getService(UUID.fromString("331a36f5-2459-45ea-9d95-6142f0c4b307"));
                if (null != mBGXSS) {
                    Log.d("bgx_dbg", "BGXSS found.");
                }
                dps.mRxCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("a9da6040-0823-4995-94ec-9ce41ca28833"));
                if (null != mRxCharacteristic) {
                    Log.d("bgx_dbg", "RxCharacteristic discovered.");
                }
                dps.mTxCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("a73e9a10-628f-4494-a099-12efaf72258f"));
                if (null != mTxCharacteristic) {
                    Log.d("bgx_dbg", "TxCharacteristic discovered.");
                }
                dps.mBGXSSModeCharacteristic = mBGXSS.getCharacteristic(UUID.fromString("75a9f022-af03-4e41-b4bc-9de90a47d50b"));
                if (null != mBGXSSModeCharacteristic) {
                    Log.d("bgx_dbg", "BGXSS Mode Characteristic discovered.");
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

                }

                Intent setupIntent = new Intent();
                setupIntent.setAction(ACTION_ENABLE_MODE_CHANGE_NOTIFICATION);
                queueGattIntent(setupIntent);

                setupIntent = new Intent();
                setupIntent.setAction(ACTION_ENABLE_TX_CHANGE_NOTIFICATION);
                queueGattIntent(setupIntent);

                if (Build.VERSION.SDK_INT >= 26) {
                    if (BluetoothAdapter.getDefaultAdapter().isLe2MPhySupported()) {
                        Log.d("bgx_dbg", "Queuing the 2m phy action");
                        queueGattIntent(new Intent(ACTION_SET_2M_PHY));
                    }
                }

                mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.CONNECTED;
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.CONNECTED);
                broadcastIntent.putExtra("device", mBluetoothGatt.getDevice());
                broadcastIntent.putExtra("DeviceAddress", mBluetoothGatt.getDevice().getAddress());
                sendBroadcast(broadcastIntent);

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (characteristic == mBGXSSModeCharacteristic) {
                    int BusMode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Intent intent = new Intent();
                    intent.setAction(BGX_MODE_STATE_CHANGE);
                    intent.putExtra("busmode", BusMode);
                    intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                    sendBroadcast(intent);
                    Log.d("bgx_dbg", "BusMode: " + BusMode);
                } else if (mTxCharacteristic == characteristic) {

                    final String myValue = mTxCharacteristic.getStringValue(0);
                    Intent intent = new Intent(BGX_DATA_RECEIVED);
                    intent.putExtra("data", myValue);
                    intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                    sendBroadcast(intent);

                }
                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status ) {

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
                        byte[] deviceIDValue =  dps.mOTADeviceIDCharacterisitc.getValue();
                        char[] hexChars = new char[deviceIDValue.length * 2];

                        for (int i = 0; i < deviceIDValue.length; ++i) {
                            int v = deviceIDValue[i] & 0xFF;
                            hexChars[i*2] = kHexArray[v >>> 4];
                            hexChars[1 + (i*2)] = kHexArray[v & 0x0F];
                        }
                        String bgxDeviceUUID = new String(hexChars);
                        Intent intent = new Intent(BGX_DEVICE_INFO);
                        intent.putExtra("bgx-device-uuid", bgxDeviceUUID);
                        BGXPartID devicePartID;
                        if (bgxDeviceUUID.startsWith(BGX13S_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX13S;
                        } else if (bgxDeviceUUID.startsWith(BGX13P_Device_Prefix)) {
                            devicePartID = BGXPartID.BGX13P;
                        } else if (bgxDeviceUUID.startsWith(BGX_Invalid_Device_Prefix)) {
                            devicePartID = BGXPartID.BGXInvalid;
                        } else {
                            Log.e("bgx_dbg", "Unknown BGX PartID");
                            devicePartID = BGXPartID.BGXInvalid;
                        }
                        intent.putExtra("bgx-part-id", devicePartID);
                        intent.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());

                        Log.d("bgx_dbg", "**** Read the BGX Device UUID: " + bgxDeviceUUID + " ****");

                        sendBroadcast(intent);
                    }
                }

                dps.clearGattBusyFlagAndExecuteNext();

            }


            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                DeviceProperties dps = mDeviceProperties.get(gatt.getDevice().getAddress());

                if (dps.mOTAControlCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite OTAControlCharacteristic");
                    if ( OTA_State.WrittenZeroToControlCharacteristic == dps.mOTAState ) {

                        if (BluetoothGatt.GATT_SUCCESS == status) {
                            // Continue with OTA.
                            dps.mOTAState = OTA_State.WritingOTAImage;
                            dps.writeOTAImageChunk();

                        } else {
                            // OTA Failed.
                            Log.e("bgx_dbg", "OTA Failed. Bad status on write to OTAControlCharacteristic.");
                            Intent intent = new Intent();
                            intent.setAction(OTA_STATUS_MESSAGE);
                            intent.putExtra("ota_status", OTA_Status.Idle);
                            intent.putExtra("ota_failed", true);
                            intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                            sendBroadcast(intent);
                        }

                    } else if (OTA_State.WriteThreeToControlCharacteristic == dps.mOTAState) {
                        Intent intent = new Intent();
                        intent.setAction(OTA_STATUS_MESSAGE);
                        intent.putExtra("ota_status", OTA_Status.Finished);
                        intent.putExtra("DeviceAddress", gatt.getDevice().getAddress());
                        sendBroadcast(intent);


                        dps.fOTAInProgress = false;
                        dps.mOTAState = OTA_State.OTA_Idle;
                        dps.clearGattBusyFlagAndExecuteNext();
                    }
                } else if (dps.mOTADataCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite OTADataCharacteristic");
                    if (BluetoothGatt.GATT_SUCCESS == status) {
                        if ( OTA_State.WritingOTAImage == dps.mOTAState ) {
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
                } else if (dps.mRxCharacteristic == characteristic) {

                    if (null != dps.mData2Write) {
                        writeChunkOfData();
                    } else {
                        dps.clearGattBusyFlagAndExecuteNext();
                    }

                } else if (dps.mBGXSSModeCharacteristic == characteristic) {
                    Log.d("bgx_dbg", "onCharacteristicWrite - mode characteristic. Status: " + status);
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
                Log.e("bgx_dbg", "onDescriptorWrite failed.");
            }

            dps.clearGattBusyFlagAndExecuteNext();
        }

        };




        public void startConnectionTimer() {
            assert(null == mConnectionTimer);

            mConnectionTimer = new Timer();
            mConnectionTimer.schedule(new TimerTask() {
                @Override
                public void run() {

                    Boolean fTrigger = false;

                    switch (mBluetoothGatt.getDevice().getBondState()) {
                        case BluetoothDevice.BOND_NONE: {
                            Log.d("bgx_dbg", "BOND_NONE");
                            if (!mBluetoothGatt.getDevice().createBond()) {
                                Log.e("bgx_dbg", "Error createBond returned false.");
                            }
                        }
                            break;
                        case BluetoothDevice.BOND_BONDING:
                            Log.d("bgx_dbg", "BOND_BONDING");
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            Log.d("bgx_dbg", "BOND_BONDED");
                            fTrigger = true;
                            break;
                    }

                    if (fTrigger) {

                        boolean fDiscoverServices = mBluetoothGatt.discoverServices();
                        if (fDiscoverServices) {
                            Log.d("bgx_dbg", "Discover Services returned true.");
                        } else {
                            Log.d("bgx_dbg", "Discover Services returned false.");
                        }

                        cancelConnectionTimer();
                    }

                }
            }, 200, 200);
        }

        public void cancelConnectionTimer() {
            if (null != mConnectionTimer) {
                mConnectionTimer.cancel();
                mConnectionTimer = null;
            }
        }


        private static final int kDataWriteChunkSize = 20;

        /**
         * writeChunkOfData
         *
         * This is called to write a bit of data to the Rx characteristic.
         * and will be called again from OnCharacteristicWrite until all
         * of the string has been written. The last call to this function
         * clears mString2Write which indicates to OnCharacteristicWrite
         * that no further calls to this function are needed in which case
         * it calls clearGattBusyFlagAndExecuteNext().
         */
        private void writeChunkOfData() {

            int ibegin, iend;

            ibegin = this.mWriteOffset;
            iend = this.mData2Write.length - 1;

            if (iend - ibegin > kDataWriteChunkSize) {
                iend = ibegin + kDataWriteChunkSize;
            }


            this.mRxCharacteristic.setValue( Arrays.copyOfRange(this.mData2Write, ibegin, iend));

            if (this.mData2Write.length-1 > iend) {
                this.mWriteOffset = iend ;
            } else {
                this.mWriteOffset = 0;
                this.mData2Write = null;
            }

            boolean writeResult = this.mBluetoothGatt.writeCharacteristic(this.mRxCharacteristic);
            if (!writeResult) {
                Log.e("bgx_dbg", "An error occurred while writing to the rx characteristic of the bgxss service.");
            }
        }


        private FileInputStream mOTAImageFileInputStream = null;

        private final int kChunkSize = 244;

        private int ota_bytes_sent;

        private void writeOTAImageChunk() {
            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            try {
                byte[] buffer = new byte[kChunkSize];
                int bytesRead = mOTAImageFileInputStream.read(buffer);

                Log.d("bgx_dbg", "mOTAImageFileInputStream.read(buffer) called returned "+bytesRead);

                if (-1 == bytesRead) {
                    // this indicates an error or EOF which in this case
                    // can be treated as an error.
                    Log.e("bgx_dbg", "Error: An error occurred while trying to read the OTA image.");
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

                if (bytesRead < kChunkSize) {
                    Log.d("bgx_dbg", "Writing the final chunk (" + bytesRead + " bytes) of the OTA image. " + ota_bytes_sent + " bytes written");

                    this.mOTAState = OTA_State.WriteThreeToControlCharacteristic;
                    Log.d("bgx_dbg", "Change OTA_State to WriteThreeToControlCharacteristic.");
                    byte[] tempBuffer = new byte[bytesRead];
                    System.arraycopy(buffer, 0, tempBuffer, 0, bytesRead);
                    this.mOTADataCharacteristic.setValue(tempBuffer);
                } else {
                    Log.d("bgx_dbg", "Writing 244 bytes of the OTA image. "+ota_bytes_sent + " bytes written");
                    this.mOTADataCharacteristic.setValue(buffer);
                }

                if (!this.mBluetoothGatt.writeCharacteristic(this.mOTADataCharacteristic)) {
                    Log.e("bgx_dbg", "Failed to write to OTADataCharacteristic.");
                }


            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * finishOTA
         *
         *  write the final three to the OTA control characteristic.
         */
        private void finishOTA() {

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
            if (!this.mBluetoothGatt.writeCharacteristic(this.mOTAControlCharacteristic)) {
                Log.e("bgx_dbg", "Failed to write to OTAControlCharacteristic.");
            }
        }

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
         * handleActionOTAWithImage
         *
         * Perform the OTA.
         *
         * @param ota_image_path the path to the image to use for the OTA.
         */

        private void handleActionOTAWithImage(String ota_image_path)
        {
            /**
             *
             * first verify the assumptions:
             * 1. We are connected to a device.
             * 2. The path passed as an argument is to a file that exists.
             *
             */


            ota_bytes_sent = 0;

            if (this.fOTAUserCanceled) {
                ReportOTACanceled();
                return;
            }

            BluetoothManager bleManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);

            List<BluetoothDevice> deviceList = bleManager.getConnectedDevices(GATT);

            File ota_image  = new File(ota_image_path);

            /**
             * Set up writes for OTA image.
             */

            try {
                mOTAImageFileInputStream = new FileInputStream(ota_image_path);
            } catch (FileNotFoundException exception) {
                exception.printStackTrace();
                // report OTA Failure.
                Log.e("bgx_dbg", "Error OTA Image file not found: " + ota_image_path);
                return;
            }

            /**
             * Write a zero to the OTA Control characteristic.
             */

            this.mOTAState = OTA_State.WrittenZeroToControlCharacteristic;

            byte zeroValue[] = new byte[1];
            zeroValue[0] = (byte) 0;

            this.mOTAControlCharacteristic.setValue(zeroValue);
            if (!this.mBluetoothGatt.writeCharacteristic( this.mOTAControlCharacteristic )) {
                Log.e("bgx_dbg", "Failed to write to OTAControlCharacteristic");
            }

        }


        /**
         * clearCancelFlag
         *
         * Call this before initiating a connection in order to clear the connection cancel flag.
         *
         * To Do: maybe we can clear this a better way (such as when the user calls connect?)
         */
        public void clearCancelFlag() {
            if (this.fUserConnectionCanceled) {
                Log.d("bgx_dbg", "Clearing the cancel flag.");
            }
            this.fUserConnectionCanceled = false;
        }

    };




    public BGXpressService() {
        super("BGXpressService");
    }

    static private Map<String, DeviceProperties> mDeviceProperties = null;


    static public Handler mHandler = null;


    public void onCreate()
    {
        super.onCreate();

        if (null == mHandler) {
            mHandler = new Handler();
        }

        if (null == mScanProperties) {
            mScanProperties = new ScanProperties();
        }

        if (null == mDeviceProperties) {
            mDeviceProperties = new HashMap<String, DeviceProperties>();
        }



    }

    public void onDestroy()
    {
        super.onDestroy();
    }

    /**
     * Starts this service to perform action Start Scan. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionStartScan(Context context) {

        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_START_SCAN);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Stop Scan. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionStopScan(Context context) {

        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_STOP_SCAN);
        context.startService(intent);
    }

    /**
     * startActionBGXConnect
     *
     * Attempt to connect to the specified device.
     *
     * @param context
     * @param deviceAddress the Bluetooth address of the device to which you wish to connect.
     */
    public static void startActionBGXConnect(Context context, String deviceAddress) {

        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_CONNECT);
        intent.putExtra("DeviceAddress", deviceAddress);
        context.startService(intent);
    }

    /**
     * startActionBGXCancelConnect
     *
     * Attempt to cancel an in-progress connection operation.
     *
     * @param context
     */
    public static void startActionBGXCancelConnect(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_CANCEL_CONNECTION);
        intent.putExtra("DeviceAddress", deviceAddress);
        context.startService(intent);
    }

    /**
     * startActionBGXDisconnect
     *
     * Disconnect a BGX Device.
     * @param deviceAddress the Bluetooth address of the device to which you wish to connect.
     *
     * @param context
     */
    public static void startActionBGXDisconnect(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.setAction(ACTION_BGX_DISCONNECT);
        intent.putExtra("DeviceAddress", deviceAddress);
        context.startService(intent);
    }



    /**
     * getBGXDeviceInfo
     *
     * Starts an operation to get the device info which
     * is the part id and the device uuid.
     *
     * @param context
     */
    public static void getBGXDeviceInfo(Context context, String deviceAddress) {
        Intent intent = new Intent(context, BGXpressService.class);
        intent.putExtra("DeviceAddress", deviceAddress);
        intent.setAction(ACTION_BGX_GET_INFO);
        context.startService(intent);
    }

    public static BGX_CONNECTION_STATUS getBGXDeviceConnectionStatus(String deviceAddress) {
        BGX_CONNECTION_STATUS cs = BGX_CONNECTION_STATUS.DISCONNECTED;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            cs = dps.mBGXDeviceConnectionState;
        }

        return cs;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            int busMode;
            boolean result;

            String bgxDeviceAddress = (String) intent.getStringExtra("DeviceAddress");
            DeviceProperties dps = mDeviceProperties.get(bgxDeviceAddress);


            final String action = intent.getAction();
            if (ACTION_START_SCAN.equals(action)) {

                handleActionStartScan();
            } else if (ACTION_STOP_SCAN.equals(action)) {

                handleActionStopScan();
            } else if (ACTION_BGX_CONNECT.equals(action)) {

                handleActionBGXConnect(bgxDeviceAddress);
            } else if ( ACTION_BGX_DISCONNECT.equals(action) ) {
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
                dps.fUserConnectionCanceled = true;
                if (null !=  dps.mBluetoothGatt ) {
                    dps.mBluetoothGatt.disconnect();
                }
            } else if (ACTION_DMS_GET_VERSIONS.equals(action)) {

                String apiKey = getDmsAPIKey();
                BGXPartID partID = (BGXPartID) intent.getSerializableExtra("bgx-part-id");
                handleActionGetDMSVersions(apiKey, partID);
            } else if (ACTION_BGX_GET_INFO.equals(action)) {
                dps.queueGattIntent(intent);

            } else if (ACTION_DMS_REQUEST_VERSION.equals(action)) {


                String dmsVersion = intent.getStringExtra("dms-version");
                String apiKey = getDmsAPIKey();
                BGXPartID partID = (BGXPartID) intent.getSerializableExtra("bgx-part-id");
                Log.d("bgx_dbg", "Version Record: " + dmsVersion);

                handleActionGetDMSVersion(apiKey, partID, dmsVersion);


            } else if (ACTION_OTA_WITH_IMAGE.equals(action)) {
                dps.queueGattIntent(intent);

            } else if (ACTION_OTA_CANCEL.equals(action)) {
                if (dps.fOTAInProgress) {
                    // cancel it.
                    dps.fOTAUserCanceled = true;
                }
            }
        }

    }

    /**
     * Handle action StartScan in the provided background thread.
     */
    private void handleActionStartScan() {
        Log.d("bgx_dbg", "BGXpressService::StartScan");

        mScanProperties.mScanResults = new ArrayList<BluetoothDevice>();
        mScanProperties.mLEScanner.startScan(mScanProperties.filters, mScanProperties.settings, mScanProperties.mScanCallback);
        Intent intent = new Intent();
        intent.setAction(BGX_SCAN_MODE_CHANGE);
        intent.putExtra("isscanning", true);
        sendBroadcast(intent);
    }

    /**
     * Handle action StopScan in the provided background thread.
     */
    private void handleActionStopScan() {

        Log.d("bgx_dbg", "BGXpressService::StopScan");

        mScanProperties.mLEScanner.stopScan(mScanProperties.mScanCallback);
        Intent intent = new Intent();
        intent.setAction(BGX_SCAN_MODE_CHANGE);
        intent.putExtra("isscanning", false);
        sendBroadcast(intent);
    }

    /**
     * Handle BGX Connect in the provided background thread.
     *  As it connects, we will broadcast various state changes
     *  the caller will receive these and respond to them.
     *
     * @param deviceAddress
     */
    private void handleActionBGXConnect(String deviceAddress) {
        BluetoothDevice btDevice = null;

        DeviceProperties dps = mDeviceProperties.get(deviceAddress);

        for (int i = 0; i < mScanProperties.mScanResults.size(); ++i) {

            BluetoothDevice iDevice = (BluetoothDevice) mScanProperties.mScanResults.get(i);
            if (0 == iDevice.getAddress().compareTo(deviceAddress)) {
                // found it.
                btDevice = iDevice;
                break;
            }

        }

        if (null == dps) {
            dps = new DeviceProperties();
            mDeviceProperties.put(deviceAddress, dps);
        } else {
            dps.fUserConnectionCanceled = false;
        }

        if (null != btDevice && !dps.fUserConnectionCanceled) {
            // connect to it.
            Log.d("bgx_dbg", "Found the device. Connect now.");
            if (null == dps.mBluetoothGatt) {
                dps.mBluetoothGatt = btDevice.connectGatt(this, false, dps.mGattCallback);
            } else {
                dps.mBluetoothGatt.connect();
            }
        }
    }

    /**
     * Handle BGX Disconnect.
     *
     */
    private void handleActionBGXDisconnect(String deviceAddress)
    {
        DeviceProperties dps = mDeviceProperties.get(deviceAddress);
        if (dps != null) {
            if (dps.mBluetoothGatt != null) {

                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(BGX_CONNECTION_STATUS_CHANGE);
                broadcastIntent.putExtra("device",  dps.mBluetoothGatt.getDevice());
                broadcastIntent.putExtra("DeviceAddress", dps.mBluetoothGatt.getDevice().getAddress());
                broadcastIntent.putExtra("bgx-connection-status", BGX_CONNECTION_STATUS.DISCONNECTED);
                dps.mBluetoothGatt.disconnect();
                dps.mBluetoothGatt = null;
                dps.mBGXDeviceConnectionState = BGX_CONNECTION_STATUS.DISCONNECTED;
                sendBroadcast(broadcastIntent);
            }
        }
    }



    private void handleActionGetDMSVersions(String apiKey, BGXPartID partID)
    {
        HttpsURLConnection versConnection = null;
        try {
            URL dmsVersionsURL;
            if (BGXPartID.BGX13P == partID) {
                dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_P);
            } else if (BGXPartID.BGX13S == partID) {
                dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_S);
            } else {
                Log.e("bgx_dbg", "Invalid partID");
                return;
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

                while (  -1 != ( bytesRead = sin.read(contents))) {
                    dmsResponse += new String(contents, 0, bytesRead);
                }

                // broadcast the response.
                Intent intent = new Intent();
                intent.setAction(DMS_VERSIONS_AVAILABLE);
                intent.putExtra("versions-available-json", dmsResponse);
                sendBroadcast(intent);
            } else {
                Log.e("bgx_dbg", "HTTP Error occurred while getting DMS versions: " + response);
            }

        }  catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (null != versConnection) {
                versConnection.disconnect();
            }
        }
    }

    private void handleActionGetDMSVersion(String apiKey, BGXPartID partID, String dmsVersion)
    {
        HttpsURLConnection versConnection = null;

        // create the file name.

        File partFolder = null;
        File versionFile = null;
        boolean fResult;

        Intent intent = new Intent();
        intent.setAction(OTA_STATUS_MESSAGE);
        intent.putExtra("ota_status", OTA_Status.Downloading);
        sendBroadcast(intent);

        if (BGXPartID.BGX13P.equals(partID)) {

            partFolder = new File(this.getFilesDir(), "BGX13P");

        } else if (BGXPartID.BGX13S.equals(partID)) {
            partFolder = new File(this.getFilesDir(), "BGX13S");
        }

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
                URL dmsVersionsURL;
                if (BGXPartID.BGX13P == partID) {
                    dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_P + "/" + dmsVersion);
                } else if (BGXPartID.BGX13S == partID) {
                    dmsVersionsURL = new URL(BGXpressService.DMS_VERSIONS_URL_S + "/" + dmsVersion);
                } else {
                    Log.e("bgx_dbg", "Invalid partID");
                    return;
                }

                versConnection = (HttpsURLConnection) dmsVersionsURL.openConnection();
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

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
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




}

/** @} */
