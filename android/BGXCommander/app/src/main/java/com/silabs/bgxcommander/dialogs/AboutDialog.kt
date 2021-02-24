package com.silabs.bgxcommander.dialogs

import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.DialogFragment
import com.silabs.bgxcommander.R
import kotlinx.android.synthetic.main.dialog_about.*

class AboutDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tv_version_info.text = getString(R.string.label_version_name, packageInfo.versionName, PackageInfoCompat.getLongVersionCode(packageInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        btn_ok.setOnClickListener {
            dismiss()
        }
    }

}
