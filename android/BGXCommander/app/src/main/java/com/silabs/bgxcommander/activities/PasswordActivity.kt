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
package com.silabs.bgxcommander.activities

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.silabs.bgxcommander.enums.PasswordKind
import com.silabs.bgxcommander.R
import kotlinx.android.synthetic.main.activity_password.*

class PasswordActivity : AppCompatActivity() {
    private var deviceAddress: String? = null
    private var passwordKind: PasswordKind? = null
    private var deviceName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)
        Log.d("bgx_dbg", "Password Activity onCreate")

        passwordKind = intent.getSerializableExtra("PasswordKind") as PasswordKind
        deviceAddress = intent.getStringExtra("DeviceAddress")
        deviceName = intent.getStringExtra("DeviceName")

        btn_password_ok.setOnClickListener {
            val am = AccountManager.get(this)
            val myPassword = et_password.text.toString()
            savePassword(am, passwordKind, deviceAddress, myPassword)

            sendBroadcast(getPasswordUpdatedIntent())
            finish()
        }

        btn_password_cancel.setOnClickListener {
            if (passwordKind == PasswordKind.OTAPasswordKind) {
                setResult(FirmwareUpdateActivity.RESULT_CODE_CLOSE_FIRMWARE_UPDATE_VIEW, Intent())
            }
            finish()
        }

        when (passwordKind) {
            PasswordKind.UnknownPasswordKind -> {
            }
            PasswordKind.BusModePasswordKind -> {
                tv_password_instructions.text = "A password is required for remote console on $deviceName"
            }
            PasswordKind.OTAPasswordKind -> {
                tv_password_instructions.text = "A password is required to update the firmware on $deviceName"
            }
        }
    }

    override fun onBackPressed() {
        if (passwordKind == PasswordKind.OTAPasswordKind) {
            setResult(FirmwareUpdateActivity.RESULT_CODE_CLOSE_FIRMWARE_UPDATE_VIEW, Intent())
        }
        super.onBackPressed()
    }

    private fun getPasswordUpdatedIntent(): Intent {
        return Intent().apply {
            action = ACTION_PASSWORD_UPDATED
            putExtra("PasswordKind", passwordKind)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("bgx_dbg", "Password Activity onResume")

        val am = AccountManager.get(this)
        val myPassword = retrievePassword(am, passwordKind, deviceAddress)
        myPassword?.let {
            et_password.setText(it)
        }
    }

    companion object {
        /**
         * Broadcast intent sent when the password is updated.
         *
         * Extras:
         * PasswordKind - the kind of password updated.
         */
        const val ACTION_PASSWORD_UPDATED = "com.silabs.bgx.password.updated"

        fun retrievePassword(am: AccountManager, kind: PasswordKind?, deviceAddress: String?): String? {
            var thePassword: String? = null
            // pull the password out of the Account Manager.
            val myAccounts = am.getAccountsByType(passwordKindString(kind))

            for (i in myAccounts.indices) {
                val account = myAccounts[i]
                Log.d("bgx_dbg", "Account name: " + account.name + " type: " + account.type)

                if (deviceAddress == account.name) {
                    thePassword = am.getPassword(account)
                    break
                }
            }
            return thePassword
        }

        fun savePassword(am: AccountManager, kind: PasswordKind?, deviceAddress: String?, password: String?) {
            val myAccount = Account(deviceAddress, passwordKindString(kind))
            val added = am.addAccountExplicitly(myAccount, password, Bundle())

            if (added) {
                Log.d("bgx_dbg", "Account was added.")
            } else {
                Log.e("bgx_dbg", "Account add fail, setting password.")
                am.setPassword(myAccount, password)
            }
        }

        private fun passwordKindString(kind: PasswordKind?): String {
            return when (kind) {
                PasswordKind.BusModePasswordKind -> "com.silabs.BusModePasswordKind"
                PasswordKind.OTAPasswordKind -> "com.silabs.OTAPasswordKind"
                else -> "com.silabs.unknownPasswordKind"
            }
        }
    }
}