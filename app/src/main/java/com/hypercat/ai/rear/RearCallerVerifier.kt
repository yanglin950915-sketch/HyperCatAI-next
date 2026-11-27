package com.hypercat.ai.rear

import android.content.Context

/**
 * Accepts calls only from Xiaomi's system Theme Manager, which hosts native MAML
 * rear-screen cards. Package ownership is resolved from the Binder UID; callers
 * cannot authenticate themselves by supplying a package-name extra.
 */
internal object RearCallerVerifier {
    private val TRUSTED_PACKAGES = setOf("com.android.thememanager")

    fun isTrustedUid(context: Context, uid: Int): Boolean {
        if (uid <= 0) return false
        return context.packageManager.getPackagesForUid(uid)
            ?.any { it in TRUSTED_PACKAGES } == true
    }
}
