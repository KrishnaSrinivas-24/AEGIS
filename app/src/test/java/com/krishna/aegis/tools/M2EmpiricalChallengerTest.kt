package com.krishna.aegis.tools

import com.google.ai.edge.litertlm.tool
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Empirical Challenge Harness for Milestone M2:
 * Tests LiteRT-LM OpenAPI tool serialization, ToolRegistry provider parity,
 * RiskAssessor boundary/adversarial cases, and automaticToolCalling=false enforcement.
 */
class M2EmpiricalChallengerTest {

    private class CustomAdversarialTool(
        override val name: String,
        override val description: String,
        override val parameterSchema: Map<String, String>
    ) : AegisTool {
        override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome = ToolOutcome.Ok("ok")
    }

    // -------------------------------------------------------------
    // 1. Adversarial schemas for AegisOpenApiTool
    // -------------------------------------------------------------

    @Test
    fun testAdversarialEmptyParameterSchema() {
        val tool = CustomAdversarialTool(
            name = "empty_params_tool",
            description = "A tool with zero parameters",
            parameterSchema = emptyMap()
        )
        val adapter = AegisOpenApiTool(tool)
        val jsonStr = adapter.getToolDescriptionJsonString()
        assertNotNull(jsonStr)

        val json = JSONObject(jsonStr)
        assertEquals("empty_params_tool", json.getString("name"))
        assertEquals("A tool with zero parameters", json.getString("description"))

        val params = json.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        assertEquals(0, params.getJSONObject("properties").length())
        assertFalse("When schema has no params, 'required' array must not be present", params.has("required"))
    }

    @Test
    fun testAdversarialSpecialCharactersInDescriptionAndNames() {
        val complexDesc = "Special chars: quotes \"double\" and 'single', newlines \n\r, tabs \t, emojis 🛡️⚡🤖, backslash C:\\\\path, and unicode \u0000 \uFFFF"
        val tool = CustomAdversarialTool(
            name = "weird_tool_name_!@#",
            description = complexDesc,
            parameterSchema = mapOf(
                "arg with spaces & symbols !@#$%" to "Desc with <xml>tags</xml> & \"quotes\"",
                "unicode_param_日本語" to "Japanese description: 端末の画面を読み取る"
            )
        )
        val adapter = AegisOpenApiTool(tool)
        val jsonStr = adapter.getToolDescriptionJsonString()
        assertNotNull(jsonStr)

        val json = JSONObject(jsonStr)
        assertEquals("weird_tool_name_!@#", json.getString("name"))
        assertEquals(complexDesc, json.getString("description"))

        val params = json.getJSONObject("parameters")
        val props = params.getJSONObject("properties")
        assertTrue(props.has("arg with spaces & symbols !@#$%"))
        val p1 = props.getJSONObject("arg with spaces & symbols !@#$%")
        assertEquals("string", p1.getString("type"))
        assertEquals("Desc with <xml>tags</xml> & \"quotes\"", p1.getString("description"))

        assertTrue(props.has("unicode_param_日本語"))
        val p2 = props.getJSONObject("unicode_param_日本語")
        assertEquals("Japanese description: 端末の画面を読み取る", p2.getString("description"))

        val req = params.getJSONArray("required")
        assertEquals(2, req.length())
    }

    @Test
    fun testAdversarialEmptyStringsAndValues() {
        val tool = CustomAdversarialTool(
            name = "",
            description = "",
            parameterSchema = mapOf("" to "")
        )
        val adapter = AegisOpenApiTool(tool)
        val jsonStr = adapter.getToolDescriptionJsonString()
        assertNotNull(jsonStr)

        val json = JSONObject(jsonStr)
        assertEquals("", json.getString("name"))
        assertEquals("", json.getString("description"))
        val params = json.getJSONObject("parameters")
        val props = params.getJSONObject("properties")
        assertTrue(props.has(""))
        val req = params.getJSONArray("required")
        assertEquals(1, req.length())
        assertEquals("", req.getString(0))
    }

    // -------------------------------------------------------------
    // 2. Tool count parity
    // -------------------------------------------------------------

    @Test
    fun testToolRegistryParityAndCompleteness() {
        val allTools = ToolRegistry.getAllTools()
        val providers = ToolRegistry.getLiteRtToolProviders()

        assertTrue("ToolRegistry must contain tools", allTools.isNotEmpty())
        assertEquals("Count of LiteRT ToolProviders must strictly equal count of registered AegisTools", allTools.size, providers.size)

        for (tool in allTools) {
            val adapter = AegisOpenApiTool(tool)
            val jsonStr = adapter.getToolDescriptionJsonString()
            val parsed = JSONObject(jsonStr)
            assertEquals(tool.name, parsed.getString("name"))
            assertEquals(tool.description, parsed.getString("description"))
            val provider = tool(adapter)
            assertNotNull(provider)
        }
    }

