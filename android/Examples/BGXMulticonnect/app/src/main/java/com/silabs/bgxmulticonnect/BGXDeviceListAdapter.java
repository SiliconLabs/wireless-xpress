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

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.silabs.bgxpress.BGX_CONNECTION_STATUS;
import com.silabs.bgxpress.BGXpressService;

import java.util.ArrayList;
import java.util.HashMap;

public class BGXDeviceListAdapter extends RecyclerView.Adapter<BGXDeviceListAdapter.ViewHolder> {

    private Context context;
    private ArrayList<HashMap<String, String>> mDataset;
    private LayoutInflater mInflater;

    private View mSelectedRowView = null;
    private Handler mHandler = new Handler();



    BGXDeviceListAdapter(Context context, ArrayList dataset ) {
        this.context = context;
        this.mInflater = LayoutInflater.from(context);
        this.mDataset = dataset;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.device_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final int rowPosition = position;
        final HashMap<String, String> deviceRecord = mDataset.get(position);
        String deviceName = deviceRecord.get("name");
        final String deviceAddress = deviceRecord.get("uuid");
        String rssiValueStr = deviceRecord.get("rssi");

        Button connectButton = holder.getConnectButton();
        Button detailsButton = holder.getDetailsButton();

        holder.getTextView().setText(deviceName);
        holder.getUuidTextView().setText(deviceAddress);
        holder.getRssiValueTextView().setText(rssiValueStr);

        BGX_CONNECTION_STATUS deviceConnectionStatus = BGXpressService.getBGXDeviceConnectionStatus(deviceAddress);
        switch (deviceConnectionStatus) {
            case CONNECTING:
            case INTERROGATING:
            case DISCONNECTING:
                connectButton.setEnabled(false);
                detailsButton.setEnabled(false);
                holder.fConnected = false;


                break;
            case DISCONNECTED:
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
                detailsButton.setEnabled(false);
                holder.fConnected = false;
                break;
            case CONNECTED:
                connectButton.setText("Disconnect");
                connectButton.setEnabled(true);
                detailsButton.setEnabled(true);
                holder.fConnected = true;
                break;
        }
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView myTextView;
        TextView uuidTextView;
        TextView rssiValueTextView;
        Boolean fConnected;

        Button detailsButton;
        Button connectButton;

        BluetoothDevice btDevice = null;

        BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String intentDeviceAddress = intent.getStringExtra("DeviceAddress");

                int position = getAdapterPosition();

                HashMap<String, String> deviceRecord = mDataset.get(position);
                String deviceAddress = deviceRecord.get("uuid");

                if (intentDeviceAddress.equalsIgnoreCase(deviceAddress)) {
                    switch (intent.getAction()) {
                        case BGXpressService.BGX_CONNECTION_STATUS_CHANGE: {
                            Log.d("bgx_dbg", "*** OnReceive a BGX_CONNECTION_STATUS_CHANGE Intent ***");
                            BGX_CONNECTION_STATUS connectionStatusValue = (BGX_CONNECTION_STATUS) intent.getSerializableExtra("bgx-connection-status");


                            if (BGX_CONNECTION_STATUS.CONNECTED == connectionStatusValue) {
                                btDevice = (BluetoothDevice) intent.getParcelableExtra("device");
                                connectButton.setText("Disconnect");
                                fConnected = true;
                                detailsButton.setEnabled(true);
                            } else {
                                detailsButton.setEnabled(false);
                                btDevice = null;
                                connectButton.setText("Connect");
                                fConnected = false;
                            }
                        }
                    }
                }
            }
        };


        ViewHolder(View itemView) {
            super(itemView);
            fConnected = false;
            itemView.setOnClickListener(this);
            myTextView = itemView.findViewById(R.id.DeviceNameTextView);
            uuidTextView = itemView.findViewById(R.id.DeviceUUIDTextView);
            rssiValueTextView = itemView.findViewById(R.id.rssiValueTextView);

            detailsButton = itemView.findViewById(R.id.DetailsButton);
            connectButton = itemView.findViewById(R.id.ConnectButton);

            connectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    int position = getAdapterPosition();
                    Log.d("bgx_dbg", "Clicked Connect at position "+position);

                    HashMap<String, String> deviceRecord = mDataset.get(position);
                    String deviceAddress = deviceRecord.get("uuid");
                    String deviceName = deviceRecord.get("name");

                    if (fConnected) {

                        Log.d("bgx_dbg", "Calling Disconnect for device "+deviceName+" ("+deviceAddress+")");
                        BGXpressService.startActionBGXDisconnect(context, deviceAddress);
                    } else {

                        Log.d("bgx_dbg", "Calling Connect for device "+deviceName+" ("+deviceAddress+")");
                        BGXpressService.startActionBGXConnect(context, deviceAddress);
                    }

                }
            });

            detailsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    Log.d("bgx_dbg", "Details at position "+position+"!");

                    HashMap<String, String> deviceRecord = mDataset.get(position);

                    String deviceAddress = deviceRecord.get("uuid");
                    String deviceName = deviceRecord.get("name");

                    Intent intent2 = new Intent(context, DeviceDetails.class);
//                    intent2.putExtra("BLUETOOTH_DEVICE", btDevice);

                    intent2.putExtra("DeviceAddress", deviceAddress);
                    intent2.putExtra("DeviceName", deviceName);

                    intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent2);
                }
            });

            IntentFilter myFilter = new IntentFilter(BGXpressService.BGX_CONNECTION_STATUS_CHANGE);

            context.registerReceiver(myBroadcastReceiver, myFilter);

        }


        @Override
        public void onClick(View v) {

/*
            int position = getAdapterPosition();



            if (null != mSelectedRowView) {
                mSelectedRowView.setBackgroundColor(Color.WHITE);
                mSelectedRowView = null;

            }

            v.setBackgroundColor(Color.LTGRAY);
            mSelectedRowView = v;


            Log.d("bgx_dbg", "Adapter Position: " + position);

            HashMap<String, String> deviceRecord = mDataset.get(position);
            Log.d("bgx_dbg", "Selected Device: " + deviceRecord.get("name"));


            String deviceAddress = deviceRecord.get("uuid");



            BGXpressService.startActionBGXConnect(context, deviceAddress);

            Intent intent2 = new Intent(context, IndeterminateProgressActivity.class);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent2.putExtra("DeviceAddress", deviceAddress);

            context.startActivity(intent2);

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (null != mSelectedRowView) {
                        mSelectedRowView.setBackgroundColor(Color.WHITE);
                        mSelectedRowView = null;
                    }
                }
            }, 500);
*/
        }

        public TextView getTextView() {
            return myTextView;
        }

        public TextView getUuidTextView() {
            return uuidTextView;
        }

        public TextView getRssiValueTextView() {
            return rssiValueTextView;
        }

        public Button getConnectButton() {
            return connectButton;
        }

        public Button getDetailsButton() {
            return detailsButton;
        }

    }

}
