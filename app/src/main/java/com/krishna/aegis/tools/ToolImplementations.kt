package com.krishna.aegis.tools

import com.krishna.aegis.data.AegisDatabase
import com.krishna.aegis.data.UserFact
import com.krishna.aegis.service.UINodeParser
import kotlinx.coroutines.delay

class ReadScreenSummaryTool : AegisTool {
    override val name = "read_screen_summary"
    override val description = "Reads and summarizes all interactive and visible UI elements on the current active screen."
    override val parameterSchema = emptyMap<String, String>()

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val root = context.getRootInActiveWindow()
        val pkg = context.getActivePackageName()
        val summary = UINodeParser.summarizeScreen(root, pkg)
        return ToolOutcome.Ok(summary)
    }
}

class OpenAppTool : AegisTool {
    override val name = "open_app"
    override val description = "Launches an application by name or package (e.g. 'Settings', 'Calculator', 'Chrome')."
    override val parameterSchema = mapOf("name" to "Name or package of the app to launch")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val name = args["name"]?.toString()?.trim()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'name'")

        val resolvedPackage = context.resolvePackageForApp(name)
            ?: return ToolOutcome.TransientErr("Could not find installed application matching '$name'")

        val launched = context.launchApp(resolvedPackage)
        return if (launched) {
            delay(800) // Allow window transition
            ToolOutcome.Ok("Successfully launched '$name' ($resolvedPackage)")
        } else {
            ToolOutcome.TransientErr("Failed to start activity for '$resolvedPackage'")
        }
    }
}

class ClickTool : AegisTool {
    override val name = "click"
    override val description = "Clicks an interactive UI element by text, description, or resource ID."
    override val parameterSchema = mapOf("target" to "Text, content description, or label of the element to click")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val target = args["target"]?.toString()?.trim()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'target'")

        val root = context.getRootInActiveWindow()
        val node = UINodeParser.findClickableNode(root, target)
            ?: return ToolOutcome.TransientErr("UI element matching '$target' not visible on current screen")

        val screenSummary = UINodeParser.summarizeScreen(root, context.getActivePackageName())
        val riskVerdict = RiskAssessor.evaluateRisk(
            ScreenContextSnapshot(
                activePackageName = context.getActivePackageName(),
                visibleScreenSummary = screenSummary,
                targetNode = node,
                toolName = name,
                toolArgs = args
            )
        )

        if (riskVerdict is RiskVerdict.RequireBiometric) {
            val approved = HITLInterceptionWrapper.requestBiometricConfirmation(
                context.androidContext,
                "Authorize click on '${target}' (${riskVerdict.reason})"
            )
            if (!approved) {
                return ToolOutcome.AuthErr("Action aborted: Biometric authentication failed or was cancelled by user.")
            }
        }

        val clicked = context.dispatchClick(node)
        return if (clicked) {
            delay(500)
            ToolOutcome.Ok("Clicked '$target' successfully")
        } else {
            ToolOutcome.TransientErr("Click action dispatched but failed to execute on '$target'")
        }
    }
}

class TypeTextTool : AegisTool {
    override val name = "type_text"
    override val description = "Types text into an input field or focused editable view."
    override val parameterSchema = mapOf(
        "text" to "The string to type",
        "target" to "Optional label or hint of the editable field"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val text = args["text"]?.toString()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'text'")
        val target = args["target"]?.toString()

        val root = context.getRootInActiveWindow()
        val node = UINodeParser.findEditableNode(root, target)
            ?: return ToolOutcome.TransientErr("No editable input field found on current screen")

        val screenSummary = UINodeParser.summarizeScreen(root, context.getActivePackageName())
        val riskVerdict = RiskAssessor.evaluateRisk(
            ScreenContextSnapshot(
                activePackageName = context.getActivePackageName(),
                visibleScreenSummary = screenSummary,
                targetNode = node,
                toolName = name,
                toolArgs = args
            )
        )

        if (riskVerdict is RiskVerdict.RequireBiometric) {
            val approved = HITLInterceptionWrapper.requestBiometricConfirmation(
                context.androidContext,
                "Authorize typing into sensitive field (${riskVerdict.reason})"
            )
            if (!approved) {
                return ToolOutcome.AuthErr("Action aborted: Biometric authentication rejected.")
            }
        }

        val success = context.dispatchSetText(node, text)
        return if (success) {
            delay(300)
            ToolOutcome.Ok("Typed '$text' into target field")
        } else {
            ToolOutcome.TransientErr("Failed to set text on input field")
        }
    }
}

class ScrollTool : AegisTool {
    override val name = "scroll"
    override val description = "Scrolls the current view. Direction can be 'forward' (down) or 'backward' (up)."
    override val parameterSchema = mapOf("direction" to "Direction to scroll: 'forward' or 'backward'")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val direction = args["direction"]?.toString()?.lowercase() ?: "forward"
        val forward = direction != "backward" && direction != "up"
        val success = context.dispatchScroll(forward)
        return if (success) {
            delay(400)
            ToolOutcome.Ok("Scrolled screen $direction")
        } else {
            ToolOutcome.TransientErr("Failed to scroll screen")
        }
    }
}

class GoBackTool : AegisTool {
    override val name = "go_back"
    override val description = "Performs Android system Back navigation."
    override val parameterSchema = emptyMap<String, String>()

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val success = context.dispatchGlobalBack()
        delay(400)
        return if (success) ToolOutcome.Ok("Navigated back") else ToolOutcome.TransientErr("Global back action failed")
    }
}

class GoHomeTool : AegisTool {
    override val name = "go_home"
    override val description = "Navigates to the Android home screen."
    override val parameterSchema = emptyMap<String, String>()

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val success = context.dispatchGlobalHome()
        delay(500)
        return if (success) ToolOutcome.Ok("Returned to Home screen") else ToolOutcome.TransientErr("Home action failed")
    }
}

class SaveFactTool : AegisTool {
    override val name = "save_fact"
    override val description = "Stores a key-value fact or user preference into persistent memory."
    override val parameterSchema = mapOf("key" to "Fact key", "value" to "Fact value")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val key = args["key"]?.toString() ?: return ToolOutcome.LogicErr("Missing 'key'")
        val value = args["value"]?.toString() ?: return ToolOutcome.LogicErr("Missing 'value'")

        val db = AegisDatabase.getInstance(context.androidContext)
        db.factDao().saveFact(UserFact(factKey = key, factValue = value))
        return ToolOutcome.Ok("Saved fact [$key = $value]")
    }
}

class RetrieveFactTool : AegisTool {
    override val name = "retrieve_fact"
    override val description = "Retrieves a stored fact from persistent memory by key."
    override val parameterSchema = mapOf("key" to "Fact key")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val key = args["key"]?.toString() ?: return ToolOutcome.LogicErr("Missing 'key'")
        val db = AegisDatabase.getInstance(context.androidContext)
        val value = db.factDao().getFact(key)
        return if (value != null) {
            ToolOutcome.Ok("Fact [$key]: $value")
        } else {
            ToolOutcome.Ok("Fact [$key] not found")
        }
    }
}

class TasksTool : AegisTool {
    override val name = "tasks"
    override val description = "Terminal action declaring the user's goal has been fully completed."
    override val parameterSchema = mapOf("summary" to "Summary of what was accomplished")

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val summary = args["summary"]?.toString() ?: "Task completed successfully"
        return ToolOutcome.Ok(summary)
    }
}
