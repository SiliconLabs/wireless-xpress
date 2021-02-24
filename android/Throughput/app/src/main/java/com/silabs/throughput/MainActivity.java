package com.silabs.throughput;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silabs.bgxpress.BGX_CONNECTION_STATUS;
import com.silabs.bgxpress.BGXpressService;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.os.SystemClock.elapsedRealtime;


public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
    private int REQUEST_ENABLE_BT = 1;

    BroadcastReceiver mDeviceDiscoveryReceiver;
    private boolean fLocationPermissionGranted;
    private Handler mHandler;
    private Handler mSendHandler;
    private HandlerThread mHandlerThread;
    private ArrayList<Map<String, String>> mScanResults;
    private Context mContext;
    private Button connectButton;
    private Button disconnectButton;
    private Spinner bgx_devices_spinner;

    private Switch mLoopback;
    private Switch mAckWrites;
    private Switch m2MPhy;
    private TextView bytesRx;
    private TextView bps;
    private Button clearButton;
    private Button txDataButton;

    private boolean fLoopback;
    private boolean f2MPhyValue;

    private long mStartTime;
    private long mLastByteRxTime;
    private long mBytesRx;

    private String mDeviceAddress;

    private int realMTUSize;
    private EditText mMtuSize;
    private Button mSetMtuBtn;

    private EditText mTxDataSize;
    private int txDataSize;



    private Button captureButton;
    private TextView fileInfo;

    private Switch logBoundariesSwitch;
    private boolean logBoundaries;
    private OutputStreamWriter mOutputStreamWriter;

    private boolean fConnected;

    private ProgressBar mTransmitProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fConnected = false;
        logBoundaries = false;
        mContext = this;
        mStartTime = 0;
        mBytesRx = 0;
        realMTUSize = -1;
        mOutputStreamWriter = null;

        mTransmitProgress = (ProgressBar) findViewById(R.id.transmit_progress_bar);
        mTransmitProgress.setVisibility(View.INVISIBLE);

        mHandlerThread = new HandlerThread("BGXThroughput");
        mHandlerThread.start();
        mSendHandler = new Handler(mHandlerThread.getLooper());

        mHandler = new Handler();

        if (null == mScanResults) {
            mScanResults = new ArrayList<Map<String, String>>();
        }

        bgx_devices_spinner = (Spinner) findViewById(R.id.bgx_devices);
        mLoopback = (Switch) findViewById(R.id.loopback_switch);
        mAckWrites = (Switch) findViewById(R.id.ackwrites_switch);
        bytesRx = (TextView) findViewById(R.id.bytesRxTV);
        bps = (TextView) findViewById(R.id.bpsTV);
        clearButton = (Button) findViewById(R.id.clear_button);

        mMtuSize = (EditText) findViewById(R.id.mtu_size);
        mSetMtuBtn = (Button) findViewById(R.id.set_mtu);
        txDataButton = (Button) findViewById(R.id.transmitDataBtn);
        m2MPhy = (Switch) findViewById(R.id.TMP_switch);

        mTxDataSize = (EditText) findViewById(R.id.dataTxSize);



        captureButton = (Button) findViewById(R.id.CaptureButton);
        fileInfo = (TextView) findViewById(R.id.fileInfo);
        logBoundariesSwitch = (Switch) findViewById(R.id.logBoundariesSwitch);

        mTxDataSize.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {

                try {
                    txDataSize = Integer.parseInt(mTxDataSize.getText().toString());
                } catch (Exception e) {
                    txDataSize = 0;
                }
            }
        });


        m2MPhy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                f2MPhyValue = isChecked;
            }
        });


        logBoundariesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                logBoundaries = isChecked;
            }
        });

        fLoopback = mLoopback.isChecked();

        mLoopback.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                fLoopback = isChecked;

                /*
                Per MCUSW-496 leave this enabled all the time.
                if (isChecked) {
                    mAckWrites.setEnabled(true);
                } else {
                    mAckWrites.setEnabled(false);
                }
                */

            }
        });

        mAckWrites.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                BGXpressService.setBGXAcknowledgedWrites( mDeviceAddress, isChecked );

                BGXpressService.setBGXAcknowledgedReads( mDeviceAddress, isChecked);

            }
        });

        bgx_devices_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {



                boolean ackWrites = false;

                mAckWrites.setChecked(ackWrites);

                mLoopback.setChecked(false);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mSetMtuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int imtu = Integer.parseInt(mMtuSize.getText().toString());



                Intent intent = new Intent("com.silabs.bgx.request_mtu");
                intent.putExtra("mtu", imtu);
                intent.putExtra("DeviceAddress", mDeviceAddress);
                intent.setClass(mContext, BGXpressService.class);
                startService(intent);

            }
        });

        mDeviceDiscoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch(intent.getAction()) {
                    case BGXpressService.BGX_SCAN_MODE_CHANGE: {
                        boolean fScanning = intent.getBooleanExtra("isscanning", false);

                        if (fScanning) {
                            Log.d("bgx_throughput", "BGX_SCAN_MODE_CHANGE - it is scanning...");
                        } else {
                            Log.d("bgx_throughput", "BGX_SCAN_MODE_CHANGE - not scanning...");

                        }

                    }
                    break;
                    case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                        Log.d("bgx_throughput", "BGX_CONNECTION_STATUS_CHANGE");
                        BGX_CONNECTION_STATUS connectionStatusValue = (BGX_CONNECTION_STATUS)intent.getSerializableExtra("bgx-connection-status");

                        if (BGX_CONNECTION_STATUS.INTERROGATING == connectionStatusValue) {


                        } else if (BGX_CONNECTION_STATUS.CONNECTED == connectionStatusValue) {

                            fConnected = true;
                            connectButton.setEnabled(false);
                            disconnectButton.setEnabled(true);

                            mMtuSize.setEnabled(true);
                            txDataButton.setEnabled(true);
                            m2MPhy.setEnabled(false);

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {


                                    int writeType;
                                    int txReadType;

                                    if ( mAckWrites.isChecked() ) {
                                        writeType = WRITE_TYPE_DEFAULT;
                                        txReadType = 1;
                                    } else {
                                        writeType = WRITE_TYPE_NO_RESPONSE;
                                        txReadType = 0;
                                    }


                                    BGXpressService.setBGXAcknowledgedReads( mDeviceAddress, mAckWrites.isChecked() );

                                    BGXpressService.setBGXAcknowledgedWrites(mDeviceAddress, mAckWrites.isChecked());

                                }
                            });


                        } else if (BGX_CONNECTION_STATUS.DISCONNECTED == connectionStatusValue) {
                            fConnected = false;
                            connectButton.setEnabled(true);
                            disconnectButton.setEnabled(false);

                            mMtuSize.setEnabled(false);
                            txDataButton.setEnabled(false);
                            mDeviceAddress = null;
                            m2MPhy.setEnabled(true);
                        }

                    }
                    break;
                    case BGXpressService.BGX_SCAN_DEVICE_DISCOVERED: {
                        @SuppressWarnings("unchecked")
                        HashMap<String, String> deviceRecord = (HashMap<String, String>) intent.getSerializableExtra("DeviceRecord");
                        String deviceName = deviceRecord.get("name");
                        Log.d("bgx_throughput", "Received a device record: "+ deviceName + " "+deviceRecord.get("uuid"));


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

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String [] bgxDevices = new String[mScanResults.size()];
                                    for (int i = 0; i < mScanResults.size(); ++i) {
                                        HashMap<String, String> iDeviceRecord = (HashMap<String, String>) mScanResults.get(i);
                                        bgxDevices[i] = iDeviceRecord.get("name");
                                    }

                                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, bgxDevices);
                                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                    bgx_devices_spinner.setAdapter(adapter);
                                }
                            });
                        }

                    }
                    break;

                    case BGXpressService.BGX_DATA_RECEIVED: {
                        String stringReceived = intent.getStringExtra("data");

                        try {
                            if (null != mOutputStreamWriter) {
                                if (logBoundaries) {
                                    mOutputStreamWriter.write('|');
                                }
                                mOutputStreamWriter.write(stringReceived);
                            }
                        } catch (IOException exception) {
                            Log.e("bgx_dbg", "IOException caught");
                        }

                        Log.d("bgx_throughput", "Rx: "+stringReceived);




                        if (0 == mStartTime) {
                            mStartTime = SystemClock.elapsedRealtime();
                        }
                        mLastByteRxTime = SystemClock.elapsedRealtime();

                        mBytesRx += stringReceived.length();


                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                redrawStats();
                            }
                        });


                        if (fLoopback) {
                            Log.d("bgx_throughput", "Sending data loopback: "+ stringReceived);


                            Intent writeIntent = new Intent(BGXpressService.ACTION_WRITE_SERIAL_DATA);
                            writeIntent.putExtra("value", stringReceived);
                            writeIntent.setClass(mContext, BGXpressService.class);
                            writeIntent.putExtra("DeviceAddress", mDeviceAddress);
                            startService(writeIntent);


                        }

                    }
                    break;
                    case BGXpressService.BGX_MTU_CHANGE: {
                        int status = intent.getIntExtra("status", -1);
                        int mtu = intent.getIntExtra("mtu", -1);
                        String deviceAddress = intent.getStringExtra("deviceAddress");
                        if (realMTUSize != mtu) {

                            realMTUSize = mtu;
                            Log.d("bgx_throughput", "BGX_MTU_CHANGE received. Device Address: " + deviceAddress + " status: " + status + " mtu: " + mtu);

                            String msg = "MTU Changed to " + mtu + " Status: " + status;

                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();

                            String newValue = "" + realMTUSize;

                            if (!mMtuSize.getText().toString().contentEquals(newValue)) {
                                mMtuSize.setText(newValue);
                            }
                        }

                    }
                    break;
                }
            }
        };


        IntentFilter listIntentFilter = new IntentFilter(BGXpressService.BGX_SCAN_DEVICE_DISCOVERED);
        listIntentFilter.addAction(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);
        listIntentFilter.addAction(BGXpressService.BGX_SCAN_MODE_CHANGE);
        listIntentFilter.addAction(BGXpressService.BGX_DATA_RECEIVED);
        listIntentFilter.addAction(BGXpressService.BGX_MTU_CHANGE);



        registerReceiver(mDeviceDiscoveryReceiver, listIntentFilter);

        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);

        startService(new Intent(this, BGXpressService.class));

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);


        connectButton = (Button)findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bgx_throughput", "Connect button clicked");

                String bgx_name = (String) bgx_devices_spinner.getSelectedItem();

                for (int i = 0; i < mScanResults.size(); ++i) {
                    HashMap<String, String> iDeviceRecord = (HashMap<String, String>) mScanResults.get(i);
                    String iDeviceName = (String) iDeviceRecord.get("name");
                    if (iDeviceName.equals(bgx_name)) {
                        String deviceAddress = (String) iDeviceRecord.get("uuid");
                        mDeviceAddress = deviceAddress;
                        BGXpressService.startActionStopScan(mContext);
                        BGXpressService.startActionBGXConnect(mContext, deviceAddress);
                    }
                }




            }
        });

        disconnectButton = (Button)findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(new View.OnClickListener()  {
            @Override
            public void onClick(View v) {
                Log.d("bgx_throughput", "Disconnect button clicked");
                BGXpressService.startActionBGXDisconnect(mContext, mDeviceAddress);

            }
        });


        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBytesRx = 0;
                mStartTime = 0;
                redrawStats();



            }
        });






        final String kDataPattern = "0123456789\n";

        txDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // transmit some data.

                final int size2Send = Integer.parseInt(mTxDataSize.getText().toString());
                Log.d("bgx_dbg", "Writing "+size2Send+"bytes...");

                mTransmitProgress.setVisibility(View.VISIBLE);

                mSendHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            int writeOperations = 0;
                            int finalWriteSize = 0;

                            txDataSize = size2Send;
                            int writeSize = 64009; // we wanted a large number evenly divisible by the size of the pattern (i.e. 11)

                            while ( 0 != ( writeSize % kDataPattern.length())) {
                                ++writeSize;
                            }


                            byte sendArray[] = new byte[writeSize];
                            for (int i = 0; i < writeSize; ++i) {
                                sendArray[i] = (byte) kDataPattern.charAt(i % kDataPattern.length());
                            }

                            while (txDataSize > 0) {


                                Intent writeIntent = new Intent(BGXpressService.ACTION_WRITE_SERIAL_DATA);
                                if (writeSize < txDataSize) {
                                    writeIntent.putExtra("value", new String(sendArray));
                                    txDataSize -= writeSize;
                                } else {
                                    finalWriteSize = txDataSize;
                                    writeIntent.putExtra("value", new String(sendArray, 0, txDataSize));
                                    txDataSize -= txDataSize;
                                }
                                writeIntent.setClass(mContext, BGXpressService.class);
                                writeIntent.putExtra("DeviceAddress", mDeviceAddress);
                                startService(writeIntent);
                                ++writeOperations;

                            }

                            Log.d("bgx_dbg", ""+writeOperations+" write operations "+ (writeOperations-1)+ " at "+writeSize+" and one final write of "+finalWriteSize);
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mTransmitProgress.setVisibility(View.INVISIBLE);

                                    Toast.makeText(mContext, "Done sending "+size2Send+" bytes", Toast.LENGTH_LONG).show();
                                }
                            });

                        } catch (Exception e) {
                            Log.d("bgx_throughput", "Caught an exception.");
                        }
                    }
                }, 200);


            }
        });


        if (Build.VERSION.SDK_INT >= 26 && BluetoothAdapter.getDefaultAdapter().isLe2MPhySupported()) {
            m2MPhy.setVisibility(View.VISIBLE);
        } else {
            m2MPhy.setVisibility(View.INVISIBLE);
        }


        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (null == mOutputStreamWriter) {
                    try {

                        FileOutputStream fos = openFileOutput("com.silabs.throughput.capture.txt", MODE_PRIVATE);
                        mOutputStreamWriter = new OutputStreamWriter(fos);

                    } catch (IOException exception) {
                        Log.e("bgx_dbg", "IOException");
                    }

                   fileInfo.setText("Capturing...");
                } else {
                    // close the capture file
                    try {
                        mOutputStreamWriter.flush();
                        mOutputStreamWriter.close();

                        mOutputStreamWriter = null;

                    } catch (IOException exception) {
                        Log.e("bgx_dbg", "IOException");
                    }

                    fileInfo.setText("");
                }
            }
        });


    }


    protected void onDestroy() {
        super.onDestroy();

        mHandlerThread.quit();
        unregisterReceiver(mDeviceDiscoveryReceiver);

    }

    public void redrawStats()
    {
        long ms = 0;

        bytesRx.setText( ""+mBytesRx );

        if ( 0 != mStartTime ) {
            ms = mLastByteRxTime - mStartTime;
        }

        if (ms != 0) {
            //float s = (float)ms/1000.0f;
            float thebps = ( (mBytesRx * 8 )/ ms);
          bps.setText( "" +  thebps );
        } else {
            bps.setText("--");
        }

    }

    public String getSelectedDeviceAddress()
    {
        String bgx_name = (String) bgx_devices_spinner.getSelectedItem();

        for (int i = 0; i < mScanResults.size(); ++i) {
            HashMap<String, String> iDeviceRecord = (HashMap<String, String>) mScanResults.get(i);
            String iDeviceName = (String) iDeviceRecord.get("name");
            if (iDeviceName.equals(bgx_name)) {
                String deviceAddress = (String) iDeviceRecord.get("uuid");
                return deviceAddress;
            }
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, yay! Start the Bluetooth device scan.
                    Log.d("bgx_throughput", "Received permissions to use location.");
                    fLocationPermissionGranted = true;
//                    mLocationPermissionDeniedTextView.setVisibility(View.GONE);
//                    mDeviceListRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    // Alert the user that this application requires the location permission to perform the scan.
                    Log.e("bgx_throughput", "Did not get permissions to use location.");
                    fLocationPermissionGranted = false;

//                    mLocationPermissionDeniedTextView.setVisibility(View.VISIBLE);
//                    mDeviceListRecyclerView.setVisibility(View.GONE);
//                    mBluetoothDisabledWarningTextView.setVisibility(View.GONE);
                }
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!fConnected) {
            boolean fAdapterEnabled = false;
            try {
                fAdapterEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled();

                if (!fAdapterEnabled) {
//                mDeviceListRecyclerView.setVisibility(View.GONE);
//                mBluetoothDisabledWarningTextView.setVisibility(View.VISIBLE);
                } else {
//                mDeviceListRecyclerView.setVisibility(View.VISIBLE);
//                mBluetoothDisabledWarningTextView.setVisibility(View.GONE);
                }


            } catch (Exception e) {
                Log.d("bgx_dbg", "Exception caught while calling isEnabled.");
                Toast.makeText(this, "Exception caught", Toast.LENGTH_LONG).show();
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
        } else {
            Log.d("bgx_throughput","onResume called while connected.");
        }
    }

    private void scanForDevices() {
        final Context myContext = this;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("bgx_dbg", "starting scan");

                BGXpressService.startActionStartScan(myContext);
            }
        }, 0);
    }
}
