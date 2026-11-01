package com.krishna.aegis.tools

import android.content.Intent
import com.krishna.aegis.data.AegisDatabase
import com.krishna.aegis.data.UserFact

class CreateNoteTool : AegisTool {
    override val name = "create_note"
    override val description = "Creates a new note with title and body in the device's built-in Notes app (Samsung Notes or Google Keep) and commits it to persistent memory."
    override val parameterSchema = mapOf(
        "title" to "Title of the note",
        "content" to "Full body/content text of the note"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val title = args["title"]?.toString()?.trim() ?: "AEGIS Note"
        val content = args["content"]?.toString()?.trim()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'content'")

        val appContext = context.androidContext
        val pm = appContext.packageManager

        // Check target note app: Samsung Notes -> Google Keep -> Generic note action
        val samsungNotesPkg = "com.samsung.android.app.notes"
        val googleKeepPkg = "com.google.android.keep"

        val targetPkg = when {
            isPackageInstalled(pm, samsungNotesPkg) -> samsungNotesPkg
            isPackageInstalled(pm, googleKeepPkg) -> googleKeepPkg
            else -> null
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
            if (targetPkg != null) {
                setPackage(targetPkg)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            appContext.startActivity(intent)

            // Also synchronize with Room DB episodic memory
            val db = AegisDatabase.getInstance(appContext)
            db.factDao().saveFact(
                UserFact(
                    factKey = "note_$title",
                    factValue = content
                )
            )

            val appName = if (targetPkg == samsungNotesPkg) "Samsung Notes" else "Google Keep"
            ToolOutcome.Ok("Successfully created note in $appName and synchronized with persistent memory: \"$title\"")
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to dispatch note creation intent: ${e.message}")
        }
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
