package com.krishna.aegis.tools

object ToolRegistry {
    private val tools = mutableMapOf<String, AegisTool>()

    init {
        register(ReadScreenSummaryTool())
        register(OpenAppTool())
        register(ClickTool())
        register(TypeTextTool())
        register(ScrollTool())
        register(GoBackTool())
        register(GoHomeTool())
        register(SaveFactTool())
        register(RetrieveFactTool())
        register(TasksTool())
    }

    fun register(tool: AegisTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AegisTool? = tools[name]

    fun getAllTools(): List<AegisTool> = tools.values.toList()

    fun getToolDeclarationsPrompt(): String {
        return buildString {
            append("Available Tools:\n")
            tools.values.forEach { tool ->
                append("- `${tool.name}`: ${tool.description}\n")
                if (tool.parameterSchema.isNotEmpty()) {
                    append("  Parameters:\n")
                    tool.parameterSchema.forEach { (param, desc) ->
                        append("    * $param: $desc\n")
                    }
                }
            }
        }
    }
}
