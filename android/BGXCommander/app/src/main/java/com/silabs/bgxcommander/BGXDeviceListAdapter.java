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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import android.os.Handler;

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

        holder.getTextView().setText(deviceName);
        holder.getUuidTextView().setText(deviceAddress);
        holder.getRssiValueTextView().setText(rssiValueStr);

    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView myTextView;
        TextView uuidTextView;
        TextView rssiValueTextView;


        ViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            myTextView = itemView.findViewById(R.id.DeviceNameTextView);
            uuidTextView = itemView.findViewById(R.id.DeviceUUIDTextView);
            rssiValueTextView = itemView.findViewById(R.id.rssiValueTextView);

        }

        @Override
        public void onClick(View v) {

            if (null != mSelectedRowView) {
                mSelectedRowView.setBackgroundColor(Color.WHITE);
                mSelectedRowView = null;

            }

            v.setBackgroundColor(Color.LTGRAY);
            mSelectedRowView = v;

            int position = getAdapterPosition();

            HashMap<String, String> deviceRecord = mDataset.get(position);

            String deviceAddress = deviceRecord.get("uuid");

            BGXpressService.clearCancelFlag();

            BGXpressService.startActionBGXConnect(context, deviceAddress);

            Intent intent2 = new Intent(context, IndeterminateProgressActivity.class);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

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


    }

}