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
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.widget.TextView;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.view.View.GONE;
import static com.silabs.bgxpress.BGXpressService.ACTION_OTA_WITH_IMAGE;
import static com.silabs.bgxpress.BGXpressService.DMS_VERSION_LOADED;
import static com.silabs.bgxpress.BGXpressService.OTA_STATUS_MESSAGE;
import static com.silabs.bgxcommander.DeviceDetails.kBootloaderSecurityVersion;
import static com.silabs.bgxcommander.Password.ACTION_PASSWORD_UPDATED;
import static com.silabs.bgxcommander.PasswordKind.OTAPasswordKind;

import com.silabs.bgxpress.BGXpressService;
import com.silabs.bgxpress.OTA_Status;

public class FirmwareUpdate extends AppCompatActivity implements SelectionChangedListener {

    private Button installUpdateButton;

    private Button firmwareReleaseNotesButton;

    private BroadcastReceiver mFirmwareUpdateBroadcastReceiver;

    private RecyclerView mDMSVersionsRecyclerView;


    private JSONArray mDMSVersions;

    private BGXpressService.BGXPartID mBGXPartID;
    private String mBGXDeviceID;
    private String mBGXDeviceAddress;
    private String mBGXDeviceName;
    private String mBGXPartIdentifier;

    private ConstraintLayout selectionContents;
    private ConstraintLayout updateContents;
    private Button CancelUpdateButton;

    private JSONObject mSelectedObject;

    public final Context mContext = this;

    private TextView upperProgressMessageTextView;
    private TextView lowerProgressMessageTextView;

    private TextView currentVersionTextView;

    private ProgressBar progressBar;

    private Handler mHandler;
    private ImageView decorationImageView;

    private String mImagePath;


    @Override
    public void selectionDidChange(int position, JSONObject selectedObject) {
        mSelectedObject = selectedObject;
        Log.d("bgx_dbg", "selectionDidChange called.");

        if (position != -1) {
            installUpdateButton.setEnabled(true);
        } else {
            installUpdateButton.setEnabled(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmware_update);

        mHandler = new Handler();

        mBGXPartID = (BGXpressService.BGXPartID) getIntent().getSerializableExtra("bgx-part-id");
        mBGXDeviceID = getIntent().getStringExtra("bgx-device-id");
        mBGXDeviceAddress = getIntent().getStringExtra("DeviceAddress");
        mBGXDeviceName = getIntent().getStringExtra("DeviceName");
        mBGXPartIdentifier = getIntent().getStringExtra("bgx-part-identifier");

        mDMSVersionsRecyclerView = (RecyclerView)findViewById(R.id.dmsVersionsRecyclerView);
        mDMSVersionsRecyclerView.setHasFixedSize(true);
        mDMSVersionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        try {
            mDMSVersionsRecyclerView.setAdapter(new DMSVersionsAdapter(this, this, new JSONArray("")));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(BGXpressService.ACTION_DMS_GET_VERSIONS);
        String platformID = BGXpressService.getPlatformIdentifier(mBGXDeviceAddress);
        intent.setClass(this, BGXpressService.class);

        intent.putExtra("bgx-part-id", mBGXPartID);
        intent.putExtra("DeviceAddress", mBGXDeviceAddress);
        if (null != mBGXPartIdentifier) {
            intent.putExtra("bgx-part-identifier", mBGXPartIdentifier);
        }
        if (null != platformID) {
            intent.putExtra("bgx-platform-identifier", platformID);
        }

        startService(intent);

        selectionContents = findViewById(R.id.firmware_update_1);
        updateContents = findViewById(R.id.firmware_update_2);


        currentVersionTextView = findViewById(R.id.currentVersionTextView);

        installUpdateButton = (Button)findViewById(R.id.installUpdateBtn);
        installUpdateButton.setEnabled(false);

        firmwareReleaseNotesButton = (Button)findViewById(R.id.firmwareReleaseNotes);

        CancelUpdateButton = (Button)findViewById(R.id.CancelUpdateButton);
        decorationImageView = (ImageView)findViewById(R.id.decorationImageView);

        upperProgressMessageTextView = (TextView) findViewById(R.id.upperProgressMessage);
        lowerProgressMessageTextView = (TextView) findViewById(R.id.lowerProgressMessage);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);


        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (null != ab) {
            ab.setTitle("Firmware Available for " + mBGXDeviceName);
        }


        IntentFilter ifilter = new IntentFilter(BGXpressService.DMS_VERSIONS_AVAILABLE);

        ifilter.addAction(DMS_VERSION_LOADED);
        ifilter.addAction(OTA_STATUS_MESSAGE);
        ifilter.addAction(ACTION_PASSWORD_UPDATED);

        mFirmwareUpdateBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                switch (intent.getAction()) {

                    case ACTION_PASSWORD_UPDATED:
                        startOTAUpdate();
                        break;
                    case BGXpressService.DMS_VERSIONS_AVAILABLE: {
                        String versionJSON = intent.getStringExtra("versions-available-json");
                        try {
                            setDMSVersions(new JSONArray(versionJSON));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                        break;
                    case BGXpressService.DMS_VERSION_LOADED: {

                        mImagePath = intent.getStringExtra("file_path");
                        Log.d("bgx_dbg", "path: "+mImagePath);

                        startOTAUpdate();
                    }
                        break;
                    case BGXpressService.OTA_STATUS_MESSAGE: {
                        processOTAStatusMessage(intent);
                    }
                        break;
                }
            }
        };

        registerReceiver(mFirmwareUpdateBroadcastReceiver, ifilter);


        installUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // install the update.
                try {

                    assert (null != mSelectedObject);
                    Log.d("bgx_dbg", "Install update");
                    selectionContents.setVisibility(GONE);
                    updateContents.setVisibility(View.VISIBLE);

                    String dmsVersion = mSelectedObject.getString("version");

                    int ota_image_size = mSelectedObject.getInt("size");
                    progressBar.setMax(ota_image_size);

                    progressBar.setProgress(0);

                    lowerProgressMessageTextView.setText(R.string.OTA_Status_Downloading);

                    upperProgressMessageTextView.setText(getResources().getString(R.string.InstallingFirmwareFormatString, dmsVersion));

                    String sversion;
                    Intent intent = new Intent(BGXpressService.ACTION_DMS_REQUEST_VERSION);

                    intent.putExtra("bgx-part-id", mBGXPartID);
                    intent.putExtra("dms-version", mSelectedObject.getString("version"));
                    intent.putExtra("DeviceAddress", mBGXDeviceAddress);

                    intent.setClass(mContext, BGXpressService.class);
                    startService(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        CancelUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent();
                intent.setAction(BGXpressService.ACTION_OTA_CANCEL);
                intent.putExtra("DeviceAddress", mBGXDeviceAddress);
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);

            }
        });

        firmwareReleaseNotesButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Log.d("bgx_dbg", "Show the firmware release notes now.");

                Intent intent2 = new Intent(mContext, FirmwareReleaseNotesActivity.class);
                intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent2);

            }
        });

        currentVersionTextView.setText( BGXpressService.getFirmwareRevision(mBGXDeviceAddress) );
    }

    public void startOTAUpdate()
    {
        SharedPreferences sp = mContext.getSharedPreferences("com.silabs.bgxcommander", MODE_PRIVATE);
        Boolean fUseAckdWritesForOTA = sp.getBoolean("useAckdWritesForOTA", true);

        int writeType = fUseAckdWritesForOTA ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

        // tell BGXpressService to install it.
        Intent updateIntent = new Intent(mContext, BGXpressService.class);
        updateIntent.setAction(ACTION_OTA_WITH_IMAGE);
        updateIntent.putExtra("image_path", mImagePath);
        updateIntent.putExtra("DeviceAddress", mBGXDeviceAddress);
        updateIntent.putExtra("writeType", writeType);

        // add a password.
        AccountManager am = AccountManager.get(mContext);
        String password = Password.RetrievePassword(am, OTAPasswordKind, mBGXDeviceAddress );

        if (null != password) {
            updateIntent.putExtra("password", password);
        }

        startService(updateIntent);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mFirmwareUpdateBroadcastReceiver);
        super.onDestroy();
    }

    public void setDMSVersions(JSONArray dmsVersions) {


        try {
            List<JSONObject> jsons = new ArrayList<JSONObject>();
            for (int i = 0; i < dmsVersions.length(); i++) {
                jsons.add(dmsVersions.getJSONObject(i));
            }


            Collections.sort(jsons, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject o1, JSONObject o2) {
                    try {
                        String slversion = (String) o1.get("version");
                        String srversion = (String) o2.get("version");
                        Version lversion = new Version(slversion);
                        Version rversion = new Version(srversion);

                        return rversion.compareTo(lversion);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });

            mDMSVersions = new JSONArray();
            for (int i = 0; i < jsons.size(); ++i) {
                mDMSVersions.put(jsons.get(i));
            }
        } catch (JSONException e) {
            mDMSVersions = dmsVersions;
        }

        try {
            JSONArray tmpArray = null;

            tmpArray = new JSONArray(mDMSVersions.toString());
            Log.d("bgx_dbg", "Just set the tmpArray to the total list: " + tmpArray.toString());

            // check and assign decorator if needed.
            Integer booloaderVersion = BGXpressService.getBGXBootloaderVersion(mBGXDeviceAddress);

            if (booloaderVersion != -1 && booloaderVersion < kBootloaderSecurityVersion) {


                Drawable security_decoration = ContextCompat.getDrawable(mContext, R.drawable.security_decoration);
                decorationImageView.setBackground(security_decoration);
            } else {

                try {
                    Version vFirmwareRevision = new Version(BGXpressService.getFirmwareRevision(mBGXDeviceAddress));

                    for (int i = 0; i < tmpArray.length(); ++i) {
                        JSONObject rec = (JSONObject) tmpArray.get(i);
                        String sversion = (String) rec.get("version");
                        Version iversion = new Version(sversion);

                        if (iversion.compareTo(vFirmwareRevision) > 0) {
                            // newer version available.
                            Drawable update_decoration = ContextCompat.getDrawable(mContext, R.drawable.update_decoration);
                            decorationImageView.setBackground(update_decoration);
                            break;
                        }
                    }
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }

            mDMSVersionsRecyclerView.swapAdapter(new DMSVersionsAdapter(this, this, tmpArray), true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void processOTAStatusMessage(Intent intent) {
        Boolean fOTAFailed = intent.getBooleanExtra("ota_failed", false);
        if (fOTAFailed) {
            upperProgressMessageTextView.setText(R.string.FirmwareUpdateFailedLabel);
        }


        OTA_Status otaStatus = (OTA_Status) intent.getSerializableExtra("ota_status");
        switch (otaStatus) {
            case Invalid:
                break;
            case Idle:
                lowerProgressMessageTextView.setText("");
                break;
            case Password_Required: {
                lowerProgressMessageTextView.setText(R.string.OTA_Status_PasswordRequired);

                Intent passwordIntent = new Intent(mContext, Password.class);
                passwordIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                passwordIntent.putExtra("DeviceAddress", mBGXDeviceAddress);
                passwordIntent.putExtra("PasswordKind", OTAPasswordKind);
                passwordIntent.putExtra("DeviceName", mBGXDeviceName);

                mContext.startActivity(passwordIntent);
            }
                break;
            case Downloading:
                lowerProgressMessageTextView.setText(R.string.OTA_Status_Downloading);
                break;
            case Installing:
                lowerProgressMessageTextView.setText(R.string.OTA_Status_Installing);
                break;
            case Finishing:
                lowerProgressMessageTextView.setText(R.string.OTA_Status_Finishing);
                break;
            case Finished:
                lowerProgressMessageTextView.setText(R.string.OTA_Status_Finished);

                // show alert message telling user to forget the BGX in the settings.
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle("Important");
                builder.setMessage("You should select "+mBGXDeviceName+" in the Bluetooth Settings on any paired devices and choose \"Forget\" and then turn Bluetooth off and back on again for correct operation.");
                builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                OTA_Finished();
                            }
                        });
                    }
                });
                AlertDialog dlg = builder.create();
                dlg.show();


                break;
            case Failed:
                lowerProgressMessageTextView.setText(R.string.FirmwareUpdateFailedLabel);
                CancelUpdateButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
                break;
            case UserCanceled:
                lowerProgressMessageTextView.setText(R.string.OTA_Status_UserCanceled);
                progressBar.setProgress(0);
                CancelUpdateButton.setEnabled(false);
                break;
        }

        int bytesSent = intent.getIntExtra("bytes_sent", -1);
        if (-1 != bytesSent) {
            progressBar.setProgress(bytesSent);
        }

    }

    void OTA_Finished()
    {
        Log.d("bgx_dbg", "OTA is finished.");

        CancelUpdateButton.setVisibility(View.INVISIBLE);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
                Intent intent = new Intent();
                intent.setAction(BGXpressService.ACTION_BGX_DISCONNECT);
                intent.putExtra("DeviceAddress", mBGXDeviceAddress);
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);
            }
        }, 2000);


    }
}
