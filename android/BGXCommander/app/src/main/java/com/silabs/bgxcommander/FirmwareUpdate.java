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

package com.silabs.bgxcommander;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.support.v7.widget.RecyclerView;
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
import static com.silabs.bgxcommander.BGXpressService.ACTION_OTA_WITH_IMAGE;
import static com.silabs.bgxcommander.BGXpressService.DMS_VERSION_LOADED;
import static com.silabs.bgxcommander.BGXpressService.OTA_STATUS_MESSAGE;
import static com.silabs.bgxcommander.DeviceDetails.kBootloaderSecurityVersion;


public class FirmwareUpdate extends AppCompatActivity implements SelectionChangedListener {

    private Button installUpdateButton;

    private Button firmwareReleaseNotesButton;

    private BroadcastReceiver mFirmwareUpdateBroadcastReceiver;

    private RecyclerView mDMSVersionsRecyclerView;
    //private RecyclerView.Adapter mDMSVersionsAdapter;

    private JSONArray mDMSVersions;

    private BGXpressService.BGXPartID mBGXPartID;
    private String mBGXDeviceID;
    private String mBGXDeviceAddress;
    private String mBGXDeviceName;

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

        mDMSVersionsRecyclerView = (RecyclerView)findViewById(R.id.dmsVersionsRecyclerView);
        mDMSVersionsRecyclerView.setHasFixedSize(true);
        mDMSVersionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        try {
            mDMSVersionsRecyclerView.setAdapter(new DMSVersionsAdapter(this, this, new JSONArray("")));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(BGXpressService.ACTION_DMS_GET_VERSIONS);
        intent.setClass(this, BGXpressService.class);

        intent.putExtra("bgx-part-id", mBGXPartID);
        intent.putExtra("DeviceAddress", mBGXDeviceAddress);

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


        android.support.v7.app.ActionBar ab = getSupportActionBar();
        if (null != ab) {
            ab.setTitle("Firmware Available for " + mBGXDeviceName);
        }


        IntentFilter ifilter = new IntentFilter(BGXpressService.DMS_VERSIONS_AVAILABLE);

        ifilter.addAction(DMS_VERSION_LOADED);
        ifilter.addAction(OTA_STATUS_MESSAGE);

        mFirmwareUpdateBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                switch (intent.getAction()) {

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

                        String versionFilePath = intent.getStringExtra("file_path");
                        Log.d("bgx_dbg", "path: "+versionFilePath);

                        // tell BGXpressService to install it.
                        Intent updateIntent = new Intent(mContext, BGXpressService.class);
                        updateIntent.setAction(ACTION_OTA_WITH_IMAGE);
                        updateIntent.putExtra("image_path", versionFilePath);
                        updateIntent.putExtra("DeviceAddress", mBGXDeviceAddress);
                        startService(updateIntent);
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
                        Log.d("bgx", "Got to here.");
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
            return;
        }


        OTA_Status otaStatus = (OTA_Status) intent.getSerializableExtra("ota_status");
        switch (otaStatus) {
            case Invalid:
                break;
            case Idle:
                lowerProgressMessageTextView.setText("");
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
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        OTA_Finished();
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
