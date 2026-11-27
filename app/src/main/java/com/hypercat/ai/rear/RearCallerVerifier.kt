package com.hypercat.ai.rear

import android.content.Context

/**
 * Only the REAREye app that hosts this widget may read the rear state or send
 * interaction events. Package ownership is resolved by Android from the caller UID;
 * callers cannot authenticate themselves by supplying a package-name extra.
 */
internal object RearCallerVerifier {
    const val TRUSTED_PACKAGE = "hk.uwu.reareye"

    fun isTrustedUid(context: Context, uid: Int): Boolean {
        if (uid <= 0) return false
        return context.packageManager.getPackagesForUid(uid)
            ?.any { it == TRUSTED_PACKAGE } == true
    }
}
