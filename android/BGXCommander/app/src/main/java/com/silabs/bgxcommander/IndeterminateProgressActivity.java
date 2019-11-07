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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.silabs.bgxpress.BGX_CONNECTION_STATUS;
import com.silabs.bgxpress.BGXpressService;

import static com.silabs.bgxpress.BGXpressService.BGX_CONNECTION_ERROR;

public class IndeterminateProgressActivity extends AppCompatActivity {

    BroadcastReceiver mBroadcastReceiver;

    Button mCancelButton;
    TextView mStatusLabel;
    TextView mDeviceNameLabel;

    Handler mHandler;
    String mDeviceAddress;
    String mDeviceName;

    ImageView mBondImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indeterminate_progress);

        mDeviceNameLabel = (TextView)findViewById(R.id.deviceNameLabel);
        mBondImageView = (ImageView)findViewById(R.id.bond_state_image_view);

        mDeviceAddress = getIntent().getStringExtra("DeviceAddress");
        mDeviceName = getIntent().getStringExtra("DeviceName");

        final IndeterminateProgressActivity myActivity = this;

        mCancelButton = (Button)findViewById(R.id.cancelButton);

        mStatusLabel = (TextView)findViewById(R.id.connectionStatusTextView);
        mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTING);
        mDeviceNameLabel.setText(mDeviceName);

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bgx_dbg", "cancel button clicked.");

                BGXpressService.startActionBGXCancelConnect(myActivity, mDeviceAddress);

                myActivity.finish();
            }
        });

        mHandler = new Handler();

        final IntentFilter bgxpressServiceFilter = new IntentFilter(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);
        bgxpressServiceFilter.addAction(BGX_CONNECTION_ERROR);
        bgxpressServiceFilter.addAction(BGXpressService.BGX_INVALID_GATT_HANDLES);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String myDeviceAddress = intent.getStringExtra("DeviceAddress");

                if (myDeviceAddress != null) {
                    mDeviceAddress = myDeviceAddress;
                }

                switch(intent.getAction()) {
                    case BGXpressService.BGX_INVALID_GATT_HANDLES: {


                        myActivity.finish();
                    }
                    break;
                    case BGX_CONNECTION_ERROR: {
                        int status = intent.getIntExtra("status", -1);
                        switch (status) {
                            case 137: ///< GATT_AUTH_FAIL
                                mStatusLabel.setText(R.string.BOND_FAIL_LABEL);
                                break;
                            case 133:
                                // try again.
                                BGXpressService.startActionBGXConnect(context, mDeviceAddress);
                                break;
                            case -1:
                                myActivity.finish();
                                Toast.makeText(context, R.string.DEVICE_CONNECTION_ERROR_LABEL, Toast.LENGTH_LONG).show();
                                break;
                            default:
                                mStatusLabel.setText("Error: " + status);
                                break;
                        }
                    }
                        break;
                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        BGX_CONNECTION_STATUS stateValue = (BGX_CONNECTION_STATUS) intent.getSerializableExtra("bgx-connection-status");
                        Log.d("bgx_dbg", "BGX Connection State Change: " + stateValue);


                        Boolean fBonded = intent.getBooleanExtra("bonded", false);
                        if (fBonded) {
                            mBondImageView.setImageResource(R.drawable.lock_small);
                        } else {
                            mBondImageView.setImageResource(R.drawable.unlock_small);
                        }

                        if (BGX_CONNECTION_STATUS.CONNECTING == stateValue) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTING);
                        } else if (BGX_CONNECTION_STATUS.INTERROGATING == stateValue) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_INTERROGATING);
                        } else if (BGX_CONNECTION_STATUS.CONNECTED == stateValue) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTED);
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    myActivity.finish();
                                }
                            }, 500);

                        } else if (BGX_CONNECTION_STATUS.DISCONNECTED == stateValue) {
                            myActivity.finish();
                        }
                    }
                    break;
                }
            }
        };

        registerReceiver(mBroadcastReceiver, bgxpressServiceFilter);

    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        unregisterReceiver(mBroadcastReceiver);
    }

}
