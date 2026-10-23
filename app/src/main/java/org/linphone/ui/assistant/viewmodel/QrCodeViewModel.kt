/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.json.JSONObject
import org.linphone.core.ConfiguringState
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.TransportType
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.R
import org.linphone.core.GlobalState
import org.linphone.utils.LinphoneUtils

class QrCodeViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Qr Code Scanner ViewModel]"
    }

    val remoteProvisioningSuccessfulEvent = MutableLiveData<Event<Boolean>>()

    val onErrorEvent = MutableLiveData<Event<Boolean>>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onConfiguringStatus(core: Core, status: ConfiguringState, message: String?) {
            Log.i("$TAG Configuring state is [$status]")
            if (status == ConfiguringState.Failed) {
                Log.e("$TAG Failure applying remote provisioning: $message")
                showRedToast(R.string.remote_provisioning_config_failed_toast, R.drawable.warning_circle)
                onErrorEvent.postValue(Event(true))
            }
        }

        @WorkerThread
        override fun onGlobalStateChanged(core: Core, state: GlobalState?, message: String) {
            if (state == GlobalState.On) {
                if (core.accountList.isEmpty()) {
                    Log.w("$TAG Provisioning was successful but no account has been configured yet, staying in assistant")
                    // Remote provisioning didn't contain any account
                    // and there wasn't at least one configured before either
                    remoteProvisioningSuccessfulEvent.postValue(Event(false))
                } else {
                    Log.i("$TAG At least an account exists in Core, leaving assistant")
                    remoteProvisioningSuccessfulEvent.postValue(Event(true))
                }
            }
        }

        @WorkerThread
        private fun configureTeleraSipAccount(core: Core, qrContent: String): Boolean {
            return try {
                val json = JSONObject(qrContent.trim())
                val sipAccounts = json.optJSONArray("sipaccounts") ?: return false
                if (sipAccounts.length() == 0) return false

                val first = sipAccounts.getJSONObject(0)
                val username = first.optString("sipusername", "").trim()
                if (username.isEmpty()) return false
                val password = first.optString("sippassword", "")
                val sipProxy = first.optString("sipproxy", "").trim()
                val subDomain = first.optString("subdomain", "").trim()
                // Telera: quando vazio, o servidor SIP padrao e sip.telera.com.br
                val domain = sipProxy.ifEmpty { subDomain.ifEmpty { "sip.telera.com.br" } }

                Log.i("$TAG Configuring Telera SIP account [$username@$domain] from QR code")

                val identity = core.interpretUrl("sip:$username@$domain", false)
                if (identity == null) {
                    Log.e("$TAG Failed to interpret Telera identity sip:$username@$domain")
                    showRedToast(R.string.assistant_qr_code_invalid_toast, R.drawable.warning_circle)
                    return true
                }

                val already = core.accountList.find {
                    it.params.identityAddress?.weakEqual(identity) == true
                }
                if (already != null) {
                    Log.w("$TAG Telera account [$username@$domain] already configured")
                    showRedToast(R.string.assistant_account_login_already_connected_error, R.drawable.warning_circle)
                    remoteProvisioningSuccessfulEvent.postValue(Event(true))
                    return true
                }

                val authInfo = Factory.instance().createAuthInfo(
                    username, null, password, null, null, domain
                )
                core.addAuthInfo(authInfo)

                val accountParams = core.createAccountParams()
                accountParams.identityAddress = identity
                val server = core.interpretUrl("sip:$domain", false)
                if (server != null) {
                    server.transport = TransportType.Udp
                    accountParams.serverAddress = server
                }

                val account = core.createAccount(accountParams)
                core.addAccount(account)
                core.defaultAccount = account
                Log.i("$TAG Telera account [$username@$domain] added, leaving assistant")
                remoteProvisioningSuccessfulEvent.postValue(Event(true))
                true
            } catch (e: Exception) {
                Log.d("$TAG QR content is not a Telera JSON: ${e.message}")
                false
            }
        }

        @WorkerThread
        override fun onQrcodeFound(core: Core, result: String?) {
            Log.i("$TAG QR Code found: [$result]")
            if (result == null) {
                showRedToast(R.string.assistant_qr_code_invalid_toast, R.drawable.warning_circle)
            } else if (configureTeleraSipAccount(core, result)) {
                Log.i("$TAG QR Code handled as Telera SIP account configuration")
            } else {
                val url = LinphoneUtils.getRemoteProvisioningUrlFromUri(result)
                if (url == null) {
                    Log.e("$TAG The content of the QR Code [$result] doesn't seem to be a valid web URL")
                    showRedToast(R.string.assistant_qr_code_invalid_toast, R.drawable.warning_circle)
                    return
                }

                Log.i(
                    "$TAG Setting QR code URL [$url], restarting the Core outside of iterate() loop to apply configuration changes"
                )
                core.nativePreviewWindowId = null
                core.isVideoPreviewEnabled = false
                core.isQrcodeVideoPreviewEnabled = false
                core.provisioningUri = url

                coreContext.postOnCoreThread { core ->
                    Log.i("$TAG Stopping Core")
                    core.stop()
                    Log.i("$TAG Core has been stopped, restarting it")
                    core.start()
                    Log.i("$TAG Core has been restarted")
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            val coreGlobalState = core.globalState
            if (coreGlobalState != GlobalState.On) {
                Log.e("$TAG Core isn't ON (current state is [$coreGlobalState]), video preview won't work!")
            }
        }
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
        super.onCleared()
    }

    @UiThread
    fun setBackCamera() {
        coreContext.postOnCoreThread { core ->
            // Just in case, on some devices such as Xiaomi Redmi Note 5
            // this is required right after granting the CAMERA permission
            Log.i("$TAG Reloading video devices, might be needed if CAMERA permission was granted very recently")
            core.reloadVideoDevices()

            if (!coreContext.setBackCamera()) {
                for (camera in core.videoDevicesList) {
                    if (camera != "StaticImage: Static picture") {
                        Log.w("$TAG No back facing camera found, using first one available [$camera]")
                        coreContext.core.videoDevice = camera
                        return@postOnCoreThread
                    }
                }

                Log.e("$TAG No camera device found!")
            }
        }
    }
}
