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

package com.silabs.bgxcommander;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


enum PasswordKind {
     unknownPasswordKind
    ,BusModePasswordKind
    ,OTAPasswordKind
}

public class Password extends AppCompatActivity {

    /**
     * Broadcast intent sent when the password is updated.
     *
     * Extras:
     * PasswordKind - the kind of password updated.
     */
    public static final String ACTION_PASSWORD_UPDATED = "com.silabs.bgx.password.updated";

    private Context mContext;
    private Button okayButton;
    private Button cancelButton;
    private TextView instructions;
    private EditText passwordField;

    private PasswordKind mKind;
    private String mDeviceAddress;
    private String mDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password);

        final Password passwordActivity = this;

        mContext = this;

        okayButton = (Button) findViewById(R.id.btn_password_ok);
        cancelButton = (Button) findViewById(R.id.btn_password_cancel);
        instructions = (TextView) findViewById(R.id.password_instructions);
        passwordField = (EditText) findViewById(R.id.PasswordEditText);
        Log.d("bgx_dbg", "Password Activity onCreate");

        mKind = (PasswordKind) getIntent().getSerializableExtra("PasswordKind");
        mDeviceAddress = getIntent().getStringExtra("DeviceAddress");
        mDeviceName = getIntent().getStringExtra("DeviceName");



        okayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountManager am = AccountManager.get(mContext);

                String myPassword = passwordField.getText().toString();

                SavePassword(am, mKind, mDeviceAddress, myPassword);

                // send a broadcast intent so that interested activities can know.
                Intent intent = new Intent();
                intent.setAction(ACTION_PASSWORD_UPDATED);
                intent.putExtra("PasswordKind", mKind);
                sendBroadcast(intent);

                passwordActivity.finish();
            }
        });


        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passwordActivity.finish();
            }
        });


        switch (mKind) {
            case unknownPasswordKind:
                break;
            case BusModePasswordKind:
                instructions.setText( "A password is required for remote console on " + mDeviceName );
                break;
            case OTAPasswordKind:
                instructions.setText( "A password is required to update the firmware on " + mDeviceName );
                break;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("bgx_dbg", "Password Activity onResume");

        AccountManager am = AccountManager.get(this);

        String myPassword = RetrievePassword(am, mKind, mDeviceAddress);

        if (null != myPassword) {
            passwordField.setText(myPassword);
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    static String RetrievePassword(AccountManager am, PasswordKind kind, String deviceAddress)
    {
        String thePassword = null;
        // pull the password out of the Account Manager.
        Account[] myAccounts = am.getAccountsByType(passwordKindString(kind));

        for (int i =0; i < myAccounts.length; ++i) {
            Account iAccount = myAccounts[i];
            Log.d("bgx_dbg", "Account name: "+iAccount.name +" type: "+iAccount.type);

            if ( deviceAddress.equals(iAccount.name)) {
                // found it maybe?
                thePassword = am.getPassword(iAccount);
                break;
            }

        }

        return thePassword;
    }

    static void SavePassword(AccountManager am, PasswordKind kind, String deviceAddress, String password)
    {

        Account myAccount = new Account(deviceAddress, passwordKindString(kind) );


        boolean fAdded = am.addAccountExplicitly(myAccount, password, new Bundle() );

        if (fAdded) {
            Log.d("bgx_dbg", "Account was added.");
        } else {
            Log.e("bgx_dbg", "Account add fail, setting password.");
            am.setPassword(myAccount, password);
        }

    }

    static String passwordKindString(PasswordKind kind)
    {
        String skind;
        switch (kind) {
            case BusModePasswordKind:
                skind = "com.silabs.BusModePasswordKind";
                break;
            case OTAPasswordKind:
                skind = "com.silabs.OTAPasswordKind";
                break;
            default:
            case unknownPasswordKind:
                skind = "com.silabs.unknownPasswordKind";
                break;
        }

        return skind;
    }
}
