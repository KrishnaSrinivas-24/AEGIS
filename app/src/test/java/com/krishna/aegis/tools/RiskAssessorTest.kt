package com.krishna.aegis.tools

import org.junit.Assert.*
import org.junit.Test

class RiskAssessorTest {

    private fun createSnapshot(
        packageName: String = "com.android.settings",
        summary: String = "Normal UI settings",
        tool: String = "click",
        args: Map<String, Any?> = emptyMap()
    ): ScreenContextSnapshot {
        return ScreenContextSnapshot(
            activePackageName = packageName,
            visibleScreenSummary = summary,
            targetNode = null,
            toolName = tool,
            toolArgs = args
        )
    }

    @Test
    fun testFinancialPackagesTriggerRequireBiometric() {
        val financialPackages = listOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.sbi.upi",
            "com.icicibank.mobile",
            "com.hdfcbank.android"
        )

        for (pkg in financialPackages) {
            val snapshot = createSnapshot(packageName = pkg, summary = "Balance check screen")
            val verdict = RiskAssessor.evaluateRisk(snapshot)
            assertTrue("Financial package '$pkg' must require biometric verification", verdict is RiskVerdict.RequireBiometric)
            val req = verdict as RiskVerdict.RequireBiometric
            assertTrue("Score for financial package must be >= 2", req.score >= 2)
            assertTrue("Reason must state financial app context", req.reason.contains("Financial/Banking App context"))
        }
    }

    @Test
    fun testPaymentKeywordsThresholdEvaluation() {
        // Single payment keyword produces score = 1, which remains below threshold of 2
        val singleKeywordSnapshot = createSnapshot(
            summary = "Items in cart",
            args = mapOf("target" to "buy")
        )
        val singleVerdict = RiskAssessor.evaluateRisk(singleKeywordSnapshot)
        assertEquals("Single payment keyword (score 1) should remain Safe below threshold 2", RiskVerdict.Safe, singleVerdict)

        // Multiple payment keywords (e.g. 'buy' and 'checkout') reach score = 2 and require biometric
        val multiKeywordSnapshot = createSnapshot(
            summary = "Proceed to checkout now",
            args = mapOf("target" to "buy")
        )
        val multiVerdict = RiskAssessor.evaluateRisk(multiKeywordSnapshot)
        assertTrue("Two payment keywords must trigger RequireBiometric", multiVerdict is RiskVerdict.RequireBiometric)
        val req = multiVerdict as RiskVerdict.RequireBiometric
        assertEquals(2, req.score)
        assertTrue(req.reason.contains("Payment keywords detected"))
    }

    @Test
    fun testAuthKeywordsRequireBiometric() {
        val authSnapshot = createSnapshot(
            summary = "Enter your password and pin",
            args = mapOf("target" to "Submit")
        )
        val verdict = RiskAssessor.evaluateRisk(authSnapshot)
        assertTrue("Auth keywords must require biometric", verdict is RiskVerdict.RequireBiometric)
        val req = verdict as RiskVerdict.RequireBiometric
        assertTrue(req.score >= 2)
        assertTrue(req.reason.contains("Authentication/Credential keywords detected"))
    }

    @Test
    fun testDestructiveKeywordsImmediateEscalation() {
        val destructiveKeywords = listOf(
            "delete", "remove", "erase", "uninstall", "clear data", "wipe",
            "factory reset", "format", "terminate account", "drop database"
        )

        for (kw in destructiveKeywords) {
            val snapshot = createSnapshot(
                summary = "Are you sure you want to $kw this device?",
                args = mapOf("target" to "Confirm")
            )
            val verdict = RiskAssessor.evaluateRisk(snapshot)
            assertTrue("Destructive keyword '$kw' must trigger RequireBiometric", verdict is RiskVerdict.RequireBiometric)
            val req = verdict as RiskVerdict.RequireBiometric
            assertTrue("Destructive action score must be >= 2", req.score >= 2)
            assertTrue(req.reason.contains("Destructive keywords detected"))
        }
    }

    @Test
    fun testSafePackagesAndBenignContexts() {
        val safeScenarios = listOf(
            createSnapshot(packageName = "com.sec.android.app.popupcalculator", summary = "Display: 12 + 34 = 46", args = mapOf("target" to "=")),
            createSnapshot(packageName = "com.android.settings", summary = "Display, Sound, Storage, Battery", args = mapOf("target" to "Display")),
            createSnapshot(packageName = "com.sec.android.app.clockpackage", summary = "Alarm set for 07:00 AM", args = mapOf("target" to "Alarms")),
            createSnapshot(packageName = "com.android.chrome", summary = "Wikipedia: Machine Learning overview", args = mapOf("target" to "Overview"))
        )

        for (snapshot in safeScenarios) {
            val verdict = RiskAssessor.evaluateRisk(snapshot)
            assertEquals("Safe scenario for '${snapshot.activePackageName}' must evaluate to Safe", RiskVerdict.Safe, verdict)
        }
    }

    @Test
    fun testCaseInsensitiveEvaluation() {
        val upperSnapshot = createSnapshot(
            summary = "CONFIRM ORDER AND PLACE ORDER FOR ITEMS",
            args = mapOf("target" to "PAY NOW")
        )
        val verdict = RiskAssessor.evaluateRisk(upperSnapshot)
        assertTrue("Evaluation must be case-insensitive and catch uppercase keywords", verdict is RiskVerdict.RequireBiometric)
    }

    @Test
    fun testCombinedRiskScoring() {
        // Financial package + Payment keyword + Auth keyword
        val highRiskSnapshot = ScreenContextSnapshot(
            activePackageName = "com.phonepe.app",
            visibleScreenSummary = "Confirm transfer of Rs 5000",
            targetNode = null,
            toolName = "type_text",
            toolArgs = mapOf("text" to "1234", "target" to "Enter UPI PIN")
        )
        val verdict = RiskAssessor.evaluateRisk(highRiskSnapshot)
        assertTrue(verdict is RiskVerdict.RequireBiometric)
        val req = verdict as RiskVerdict.RequireBiometric
        assertTrue("High risk cumulative score must be >= 4", req.score >= 4)
        assertTrue(req.reason.contains("Financial/Banking App context"))
        assertTrue(req.reason.contains("Payment keywords detected"))
        assertTrue(req.reason.contains("Authentication/Credential keywords detected"))
    }

    @Test
    fun testEmptySnapshotIsSafe() {
        val emptySnapshot = ScreenContextSnapshot(
            activePackageName = "",
            visibleScreenSummary = "",
            targetNode = null,
            toolName = "",
            toolArgs = emptyMap()
        )
        val verdict = RiskAssessor.evaluateRisk(emptySnapshot)
        assertEquals(RiskVerdict.Safe, verdict)
    }
}
