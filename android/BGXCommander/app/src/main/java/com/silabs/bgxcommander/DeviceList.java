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

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.silabs.bgxcommander.BGX_CONNECTION_STATUS.CONNECTED;

public class DeviceList extends AppCompatActivity {



    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;

    private static final long SCAN_PERIOD = 10000;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
    private RecyclerView mDeviceListRecyclerView;
    private RecyclerView.Adapter mDeviceListAdapter;
    private RecyclerView.LayoutManager mDeviceListLayoutManager;
    private ArrayList<Map<String, String> > mScanResults;
    private TextView mBluetoothDisabledWarningTextView;
    private TextView mLocationPermissionDeniedTextView;

    private boolean fScanning = false;
    private boolean fLocationPermissionGranted = false;
    private BroadcastReceiver mDeviceDiscoveryReceiver;

    private MenuItem mScanItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        mDeviceDiscoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                switch(intent.getAction()) {
                    case BGXpressService.BGX_SCAN_DEVICE_DISCOVERED: {
                        HashMap<String, String> deviceRecord = (HashMap<String, String>) intent.getSerializableExtra("DeviceRecord");

                        // must now check if the scan resuls already contain this device because we are no longer clearing the scan results when scan starts
                        // because in multi-connect scenario you wouldn't rediscover devices you are already connected to.

                        String devAddr = deviceRecord.get("uuid");
                        Boolean fContainsRecord = false;
                        for (int i = 0; i < mScanResults.size(); ++i) {
                            HashMap<String, String> iDeviceRecord = (HashMap<String, String>) mScanResults.get(i);
                            String iDeviceAddr = iDeviceRecord.get("uuid");
                            if (devAddr.equalsIgnoreCase(iDeviceAddr)) {
                                fContainsRecord = true;
                                break;
                            }

                        }

                        if (!fContainsRecord) {
                            mScanResults.add(deviceRecord);

                            Collections.sort(mScanResults, new Comparator<Map<String, String>>() {
                                @Override
                                public int compare(Map<String, String> leftRecord, Map<String, String> rightRecord) {
                                    String leftRssi = leftRecord.get("rssi");
                                    String rightRssi = rightRecord.get("rssi");
                                    return leftRssi.compareTo(rightRssi);

                                }
                            });

                            mDeviceListAdapter = new BGXDeviceListAdapter(getApplicationContext(), mScanResults);
                            mDeviceListRecyclerView.swapAdapter(mDeviceListAdapter, true);
                        }
                    }
                        break;

                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        BGX_CONNECTION_STATUS connectionStatusValue = (BGX_CONNECTION_STATUS)intent.getSerializableExtra("bgx-connection-status");


                        if ( BGX_CONNECTION_STATUS.CONNECTED == connectionStatusValue) {
                            BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra("device");
                            Intent intent2 = new Intent(context, DeviceDetails.class);
                            intent2.putExtra("BLUETOOTH_DEVICE", btDevice);
                            intent2.putExtra("DeviceName", btDevice.getName());
                            intent2.putExtra("DeviceAddress", btDevice.getAddress());
                            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent2);
                        }
                    }
                        break;
                    case BGXpressService.BGX_SCAN_MODE_CHANGE: {
                        fScanning = intent.getBooleanExtra("isscanning", false);
                        mScanItem.setEnabled(!fScanning);

                    }
                    break;

                }

            }
        };

        IntentFilter listIntentFilter = new IntentFilter(BGXpressService.BGX_SCAN_DEVICE_DISCOVERED);
        listIntentFilter.addAction(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);
        listIntentFilter.addAction(BGXpressService.BGX_SCAN_MODE_CHANGE);



        registerReceiver(mDeviceDiscoveryReceiver, listIntentFilter);


        mDeviceListRecyclerView = (RecyclerView) findViewById(R.id.DeviceListRecyclerView);
        mDeviceListRecyclerView.setHasFixedSize(true);

        mDeviceListLayoutManager = new LinearLayoutManager(this);
        mDeviceListRecyclerView.setLayoutManager(mDeviceListLayoutManager);

        if (null == mScanResults) {
            mScanResults = new ArrayList<Map<String, String>>();
        }

        mDeviceListAdapter = new BGXDeviceListAdapter(this, mScanResults);
        mDeviceListRecyclerView.setAdapter(mDeviceListAdapter);

        mBluetoothDisabledWarningTextView = (TextView)findViewById(R.id.BluetoothDisabledWarning);
        mLocationPermissionDeniedTextView = (TextView)findViewById(R.id.LocationPermissionDenied);

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("bgx_dbg", "BLE Not Supported.");
            finish();
        }

        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);

        startService(new Intent(this, BGXpressService.class));

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mDeviceDiscoveryReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
       getMenuInflater().inflate(R.menu.devicelist, menu);

        mScanItem = menu.findItem(R.id.scan_menuitem);

       return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch(mi.getItemId()) {
            case R.id.scan_menuitem:
                Log.d("bgx_dbg", "Scan now");

                // clear the scan list

                if (!fLocationPermissionGranted) {
                    Toast.makeText(this, "No location permission", Toast.LENGTH_LONG).show();
                    return true;
                }

                if (!fScanning) {
                    scanForDevices();
                }
                return true;
            case R.id.about_menuitem:
                try {
                    PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);

                    final Dialog aboutDialog = new Dialog( this );
                    aboutDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    aboutDialog.setContentView(R.layout.aboutbox);

                    TextView versionText = (TextView)aboutDialog.findViewById(R.id.version_info);

                    versionText.setText(getString(R.string.VersionNameLabel, pInfo.versionName));

                    Button okayButton = (Button)aboutDialog.findViewById(R.id.btn_ok);
                    okayButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            aboutDialog.dismiss();
                        }
                    });

                    aboutDialog.show();


                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.help_item: {

                String sHelpURL = getString(R.string.help_url);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sHelpURL));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);

                return true;
            }
        }
        return super.onOptionsItemSelected(mi);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, yay! Start the Bluetooth device scan.
                    Log.d("bgx_dbg", "Received permissions to use location.");
                    fLocationPermissionGranted = true;
                    mLocationPermissionDeniedTextView.setVisibility(View.GONE);
                    mDeviceListRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    // Alert the user that this application requires the location permission to perform the scan.
                    Log.e("bgx_dbg", "Did not get permissions to use location.");
                    fLocationPermissionGranted = false;

                    mLocationPermissionDeniedTextView.setVisibility(View.VISIBLE);
                    mDeviceListRecyclerView.setVisibility(View.GONE);
                    mBluetoothDisabledWarningTextView.setVisibility(View.GONE);
                }
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        boolean fAdapterEnabled = false;
        try {
            fAdapterEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled();

            if (!fAdapterEnabled) {
                mDeviceListRecyclerView.setVisibility(View.GONE);
                mBluetoothDisabledWarningTextView.setVisibility(View.VISIBLE);
            } else {
                mDeviceListRecyclerView.setVisibility(View.VISIBLE);
                mBluetoothDisabledWarningTextView.setVisibility(View.GONE);
            }


        } catch(Exception e) {
            Log.d("bgx_dbg", "Exception caught while calling isEnabled.");
            Toast.makeText(this,"Exception caught", Toast.LENGTH_LONG).show();
        }

        if (fAdapterEnabled && fLocationPermissionGranted) {

            if (null == BluetoothAdapter.getDefaultAdapter() || !fAdapterEnabled) {
                Log.d("bgx_dbg", "bluetooth adapter is not available.");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                scanForDevices();

            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                Log.d("bgx_dbg","Result canceled.");
                finish();
                return;
            }
            Log.d("bgx_dbg", "request enabled");
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanForDevices() {
        final Context myContext = this;

//        mScanResults.clear();

        mDeviceListAdapter = new BGXDeviceListAdapter(getApplicationContext(), mScanResults);
        mDeviceListRecyclerView.swapAdapter(mDeviceListAdapter, true);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("bgx_dbg", "starting scan");

                BGXpressService.startActionStartScan(myContext);
            }
        }, 0);

        /*
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("bgx_dbg", "stopping scan");
                BGXpressService.startActionStopScan(myContext);

            }
        }, SCAN_PERIOD);
        */
    }



}


