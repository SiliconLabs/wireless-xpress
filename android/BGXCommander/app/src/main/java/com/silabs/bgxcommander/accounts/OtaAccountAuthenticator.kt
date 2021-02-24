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
package com.silabs.bgxcommander.accounts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.NetworkErrorException
import android.content.Context
import android.os.Bundle

class OtaAccountAuthenticator : AbstractAccountAuthenticator {
    constructor() : super(null) {}
    constructor(context: Context) : super(context) {}

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(arg0: AccountAuthenticatorResponse, arg1: Account,
                             arg2: Array<String>): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(arg0: AccountAuthenticatorResponse,
                                   arg1: Account, arg2: String, arg3: Bundle): Bundle? {
        return null
    }

    override fun getAuthTokenLabel(arg0: String): String? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String,
            options: Bundle): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(arg0: AccountAuthenticatorResponse,
                                    arg1: Account, arg2: Bundle): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun addAccount(
            response: AccountAuthenticatorResponse,
            accountType: String,
            authTokenType: String,
            requiredFeatures: Array<String>,
            options: Bundle): Bundle? {
        return null
    }

    override fun editProperties(arg0: AccountAuthenticatorResponse, arg1: String): Bundle? {
        return null
    }
}