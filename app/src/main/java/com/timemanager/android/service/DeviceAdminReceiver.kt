package com.timemanager.android.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Required for Device Owner hard-suspend path.
 * Only activated if user provisions via ADB/QR: dpm set-device-owner ...
 */
class TimeManagerDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