    // -------------------------------------------------------------
    // 3. RiskAssessor boundary stress tests
    // -------------------------------------------------------------

    @Test
    fun testRiskAssessorFinancialAppsBoundaryCases() {
        val financialCases = listOf(
            "com.google.android.apps.nbu.paisa.user",
            "COM.GOOGLE.ANDROID.APPS.NBU.PAISA.USER",
            "Com.Google.Android.Apps.Nbu.Paisa.User",
            "com.phonepe.app",
            "COM.PHONEPE.APP",
            "com.phonepe.app.v2",
            "net.one97.paytm",
            "NET.ONE97.PAYTM",
            "in.org.npci.upiapp",
            "IN.ORG.NPCI.UPIAPP",
            "com.sbi.upi",
            "COM.SBI.UPI",
            "com.icicibank.mobile",
            "COM.ICICIBANK.MOBILE",
            "com.hdfcbank.android",
            "COM.HDFCBANK.ANDROID"
        )

        for (pkg in financialCases) {
            val snapshot = ScreenContextSnapshot(
                activePackageName = pkg,
                visibleScreenSummary = "Checking dashboard",
                targetNode = null,
                toolName = "click",
                toolArgs = mapOf("target" to "Overview")
            )
            val verdict = RiskAssessor.evaluateRisk(snapshot)
            assertTrue("Financial package '$pkg' must require biometric verification", verdict is RiskVerdict.RequireBiometric)
            val req = verdict as RiskVerdict.RequireBiometric
            assertTrue(req.score >= 2)
            assertTrue(req.reason.contains("Financial/Banking App context"))
        }
    }

    @Test
    fun testRiskAssessorPaymentKeywordsVariationsAndThreshold() {
        // Single non-overlapping payment keyword ('buy') -> Safe (score = 1, below threshold 2)
        val singleKw = ScreenContextSnapshot(
            activePackageName = "com.android.chrome",
            visibleScreenSummary = "Article discussing buy options in ancient Rome",
            targetNode = null,
            toolName = "scroll",
            toolArgs = emptyMap()
        )
        assertEquals("Single non-overlapping payment keyword in benign app must remain Safe", RiskVerdict.Safe, RiskAssessor.evaluateRisk(singleKw))

        // Multiple payment keywords in mixed case -> RequireBiometric
        val multiKw = ScreenContextSnapshot(
            activePackageName = "com.android.chrome",
            visibleScreenSummary = "Please Confirm Order and send money via UPI",
            targetNode = null,
            toolName = "click",
            toolArgs = mapOf("action" to "TRANSFER")
        )
        val verdict = RiskAssessor.evaluateRisk(multiKw)
        assertTrue("Multiple payment keywords must trigger RequireBiometric", verdict is RiskVerdict.RequireBiometric)
    }

    /**
     * EMPIRICAL FINDING:
     * Demonstrates that because RiskAssessor uses raw string containment (`textToInspect.contains(it)`)
     * against a keyword set containing both "pay" and "payment", the single word "payment"
     * matches both keywords, resulting in paymentHit.size == 2 and prematurely escalating to
     * RiskVerdict.RequireBiometric (score = 2).
     */
    @Test
    fun testEmpiricalFindingPaymentSubstringDoubleCounting() {
        val snapshot = ScreenContextSnapshot(
            activePackageName = "com.android.chrome",
            visibleScreenSummary = "Article discussing payment options",
            targetNode = null,
            toolName = "scroll",
            toolArgs = emptyMap()
        )
        val verdict = RiskAssessor.evaluateRisk(snapshot)
        assertTrue("Single word 'payment' triggers RequireBiometric due to double-counting 'pay' + 'payment'",
            verdict is RiskVerdict.RequireBiometric
        )
        val biometric = verdict as RiskVerdict.RequireBiometric
        assertEquals("Both 'pay' and 'payment' matched on a single word", 2, biometric.score)
        assertTrue(biometric.reason.contains("pay"))
        assertTrue(biometric.reason.contains("payment"))
    }

