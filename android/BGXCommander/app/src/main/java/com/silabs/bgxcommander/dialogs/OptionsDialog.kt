package com.silabs.bgxcommander.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.silabs.bgxcommander.R
import kotlinx.android.synthetic.main.dialog_options.*

class OptionsDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireContext().getSharedPreferences("com.silabs.bgxcommander", Context.MODE_PRIVATE)
        val isNewLineChecked = sharedPrefs.getBoolean("newlinesOnSend", true)
        val isOtaAckdWriteChecked = sharedPrefs.getBoolean("useAckdWritesForOTA", true)

        cb_newline.isChecked = isNewLineChecked
        cb_acknowledged_ota.isChecked = isOtaAckdWriteChecked

        btn_save.setOnClickListener {
            sharedPrefs.edit().apply {
                putBoolean("newlinesOnSend", cb_newline.isChecked)
                putBoolean("useAckdWritesForOTA", cb_acknowledged_ota.isChecked)
            }.apply()
            dismiss()
        }

        btn_cancel.setOnClickListener {
            dismiss()
        }
    }

}