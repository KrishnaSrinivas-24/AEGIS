package com.krishna.aegis.tools

import org.junit.Assert.*
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun testDefaultToolsAreRegistered() {
        val expectedTools = listOf(
            "read_screen_summary",
            "open_app",
            "click",
            "type_text",
            "scroll",
            "go_back",
            "go_home",
            "save_fact",
            "retrieve_fact",
            "tasks"
        )

        val allTools = ToolRegistry.getAllTools()
        val allToolNames = allTools.map { it.name }.toSet()

        for (expected in expectedTools) {
            assertTrue("Registry must contain default tool '$expected'", allToolNames.contains(expected))
            val tool = ToolRegistry.getTool(expected)
            assertNotNull("getTool('$expected') must return non-null", tool)
            assertEquals(expected, tool?.name)
        }
    }

    @Test
    fun testGetToolReturnsNullForUnknownNames() {
        assertNull("Non-existent tool must return null", ToolRegistry.getTool("non_existent_tool_123"))
        assertNull("Empty string tool name must return null", ToolRegistry.getTool(""))
        assertNull("Whitespace tool name must return null", ToolRegistry.getTool("   "))
        assertNull("Case mismatch should return null if registry is case-sensitive", ToolRegistry.getTool("OPEN_APP"))
    }

    @Test
    fun testRegisterCustomTool() {
        val customTool = object : AegisTool {
            override val name = "test_custom_ping"
            override val description = "Sends a ping for connectivity test"
            override val parameterSchema = mapOf("host" to "Host address to ping")
            override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
                return ToolOutcome.Ok("pong")
            }
        }

        ToolRegistry.register(customTool)
        val retrieved = ToolRegistry.getTool("test_custom_ping")
        assertNotNull("Newly registered tool must be retrievable", retrieved)
        assertEquals("test_custom_ping", retrieved?.name)
        assertEquals("Sends a ping for connectivity test", retrieved?.description)

        val allTools = ToolRegistry.getAllTools()
        assertTrue("Newly registered tool must appear in getAllTools()", allTools.any { it.name == "test_custom_ping" })
    }

    @Test
    fun testGetLiteRtToolProvidersMatchesRegisteredToolsCount() {
        val allTools = ToolRegistry.getAllTools()
        val providers = ToolRegistry.getLiteRtToolProviders()
        assertEquals("Number of LiteRT ToolProviders must match number of registered tools", allTools.size, providers.size)
        for (provider in providers) {
            assertNotNull("Provider instance must not be null", provider)
        }
    }

    @Test
    fun testToolDeclarationsPromptFormat() {
        val prompt = ToolRegistry.getToolDeclarationsPrompt()
        assertTrue("Prompt must start with 'Available Tools:'", prompt.startsWith("Available Tools:\n"))
        assertTrue("Prompt must contain open_app declaration", prompt.contains("- `open_app`:"))
        assertTrue("Prompt must contain click declaration", prompt.contains("- `click`:"))
        assertTrue("Prompt must contain type_text parameters", prompt.contains("    * text:"))
        assertTrue("Prompt must contain tasks declaration", prompt.contains("- `tasks`:"))
    }

    @Test
    fun testReRegistrationOverwritesTool() {
        val v1Tool = object : AegisTool {
            override val name = "versioned_tool"
            override val description = "Version 1.0"
            override val parameterSchema = emptyMap<String, String>()
            override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
                return ToolOutcome.Ok("v1")
            }
        }
        val v2Tool = object : AegisTool {
            override val name = "versioned_tool"
            override val description = "Version 2.0"
            override val parameterSchema = emptyMap<String, String>()
            override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
                return ToolOutcome.Ok("v2")
            }
        }

        ToolRegistry.register(v1Tool)
        assertEquals("Version 1.0", ToolRegistry.getTool("versioned_tool")?.description)

        ToolRegistry.register(v2Tool)
        assertEquals("Re-registering tool with same name must update it", "Version 2.0", ToolRegistry.getTool("versioned_tool")?.description)
    }
}
