package com.krishna.aegis.tools

import com.google.ai.edge.litertlm.tool
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenApiToolSchemaTest {

    @Test
    fun testOpenApiSerializationForAllBuiltInTools() {
        val tools = ToolRegistry.getAllTools()
        assertTrue("Built-in tools must be registered", tools.isNotEmpty())

        for (tool in tools) {
            val adapter = AegisOpenApiTool(tool)
            val jsonStr = adapter.getToolDescriptionJsonString()
            assertNotNull(jsonStr)

            val root = JSONObject(jsonStr)
            assertEquals("Name must match tool name exactly", tool.name, root.getString("name"))
            assertEquals("Description must match tool description", tool.description, root.getString("description"))

            val params = root.getJSONObject("parameters")
            assertEquals("parameters.type must be 'object'", "object", params.getString("type"))
            val props = params.getJSONObject("properties")

            if (tool.parameterSchema.isEmpty()) {
                assertEquals("Properties must be empty for 0-arg tools", 0, props.length())
            } else {
                assertEquals("Properties size must match parameterSchema size", tool.parameterSchema.size, props.length())
                for ((paramName, paramDesc) in tool.parameterSchema) {
                    assertTrue("Properties must contain parameter '$paramName'", props.has(paramName))
                    val propObj = props.getJSONObject(paramName)
                    assertEquals("Property type must be 'string'", "string", propObj.getString("type"))
                    assertEquals("Property description must match", paramDesc, propObj.getString("description"))
                }
                assertTrue("Required array must be present for parameterized tools", params.has("required"))
                val requiredArr = params.getJSONArray("required")
                assertEquals(tool.parameterSchema.size, requiredArr.length())
                val requiredSet = (0 until requiredArr.length()).map { requiredArr.getString(it) }.toSet()
                assertEquals(tool.parameterSchema.keys, requiredSet)
            }
        }
    }

    @Test
    fun testZeroParameterToolSerialization() {
        val zeroArgTool = GoBackTool()
        val adapter = AegisOpenApiTool(zeroArgTool)
        val json = JSONObject(adapter.getToolDescriptionJsonString())

        assertEquals("go_back", json.getString("name"))
        val params = json.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertEquals(0, props.length())
        assertFalse("Required field should not be present or should be empty when no params", params.has("required"))
    }

    @Test
    fun testMultiParameterToolSerialization() {
        val typeTool = TypeTextTool()
        val adapter = AegisOpenApiTool(typeTool)
        val json = JSONObject(adapter.getToolDescriptionJsonString())

        assertEquals("type_text", json.getString("name"))
        val params = json.getJSONObject("parameters")
        val props = params.getJSONObject("properties")
        assertTrue(props.has("text"))
        assertTrue(props.has("target"))
        assertEquals("The string to type", props.getJSONObject("text").getString("description"))
        assertEquals("Optional label or hint of the editable field", props.getJSONObject("target").getString("description"))

        val required = params.getJSONArray("required")
        val requiredList = (0 until required.length()).map { required.getString(it) }
        assertTrue(requiredList.contains("text"))
        assertTrue(requiredList.contains("target"))
    }

    @Test
    fun testCustomToolSerialization() {
        val customTool = object : AegisTool {
            override val name = "custom_camera_capture"
            override val description = "Captures an image using the hardware camera"
            override val parameterSchema = mapOf(
                "resolution" to "Resolution in megapixels",
                "flash" to "Flash mode: auto, on, or off",
                "lens" to "Lens selection: front or back"
            )
            override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
                return ToolOutcome.Ok("Captured image")
            }
        }

        val adapter = AegisOpenApiTool(customTool)
        val root = JSONObject(adapter.getToolDescriptionJsonString())

        assertEquals("custom_camera_capture", root.getString("name"))
        assertEquals("Captures an image using the hardware camera", root.getString("description"))
        val props = root.getJSONObject("parameters").getJSONObject("properties")
        assertEquals(3, props.length())
        assertEquals("Resolution in megapixels", props.getJSONObject("resolution").getString("description"))
        assertEquals("Flash mode: auto, on, or off", props.getJSONObject("flash").getString("description"))
        assertEquals("Lens selection: front or back", props.getJSONObject("lens").getString("description"))
    }

    @Test
    fun testLiteRtToolProviderExport() {
        val tool = OpenAppTool()
        val adapter = AegisOpenApiTool(tool)
        val provider = tool(adapter)
        assertNotNull("ToolProvider from tool() must not be null", provider)
    }

    @Test
    fun testAdapterExecutionInterception() {
        // Under automaticToolCalling = false, OpenApiTool.execute() returns empty string as orchestration routes through RiskAssessor
        val tool = ClickTool()
        val adapter = AegisOpenApiTool(tool)
        val result = adapter.execute("{\"target\": \"Settings\"}")
        assertEquals("", result)
    }
}
