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

package com.silabs.bgxcommander;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Toast;

import com.silabs.bgxpress.BGX_CONNECTION_STATUS;
import com.silabs.bgxpress.BGXpressService;
import com.silabs.bgxpress.BusMode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.silabs.bgxcommander.PasswordKind.BusModePasswordKind;
import static com.silabs.bgxcommander.TextSource.LOCAL;
import static com.silabs.bgxcommander.TextSource.REMOTE;

public class DeviceDetails extends AppCompatActivity {

   // public BluetoothDevice mBluetoothDevice;
    public String mDeviceAddress;
    public String mDeviceName;

    public Handler mHandler;

    public int mDeviceConnectionState;

    private BroadcastReceiver mConnectionBroadcastReceiver;
    private BroadcastReceiver mBondReceiver;
    public final Context mContext = this;


    // UI Elements
    private EditText mStreamEditText;
    private EditText mMessageEditText;
    private RadioButton mStreamRB;
    private RadioButton mCommandRB;
    private Button mSendButton;

    private int mBusMode;

    private TextSource mTextSource = TextSource.UNKNOWN;
    private final int kAutoScrollMessage = 0x5C011;
    private final int kAutoScrollDelay = 800; // the time in ms between adding text and autoscroll.

    private MenuItem mIconItem;
    private MenuItem mUpdateItem;

    private BGXpressService.BGXPartID mBGXPartID;
    private String mBGXDeviceID;
    private String mBGXPartIdentifier;

