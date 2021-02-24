package com.silabs.bgxcommander.accounts

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OtaAuthenticationService : Service() {
    private var mAuthenticator: OtaAccountAuthenticator? = null
    private val lock = Any()

    override fun onCreate() {
        super.onCreate()

        synchronized(lock) {
            if (mAuthenticator == null) {
                mAuthenticator = OtaAccountAuthenticator(this)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mAuthenticator?.iBinder
    }
}