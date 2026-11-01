package com.krishna.aegis.tools

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object HITLInterceptionWrapper {
    @Volatile
    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    suspend fun requestBiometricConfirmation(
        context: Context,
        reason: String,
        timeoutMs: Long = 30000L
    ): Boolean {
        pendingDeferred?.cancel()
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred

        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(BiometricAuthActivity.EXTRA_REASON, reason)
        }
        context.startActivity(intent)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        pendingDeferred = null
        return result
    }

    fun onBiometricResult(approved: Boolean) {
        pendingDeferred?.let {
            if (it.isActive) {
                it.complete(approved)
            }
        }
    }
}