    @Test
    fun testRiskAssessorAuthKeywordsBoundary() {
        // Single auth keyword -> Safe (score 1)
        val singleAuth = ScreenContextSnapshot(
            activePackageName = "com.android.settings",
            visibleScreenSummary = "App credentials management",
            targetNode = null,
            toolName = "click",
            toolArgs = emptyMap()
        )
        assertEquals(RiskVerdict.Safe, RiskAssessor.evaluateRisk(singleAuth))

        // Multiple auth keywords mixed case -> RequireBiometric (score >= 2)
        val multiAuth = ScreenContextSnapshot(
            activePackageName = "com.android.settings",
            visibleScreenSummary = "ENTER YOUR SECRET PIN AND PASSWORD TO UNLOCK",
            targetNode = null,
            toolName = "type_text",
            toolArgs = mapOf("text" to "1234")
        )
        val verdict = RiskAssessor.evaluateRisk(multiAuth)
        assertTrue("Multiple auth keywords must trigger RequireBiometric", verdict is RiskVerdict.RequireBiometric)
    }

    @Test
    fun testRiskAssessorDestructiveKeywordsImmediateThreshold() {
        val destructiveList = listOf("delete", "DELETE", "Wipe", "CLEAR DATA", "factory reset", "erase")
        for (kw in destructiveList) {
            val snapshot = ScreenContextSnapshot(
                activePackageName = "com.android.settings",
                visibleScreenSummary = "Confirm operation: $kw",
                targetNode = null,
                toolName = "click",
                toolArgs = mapOf("button" to "OK")
            )
            val verdict = RiskAssessor.evaluateRisk(snapshot)
            assertTrue("Destructive keyword '$kw' must immediately trigger RequireBiometric", verdict is RiskVerdict.RequireBiometric)
        }
    }

    @Test
    fun testRiskAssessorNullAndEmptyContexts() {
        // Completely empty snapshot
        val emptySnapshot = ScreenContextSnapshot(
            activePackageName = "",
            visibleScreenSummary = "",
            targetNode = null,
            toolName = "",
            toolArgs = emptyMap()
        )
        assertEquals(RiskVerdict.Safe, RiskAssessor.evaluateRisk(emptySnapshot))

        // Snapshot with null values in toolArgs map
        val nullValuesInArgs = ScreenContextSnapshot(
            activePackageName = "com.sec.android.app.popupcalculator",
            visibleScreenSummary = "Calculator screen 2 + 2 = 4",
            targetNode = null,
            toolName = "click",
            toolArgs = mapOf("arg1" to null, "arg2" to null, "arg3" to 42)
        )
        assertEquals("Null arguments map values must not crash or trigger false positive", RiskVerdict.Safe, RiskAssessor.evaluateRisk(nullValuesInArgs))
    }

    @Test
    fun testRiskAssessorNonFinancialAppsRemainSafe() {
        val nonFinancialApps = listOf(
            "com.android.settings",
            "com.google.android.calculator",
            "com.sec.android.app.clockpackage",
            "com.android.chrome",
            "com.whatsapp",
            "com.google.android.gm",
            "com.samsung.android.app.notes"
        )
        for (pkg in nonFinancialApps) {
            val snapshot = ScreenContextSnapshot(
                activePackageName = pkg,
                visibleScreenSummary = "Normal benign screen layout and display text",
                targetNode = null,
                toolName = "scroll",
                toolArgs = mapOf("direction" to "down")
            )
            assertEquals("Benign package '$pkg' must evaluate to Safe", RiskVerdict.Safe, RiskAssessor.evaluateRisk(snapshot))
        }
    }

    // -------------------------------------------------------------
    // 4. Verify automaticToolCalling = false enforcement
    // -------------------------------------------------------------

    @Test
    fun testAegisEngineEnforcesAutomaticToolCallingFalse() {
        val engineCandidates = listOf(
            File("src/main/java/com/krishna/aegis/engine/AegisEngine.kt"),
            File("app/src/main/java/com/krishna/aegis/engine/AegisEngine.kt")
        )
        val engineSourceFile = engineCandidates.firstOrNull { it.exists() }
        assertNotNull("AegisEngine.kt source file must exist", engineSourceFile)
        val content = engineSourceFile!!.readText()

        assertTrue("AegisEngine must instantiate ConversationConfig", content.contains("ConversationConfig("))
        assertTrue("AegisEngine must explicitly set automaticToolCalling = false",
            content.contains("automaticToolCalling = false")
        )

        val openApiCandidates = listOf(
            File("src/main/java/com/krishna/aegis/tools/AegisOpenApiTool.kt"),
            File("app/src/main/java/com/krishna/aegis/tools/AegisOpenApiTool.kt")
        )
        val openApiToolFile = openApiCandidates.firstOrNull { it.exists() }
        assertNotNull("AegisOpenApiTool.kt source file must exist", openApiToolFile)
        val openApiContent = openApiToolFile!!.readText()
        assertTrue("AegisOpenApiTool.execute must return empty string (passive bridge)",
            openApiContent.contains("return \"\"")
        )
    }
}
