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

package com.silabs.bgxmulticonnect;

import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static com.silabs.bgxpress.BGXpressService.BGX_CONNECTION_ERROR;
import com.silabs.bgxpress.BGXpressService;
import com.silabs.bgxpress.BGX_CONNECTION_STATUS;

public class IndeterminateProgressActivity extends AppCompatActivity {

    BroadcastReceiver mBroadcastReceiver;

    Button mCancelButton;
    TextView mStatusLabel;
    Handler mHandler;
    String mDeviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indeterminate_progress);

        final IndeterminateProgressActivity myActivity = this;

        mCancelButton = (Button)findViewById(R.id.cancelButton);

        mStatusLabel = (TextView)findViewById(R.id.connectionStatusTextView);
        mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTING);
        
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

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String myDeviceAddress = intent.getStringExtra("DeviceAddress");

                if (myDeviceAddress != null) {
                    mDeviceAddress = myDeviceAddress;
                }

                switch(intent.getAction()) {
                    case BGX_CONNECTION_ERROR:
                        switch  ( intent.getIntExtra("Status", -1) ) {
                            case 137: ///< GATT_AUTH_FAIL
                                mStatusLabel.setText(R.string.BOND_FAIL_LABEL);
                                break;
                            default:
                                mStatusLabel.setText(R.string.DEVICE_CONNECTION_ERROR_LABEL);
                                break;
                        }
                        break;
                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        BGX_CONNECTION_STATUS stateValue = (BGX_CONNECTION_STATUS) intent.getSerializableExtra("bgx-connection-status");
                        Log.d("bgx_dbg", "BGX Connection State Change: " + stateValue);
                        if ( BGX_CONNECTION_STATUS.CONNECTING == stateValue ) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTING);
                        } else if (BGX_CONNECTION_STATUS.INTERROGATING == stateValue) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_INTERROGATING);
                        } else if ( BGX_CONNECTION_STATUS.CONNECTED == stateValue) {
                            mStatusLabel.setText(R.string.BGX_CONNECTION_STATUS_LABEL_CONNECTED);
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    myActivity.finish();
                                }
                            }, 500);

                        } else if ( BGX_CONNECTION_STATUS.DISCONNECTED == stateValue) {
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
