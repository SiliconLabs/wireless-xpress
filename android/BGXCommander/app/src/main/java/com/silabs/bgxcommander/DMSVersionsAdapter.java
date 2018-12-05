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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;


public class DMSVersionsAdapter extends RecyclerView.Adapter<DMSVersionsAdapter.ViewHolder> {

    private Context context;
    private LayoutInflater mInflater;
    private JSONArray mDataset;

    private View mSelectedRowView = null;
    private SelectionChangedListener mSelectionChangedListener;



    DMSVersionsAdapter(Context context, SelectionChangedListener selectionChangedListener, JSONArray versions) {
        this.mSelectionChangedListener = selectionChangedListener;
        this.context = context;
        this.mInflater = LayoutInflater.from(context);
        mDataset = versions;

        if (null != mSelectedRowView) {
            mSelectedRowView.setBackgroundColor(0xFFFAFAFA);
            mSelectedRowView = null;
            mSelectionChangedListener.selectionDidChange(-1, null);
        }

    }

    @Override
    public DMSVersionsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.dms_versions_list_row, parent, false);
        return new DMSVersionsAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DMSVersionsAdapter.ViewHolder holder, int position) {

        try {
            JSONObject deviceRecord = mDataset.getJSONObject(position);

            String versionNumber = deviceRecord.getString("version");
            String versionTag = deviceRecord.getString("tag");
            int versionSize = deviceRecord.getInt("size");

            holder.getVersionNumberTextView().setText(versionNumber);
            holder.getVersionTagTextView().setText(versionTag);

            holder.getVersionBytesTextView().setText( String.format("%d bytes", versionSize) );


        } catch (JSONException e) {
            Log.e("bgx_dbg", "JSONException caught in DMSVersionsAdapter onBindViewHolder");
            e.printStackTrace();
        }

    }

    @Override
    public int getItemCount() {
        int count = 0;
        if (null != mDataset) {
            count = mDataset.length();
        }
        return count;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        TextView versionNumberTextView;
        TextView versionTagTextView;
        TextView versionBytesTextView;

        ViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            versionNumberTextView = itemView.findViewById(R.id.versionNumberTextView);
            versionTagTextView = itemView.findViewById(R.id.versionTagTextView);
            versionBytesTextView = itemView.findViewById(R.id.versionBytesTextView);
        }
        @Override
        public void onClick(View v) {


            if (null != mSelectedRowView) {
                mSelectedRowView.setBackgroundColor(0xFFFAFAFA);
                mSelectedRowView = null;
            }

            v.setBackgroundColor(Color.LTGRAY);
            mSelectedRowView = v;

            try {
                mSelectionChangedListener.selectionDidChange(getAdapterPosition(), mDataset.getJSONObject(getAdapterPosition()));
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        public TextView getVersionNumberTextView() {
            return versionNumberTextView;
        }

        public TextView getVersionTagTextView() {
            return versionTagTextView;
        }

        public TextView getVersionBytesTextView() {
            return versionBytesTextView;
        }
    }

}
