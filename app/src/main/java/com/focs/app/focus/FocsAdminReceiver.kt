package com.focs.app.focus

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device admin with a single power: locking the screen when a focus session starts. */
class FocsAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, FocsAdminReceiver::class.java)

        fun isActive(ctx: Context): Boolean =
            ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(ctx))

        fun activationIntent(ctx: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Focs only uses this to lock your screen when a focus session starts.",
                )

        fun lockNow(ctx: Context) {
            if (isActive(ctx)) ctx.getSystemService(DevicePolicyManager::class.java).lockNow()
        }
    }
}
