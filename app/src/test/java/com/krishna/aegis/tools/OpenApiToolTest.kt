package com.krishna.aegis.tools

import com.google.ai.edge.litertlm.tool
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenApiToolTest {

    @Test
    fun testOpenApiToolSerialization() {
        val tool = OpenAppTool()
        val adapter = AegisOpenApiTool(tool)
        val jsonStr = adapter.getToolDescriptionJsonString()
        val json = JSONObject(jsonStr)

        assertEquals("open_app", json.getString("name"))
        assertTrue(json.getString("description").isNotEmpty())
        val params = json.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertTrue(props.has("name"))
        val nameProp = props.getJSONObject("name")
        assertEquals("string", nameProp.getString("type"))
        assertTrue(nameProp.getString("description").isNotEmpty())
    }

    @Test
    fun testLiteRtToolProviderExport() {
        val tool = OpenAppTool()
        val adapter = AegisOpenApiTool(tool)
        val provider = tool(adapter)
        assertNotNull(provider)
    }

    @Test
    fun testToolRegistryProvidesAllToolsAsLiteRtProviders() {
        val registeredTools = ToolRegistry.getAllTools()
        val providers = ToolRegistry.getLiteRtToolProviders()
        assertEquals(registeredTools.size, providers.size)
    }

    @Test
    fun testRiskAssessorFlagsFinancialApps() {
        val snapshot = ScreenContextSnapshot(
            activePackageName = "com.phonepe.app",
            visibleScreenSummary = "Pay contacts or scan QR",
            targetNode = null,
            toolName = "click",
            toolArgs = mapOf("target" to "Pay")
        )
        val verdict = RiskAssessor.evaluateRisk(snapshot)
        assertTrue("Financial app must trigger biometric requirement", verdict is RiskVerdict.RequireBiometric)
        val biometricVerdict = verdict as RiskVerdict.RequireBiometric
        assertTrue(biometricVerdict.score >= 2)
    }

    @Test
    fun testRiskAssessorSafeForStandardNavigation() {
        val snapshot = ScreenContextSnapshot(
            activePackageName = "com.android.settings",
            visibleScreenSummary = "Network & internet, Connected devices, Apps, Display",
            targetNode = null,
            toolName = "click",
            toolArgs = mapOf("target" to "Display")
        )
        val verdict = RiskAssessor.evaluateRisk(snapshot)
        assertEquals(RiskVerdict.Safe, verdict)
    }
}