    final static Integer kBootloaderSecurityVersion = 1229;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);



        mBusMode = BusMode.UNKNOWN_MODE;

        mStreamEditText = (EditText) findViewById(R.id.streamEditText);
        mMessageEditText = (EditText) findViewById(R.id.msgEditText);
        mStreamRB = (RadioButton) findViewById(R.id.streamRB);
        mCommandRB = (RadioButton) findViewById(R.id.commandRB);
        mSendButton = (Button) findViewById(R.id.sendButton);


        final IntentFilter bgxpressServiceFilter = new IntentFilter(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_MODE_STATE_CHANGE);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_DATA_RECEIVED);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_DEVICE_INFO);
        bgxpressServiceFilter.addAction(BGXpressService.DMS_VERSIONS_AVAILABLE);
        bgxpressServiceFilter.addAction(BGXpressService.BUS_MODE_ERROR_PASSWORD_REQUIRED);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_INVALID_GATT_HANDLES);

        mConnectionBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String intentDeviceAddress = intent.getStringExtra("DeviceAddress");
                if (intentDeviceAddress != null && intentDeviceAddress.length() > 1 && !intentDeviceAddress.equalsIgnoreCase(mDeviceAddress)) {
                    return;
                }

                switch(intent.getAction()) {

                    case BGXpressService.BGX_INVALID_GATT_HANDLES: {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle("Invalid GATT Handles");
                        builder.setMessage("The bonding information on this device is invalid (probably due to a firmware update). You should select "+mDeviceName+" in the Bluetooth Settings and choose \"Forget\".");
                        builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                        AlertDialog dlg = builder.create();
                        dlg.show();
                    }
                    break;

                    case BGXpressService.DMS_VERSIONS_AVAILABLE: {

                        Integer bootloaderVersion = BGXpressService.getBGXBootloaderVersion(mDeviceAddress);
                        if ( bootloaderVersion >= kBootloaderSecurityVersion) {

                            Log.d("bgx_dbg", "Received DMS Versions.");

                            String versionJSON = intent.getStringExtra("versions-available-json");
                            try {
                                JSONArray myDMSVersions = new JSONArray(versionJSON);

                                Log.d("bgx_dbg", "Device Address: " + mDeviceAddress);
                                Version vFirmwareRevision = new Version(BGXpressService.getFirmwareRevision(mDeviceAddress));

                                for (int i = 0; i < myDMSVersions.length(); ++i) {
                                    JSONObject rec = (JSONObject) myDMSVersions.get(i);
                                    String sversion = (String) rec.get("version");
                                    Version iversion = new Version(sversion);

                                    if (iversion.compareTo(vFirmwareRevision) > 0) {
                                        // newer version available.
                                        mIconItem.setIcon(ContextCompat.getDrawable(mContext, R.drawable.update_decoration));
                                        break;
                                    }
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    break;

                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        Log.d("bgx_dbg", "BGX Connection State Change");

                        BGX_CONNECTION_STATUS connectionState = (BGX_CONNECTION_STATUS) intent.getSerializableExtra("bgx-connection-status");
                        switch (connectionState) {
                            case CONNECTED:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to CONNECTED");
                                break;
                            case CONNECTING:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to CONNECTING");
                                break;
                            case DISCONNECTING:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to DISCONNECTING");
                                break;
                            case DISCONNECTED:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to DISCONNECTED");
                                finish();
                                break;
                            case INTERROGATING:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to INTERROGATING");
                                break;
                            default:
                                Log.d("bgx_dbg", "DeviceDetails - connection state changed to Unknown connection state.");
                                break;
                        }

                    }
                    break;
                    case BGXpressService.BGX_MODE_STATE_CHANGE: {
                        Log.d("bgx_dbg", "BGX Bus Mode Change");
                        setBusMode(intent.getIntExtra("busmode", BusMode.UNKNOWN_MODE));
                    }
                    break;
                    case BGXpressService.BGX_DATA_RECEIVED: {
                        String stringReceived = intent.getStringExtra("data");
                        processText(stringReceived, REMOTE);

                    }
                    break;
                    case BGXpressService.BGX_DEVICE_INFO: {

                        Integer bootloaderVersion = BGXpressService.getBGXBootloaderVersion(mDeviceAddress);

                        mBGXDeviceID = intent.getStringExtra("bgx-device-uuid");
                        mBGXPartID = (BGXpressService.BGXPartID) intent.getSerializableExtra("bgx-part-id" );
                        mBGXPartIdentifier = intent.getStringExtra("bgx-part-identifier");

                        if ( bootloaderVersion >= kBootloaderSecurityVersion) {
                            // request DMS VERSIONS at this point because now we know the part id.
                            Intent intent2 = new Intent(BGXpressService.ACTION_DMS_GET_VERSIONS);
                            String platformID = BGXpressService.getPlatformIdentifier(mDeviceAddress);

                            intent2.setClass(mContext, BGXpressService.class);

                            intent2.putExtra("bgx-part-id", mBGXPartID);
                            intent2.putExtra("DeviceAddress", mDeviceAddress);
                            intent2.putExtra("bgx-part-identifier", mBGXPartIdentifier);
                            if (null != platformID) {
                                intent2.putExtra("bgx-platform-identifier", platformID);
                            }
                            startService(intent2);
                        } else if ( bootloaderVersion > 0) {
                            mIconItem.setIcon(ContextCompat.getDrawable(mContext, R.drawable.security_decoration));
                        }
                    }
                    break;
                    case BGXpressService.BUS_MODE_ERROR_PASSWORD_REQUIRED: {

                        setBusMode(BusMode.STREAM_MODE);

                        Intent passwordIntent = new Intent(context, Password.class);
                        passwordIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        passwordIntent.putExtra("DeviceAddress", mDeviceAddress);
                        passwordIntent.putExtra("PasswordKind", BusModePasswordKind);
                        passwordIntent.putExtra("DeviceName", mDeviceName);

                        context.startActivity(passwordIntent);

                    }
                    break;
                }
            }
        };

        registerReceiver(mConnectionBroadcastReceiver, bgxpressServiceFilter);




        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {


                Log.d("bgx_dbg", "Handle message.");

                return false;
            }
        });



        mSendButton.setEnabled(true);
        mCommandRB.setEnabled(true);
        mStreamRB.setEnabled(true);

        mStreamRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mBusMode != BusMode.STREAM_MODE) {
                        sendBusMode(BusMode.STREAM_MODE);
                        setBusMode(BusMode.STREAM_MODE);
                    }
                }

            }
        });

        mCommandRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mBusMode != BusMode.REMOTE_COMMAND_MODE && mBusMode != BusMode.LOCAL_COMMAND_MODE) {
                        sendBusMode(BusMode.REMOTE_COMMAND_MODE);
                        setBusMode(BusMode.REMOTE_COMMAND_MODE);
                    }
                }

            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bgx_dbg", "Send button clicked.");


                String msgText = mMessageEditText.getText().toString();

                if (0 == msgText.compareTo("bytetest")) {

                    bytetest();
                    return;
                }

                // let's write it.
                Intent writeIntent = new Intent(BGXpressService.ACTION_WRITE_SERIAL_DATA);

                String msg2Send;

                final SharedPreferences sp = mContext.getSharedPreferences("com.silabs.bgxcommander", MODE_PRIVATE);
                Boolean fNewLinesOnSendValue =  sp.getBoolean("newlinesOnSend", true);

                if (fNewLinesOnSendValue) {
                    msg2Send = msgText + "\r\n";
                } else {
                    msg2Send = msgText;
                }

                writeIntent.putExtra("value", msg2Send );
                writeIntent.setClass(mContext, BGXpressService.class);
                writeIntent.putExtra("DeviceAddress", mDeviceAddress);
                startService(writeIntent);

                processText(msg2Send, LOCAL);
                mMessageEditText.setText("", EditText.BufferType.EDITABLE);
            }
        });

        final ImageButton clearButton = (ImageButton) findViewById(R.id.clearImageButton);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bgx_dbg", "clear");
                mStreamEditText.setText("");
            }
        });


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDeviceName = getIntent().getStringExtra("DeviceName");
        mDeviceAddress = getIntent().getStringExtra("DeviceAddress");

        android.support.v7.app.ActionBar ab = getSupportActionBar();
        if (null != ab) {
            ab.setTitle(mDeviceName);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(BGXpressService.ACTION_READ_BUS_MODE);
                intent.setClass(mContext, BGXpressService.class);
                intent.putExtra("DeviceAddress", mDeviceAddress);
                startService(intent);
            }
        });


        BGXpressService.getBGXDeviceInfo(this, mDeviceAddress);
    }

    @Override
    protected void onDestroy() {

        Log.d("bgx_dbg", "Unregistering the connectionBroadcastReceiver");
        unregisterReceiver(mConnectionBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devicedetails, menu);

        mIconItem = menu.findItem(R.id.icon_menuitem);
        mUpdateItem = menu.findItem(R.id.update_menuitem);
        mIconItem.setIcon(null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {

        switch(mi.getItemId()) {
            case R.id.update_menuitem: {
                Log.d("bgx_dbg", "Update menu item pressed.");

                String api_key = null;
                try {
                    ComponentName myService = new ComponentName(this, BGXpressService.class);
                    Bundle data = getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;
                    if (null != data) {
                        api_key = data.getString("DMS_API_KEY");
                    }
                } catch (PackageManager.NameNotFoundException exception) {
                    exception.printStackTrace();
                }

                if (null == api_key || 0 == api_key.compareTo("MISSING_KEY") ) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle("MISSING_KEY");
                    builder.setMessage("The DMS_API_KEY supplied in your app's AndroidManifest.xml is missing. Contact Silicon Labs xpress@silabs.com for a DMS API Key for BGX.");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    AlertDialog dlg = builder.create();
                    dlg.show();

                } else if ( null == mBGXPartID || BGXpressService.BGXPartID.BGXInvalid == mBGXPartID ) {
                    Toast.makeText(this, "Invalid BGX Part ID", Toast.LENGTH_LONG).show();
                } else {

                    Intent intent = new Intent(this, FirmwareUpdate.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("bgx-device-uuid", mBGXDeviceID);
                    intent.putExtra("bgx-part-id", mBGXPartID);
                    intent.putExtra("bgx-part-identifier", mBGXPartIdentifier);
                    intent.putExtra("DeviceAddress", mDeviceAddress);
                    intent.putExtra("DeviceName", mDeviceName);

                    startActivity(intent);
                }
            }
                break;
            case R.id.options_menuitem: {
                final SharedPreferences sp = mContext.getSharedPreferences("com.silabs.bgxcommander", MODE_PRIVATE);
                Boolean fNewLinesOnSendValue =  sp.getBoolean("newlinesOnSend", true);
                Boolean fUseAckdWritesForOTA = sp.getBoolean("useAckdWritesForOTA", true);

                final Dialog optionsDialog = new Dialog(this);
                optionsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                optionsDialog.setContentView(R.layout.optionsbox);

                final CheckBox newLineCB = optionsDialog.findViewById(R.id.newline_cb);
                final CheckBox otaAckdWrites = (CheckBox) optionsDialog.findViewById(R.id.acknowledgedOTA);

                newLineCB.setChecked(fNewLinesOnSendValue);
                otaAckdWrites.setChecked(fUseAckdWritesForOTA);

                Button saveButton = (Button)optionsDialog.findViewById(R.id.save_btn);
                saveButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {

                        Boolean fValue = newLineCB.isChecked();
                        Boolean fValue2 = otaAckdWrites.isChecked();

                        SharedPreferences.Editor editor = sp.edit();

                        editor.putBoolean("newlinesOnSend", fValue);
                        editor.putBoolean("useAckdWritesForOTA", fValue2);
                        
                        editor.apply();

                        optionsDialog.dismiss();
                    }
                });

                Button cancelButton = (Button)optionsDialog.findViewById(R.id.cancel_btn);
                cancelButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        optionsDialog.dismiss();
                    }
                });

                optionsDialog.show();
            }
            break;
        }

        return super.onOptionsItemSelected(mi);
    }

    @Override
    public void onBackPressed() {
        Log.d("bgx_dbg", "Back button pressed.");
        disconnect();

        finish();

        super.onBackPressed();
    }

    void disconnect()
    {
        Intent intent = new Intent(BGXpressService.ACTION_BGX_DISCONNECT);
        intent.setClass(mContext, BGXpressService.class);
        intent.putExtra("DeviceAddress", mDeviceAddress);
        startService(intent);
    }





    public void setBusMode(int busMode) {
        if (mBusMode != busMode) {

            mBusMode = busMode;


            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    switch (mBusMode) {
                        case BusMode.UNKNOWN_MODE:
                            mStreamRB.setChecked(false);
                            mCommandRB.setChecked(false);
                            break;
                        case BusMode.STREAM_MODE:
                            mStreamRB.setChecked(true);
                            mCommandRB.setChecked(false);

                            break;
                        case BusMode.LOCAL_COMMAND_MODE:
                        case BusMode.REMOTE_COMMAND_MODE:
                            mStreamRB.setChecked(false);
                            mCommandRB.setChecked(true);
                            break;
                    }
                }
            });

        }
    }

    public void sendBusMode(int busMode)
    {
        Intent intent = new Intent(BGXpressService.ACTION_WRITE_BUS_MODE);
        intent.setClass(this, BGXpressService.class);
        intent.putExtra("busmode", busMode);
        intent.putExtra("DeviceAddress", mDeviceAddress);

        /* Here we need to check to see if a busmode password is set for this device.
         * If there is one, then we would need to add it to the intent.
         */


        AccountManager am = AccountManager.get(this);
        String password = Password.RetrievePassword(am, BusModePasswordKind, mDeviceAddress);

        if (null != password) {
            intent.putExtra("password", password);
        }


        startService(intent);
    }

    public int getBusMode() {
        return mBusMode;
    }


    void processText(String text, TextSource ts ) {

        String newText;

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        final SharedPreferences sp = mContext.getSharedPreferences("com.silabs.bgxcommander", MODE_PRIVATE);
        Boolean fNewLinesOnSendValue =  sp.getBoolean("newlinesOnSend", true);

        switch (ts) {
            case LOCAL: {
                if (LOCAL != mTextSource && fNewLinesOnSendValue) {
                    ssb.append("\n>", new ForegroundColorSpan(Color.WHITE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                ssb.append(text, new ForegroundColorSpan(Color.WHITE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            break;
            case REMOTE: {
                if (REMOTE != mTextSource && fNewLinesOnSendValue) {
                    ssb.append("\n<", new ForegroundColorSpan(Color.GREEN), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                ssb.append(text, new ForegroundColorSpan(Color.GREEN), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            }
        }

        mStreamEditText.append(ssb);

        mTextSource = ts;

    }

    /*
     * The purpose of this function is to test ACTION_WRITE_SERIAL_BIN_DATA
     * by sending all the possible byte values.
     */
    void bytetest() {
        byte[] myByteArray = new byte[256];
        for (int i = 0; i < 256; ++i) {
            myByteArray[i] = (byte)i;
        }

        Intent writeIntent = new Intent(BGXpressService.ACTION_WRITE_SERIAL_BIN_DATA);
        writeIntent.putExtra("value", myByteArray);
        writeIntent.setClass(mContext, BGXpressService.class);
        writeIntent.putExtra("DeviceAddress", mDeviceAddress);
        startService(writeIntent);
    }

}
