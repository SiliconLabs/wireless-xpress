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
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import static android.view.View.GONE;
import static com.silabs.bgxcommander.BGXpressService.ACTION_OTA_WITH_IMAGE;
import static com.silabs.bgxcommander.BGXpressService.DMS_VERSION_LOADED;
import static com.silabs.bgxcommander.BGXpressService.OTA_STATUS_MESSAGE;
import static com.silabs.bgxcommander.TagsToShow.FW_VERS_ALL;
import static com.silabs.bgxcommander.TagsToShow.FW_VERS_RELEASE;

enum TagsToShow {
    FW_VERS_RELEASE,
    FW_VERS_ALL
}



public class FirmwareUpdate extends AppCompatActivity implements SelectionChangedListener {

    TagsToShow mTagsToShow;

    private RadioButton mReleaseRB;
    private RadioButton mAllRB;

    private Button installUpdateButton;

    private BroadcastReceiver mFirmwareUpdateBroadcastReceiver;

    private RecyclerView mDMSVersionsRecyclerView;
    private RecyclerView.Adapter mDMSVersionsAdapter;

    private JSONArray mDMSVersions;

    private BGXpressService.BGXPartID mBGXPartID;
    private String mBGXDeviceID;

    private ConstraintLayout selectionContents;
    private ConstraintLayout updateContents;
    private Button CancelUpdateButton;

    private JSONObject mSelectedObject;

    public final Context mContext = this;

    private TextView upperProgressMessageTextView;
    private TextView lowerProgressMessageTextView;
    private ProgressBar progressBar;

    private Handler mHandler;

    @Override
    public void selectionDidChange(int position, JSONObject selectedObject) {
        mSelectedObject = selectedObject;
        Log.d("debug", "selectionDidChange called.");

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

        mTagsToShow = FW_VERS_RELEASE;

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

        startService(intent);

        selectionContents = findViewById(R.id.firmware_update_1);
        updateContents = findViewById(R.id.firmware_update_2);

        installUpdateButton = (Button)findViewById(R.id.installUpdateBtn);
        installUpdateButton.setEnabled(false);

        CancelUpdateButton = (Button)findViewById(R.id.CancelUpdateButton);

        mReleaseRB = (RadioButton)findViewById(R.id.releaseRB);
        mAllRB = (RadioButton) findViewById(R.id.allRB);


        upperProgressMessageTextView = (TextView) findViewById(R.id.upperProgressMessage);
        lowerProgressMessageTextView = (TextView) findViewById(R.id.lowerProgressMessage);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        mReleaseRB.setEnabled(true);
        mAllRB.setEnabled(true);
        mReleaseRB.setChecked(true);

        mReleaseRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mAllRB.setChecked(false);
                    mTagsToShow = FW_VERS_RELEASE;
                    setDMSVersions(mDMSVersions);
                }
            }
        });

        mAllRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mReleaseRB.setChecked(false);
                    mTagsToShow = FW_VERS_ALL;

                    setDMSVersions(mDMSVersions);
                }
            }
        });

        android.support.v7.app.ActionBar ab = getSupportActionBar();
        if (null != ab) {
            ab.setTitle("BGX Firmware Update");
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
                        Log.d("debug", "path: "+versionFilePath);

                        // tell BGXpressService to install it.
                        Intent updateIntent = new Intent(mContext, BGXpressService.class);
                        updateIntent.setAction(ACTION_OTA_WITH_IMAGE);
                        updateIntent.putExtra("image_path", versionFilePath);
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
                    Log.d("debug", "Install update");
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
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);

//                selectionContents.setVisibility(View.VISIBLE);
//                updateContents.setVisibility(GONE);
            }
        });
    }


    @Override
    public void onDestroy() {
        unregisterReceiver(mFirmwareUpdateBroadcastReceiver);
        super.onDestroy();
    }

    public void setDMSVersions(JSONArray dmsVersions) {


        mDMSVersions = dmsVersions;

        try {
            JSONArray tmpArray = null;

            if (mTagsToShow == FW_VERS_RELEASE) {

                tmpArray = new JSONArray();
                for (int i = 0; i < mDMSVersions.length(); ++i) {
                    JSONObject rec = (JSONObject) mDMSVersions.get(i);
                    String recTag = (String) rec.get("tag");
                    if (recTag.equals("release")) {
                        tmpArray.put(rec);
                    }
                }
            } else {
                tmpArray = new JSONArray(mDMSVersions.toString());
                Log.d("debug", "Just set the tmpArray to the total list: " + tmpArray.toString());

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
        Log.d("Debug", "OTA is finished.");

        CancelUpdateButton.setVisibility(View.INVISIBLE);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
                Intent intent = new Intent();
                intent.setAction(BGXpressService.ACTION_BGX_DISCONNECT);
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);
            }
        }, 2000);


    }
}
