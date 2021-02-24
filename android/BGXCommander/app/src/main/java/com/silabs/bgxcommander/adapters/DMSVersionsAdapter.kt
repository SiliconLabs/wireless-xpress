package com.silabs.bgxcommander.adapters

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.silabs.bgxcommander.R
import com.silabs.bgxcommander.other.SelectionChangedListener
import kotlinx.android.synthetic.main.adapter_dms_versions.view.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DMSVersionsAdapter(private val context: Context, private val listener: SelectionChangedListener, private val versions: JSONArray?) : RecyclerView.Adapter<DMSVersionsAdapter.DMSVersionsViewHolder>() {
    private var selectedItemPos = -1
    private var lastSelectedItemPos = -1

    inner class DMSVersionsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val tvVersionNumber = itemView.tv_version_number as TextView
        private val tvVersionDescription = itemView.tv_version_description as TextView

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(deviceRecord: JSONObject?) {
            if (adapterPosition == selectedItemPos) {
                setSelected()
            } else {
                setDeselected()
            }

            try {
                tvVersionNumber.text = deviceRecord?.getString("version")
                tvVersionDescription.text = deviceRecord?.getString("description")
            } catch (e: JSONException) {
                Log.e("bgx_dbg", "JSONException caught in DMSVersionsAdapter onBindViewHolder")
                e.printStackTrace()
            }
        }

        private fun setSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        private fun setDeselected() {
            itemView.setBackgroundColor(0xFAFAFA)
        }

        override fun onClick(v: View) {
            selectedItemPos = adapterPosition
            notifyItemChanged(selectedItemPos, Unit)
            if (lastSelectedItemPos != -1) notifyItemChanged(lastSelectedItemPos, Unit)
            lastSelectedItemPos = selectedItemPos

            try {
                listener.selectionDidChange(adapterPosition, versions?.getJSONObject(adapterPosition))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DMSVersionsViewHolder {
        return DMSVersionsViewHolder(LayoutInflater.from(context).inflate(R.layout.adapter_dms_versions, parent, false))
    }

    override fun getItemCount(): Int {
        return versions?.length() ?: 0
    }

    override fun onBindViewHolder(holder: DMSVersionsViewHolder, position: Int) {
        holder.bind(versions?.getJSONObject(position))
    }

}