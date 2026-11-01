package com.krishna.aegis.tools

import android.view.accessibility.AccessibilityNodeInfo

data class ScreenContextSnapshot(
    val activePackageName: String,
    val visibleScreenSummary: String,
    val targetNode: AccessibilityNodeInfo? = null,
    val toolName: String,
    val toolArgs: Map<String, Any?>
)

sealed class RiskVerdict {
    object Safe : RiskVerdict()
    data class RequireBiometric(val reason: String, val score: Int) : RiskVerdict()
}

object RiskAssessor {

    private val PAYMENT_KEYWORDS = setOf(
        "pay", "payment", "send", "transfer", "upi", "checkout", "buy", "purchase",
        "confirm order", "place order", "send money"
    )

    private val AUTH_KEYWORDS = setOf(
        "pin", "otp", "password", "passcode", "cvv", "credential", "api_key", "secret"
    )

    private val DESTRUCTIVE_KEYWORDS = setOf(
        "delete", "remove", "erase", "uninstall", "clear data", "wipe", "factory reset",
        "format", "terminate account", "drop database"
    )

    private val FINANCIAL_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.sbi.upi",
        "com.icicibank.mobile",
        "com.hdfcbank.android"
    )

    fun evaluateRisk(snapshot: ScreenContextSnapshot): RiskVerdict {
        var riskScore = 0
        val detectedReasons = mutableListOf<String>()

        val lowerPackage = snapshot.activePackageName.lowercase()
        if (FINANCIAL_PACKAGES.any { lowerPackage.contains(it) }) {
            riskScore += 2
            detectedReasons.add("Financial/Banking App context ($lowerPackage)")
        }

        val textToInspect = buildString {
            append(snapshot.visibleScreenSummary.lowercase())
            append(" ")
            append(snapshot.toolArgs.values.joinToString(" ").lowercase())
            snapshot.targetNode?.let { node ->
                append(" ")
                append(node.text?.toString()?.lowercase() ?: "")
                append(" ")
                append(node.contentDescription?.toString()?.lowercase() ?: "")
                append(" ")
                append(node.viewIdResourceName?.lowercase() ?: "")
            }
        }

        if (snapshot.targetNode?.isPassword == true) {
            riskScore += 2
            detectedReasons.add("Target input is a Password field")
        }

        val paymentHit = PAYMENT_KEYWORDS.filter { textToInspect.contains(it) }
        if (paymentHit.isNotEmpty()) {
            riskScore += paymentHit.size.coerceAtMost(2)
            detectedReasons.add("Payment keywords detected: ${paymentHit.joinToString()}")
        }

        val authHit = AUTH_KEYWORDS.filter { textToInspect.contains(it) }
        if (authHit.isNotEmpty()) {
            riskScore += authHit.size.coerceAtMost(2)
            detectedReasons.add("Authentication/Credential keywords detected: ${authHit.joinToString()}")
        }

        val destructiveHit = DESTRUCTIVE_KEYWORDS.filter { textToInspect.contains(it) }
        if (destructiveHit.isNotEmpty()) {
            riskScore += (destructiveHit.size * 2).coerceAtMost(4)
            detectedReasons.add("Destructive keywords detected: ${destructiveHit.joinToString()}")
        }

        return if (riskScore >= 2) {
            RiskVerdict.RequireBiometric(
                reason = detectedReasons.joinToString("; "),
                score = riskScore
            )
        } else {
            RiskVerdict.Safe
        }
    }
}
