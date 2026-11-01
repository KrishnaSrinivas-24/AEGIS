package com.krishna.aegis.tools

import com.google.ai.edge.litertlm.OpenApiTool
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges an [AegisTool] into LiteRT-LM's [OpenApiTool] contract.
 * Generates dynamic OpenAPI 3.0 tool schemas representing the tool's name,
 * description, and argument parameters for Gemma 4 native on-device function calling.
 */
class AegisOpenApiTool(
    val aegisTool: AegisTool
) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String {
        val root = JSONObject()
        root.put("name", aegisTool.name)
        root.put("description", aegisTool.description)

        val parameters = JSONObject()
        parameters.put("type", "object")

        val properties = JSONObject()
        val required = JSONArray()

        for ((paramName, paramDesc) in aegisTool.parameterSchema) {
            val prop = JSONObject()
            prop.put("type", "string")
            prop.put("description", paramDesc)
            properties.put(paramName, prop)
            required.put(paramName)
        }

        parameters.put("properties", properties)
        if (required.length() > 0) {
            parameters.put("required", required)
        }

        root.put("parameters", parameters)
        return root.toString()
    }

    override fun execute(jsonArgs: String): String {
        // When automaticToolCalling = false, tool calls are intercepted by Conversation.sendMessage,
        // routed through RiskAssessor, and executed asynchronously by the AgentOrchestrator.
        return ""
    }
}
