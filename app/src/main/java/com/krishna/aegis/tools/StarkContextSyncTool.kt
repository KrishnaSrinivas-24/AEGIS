package com.krishna.aegis.tools

import android.os.Environment
import com.krishna.aegis.data.AegisDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StarkContextSyncTool : AegisTool {
    override val name = "stark_context_sync"
    override val description = "Synchronizes mobile episodic memory (WhatsApp messages, Gmail context, learned skills, user facts) into an Obsidian PCOS Markdown bundle for Project S.T.A.R.K."
    override val parameterSchema = mapOf(
        "summary" to "Optional high-level note or session goal to attach to this sync snapshot"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val userSummary = args["summary"]?.toString()?.trim() ?: "Periodic Mobile Context Sync"
        val appContext = context.androidContext
        val db = AegisDatabase.getInstance(appContext)

        val notifications = db.notificationDao().getLatest(30)
        val facts = db.factDao().getAllFacts()
        val skills = db.skillDao().getAllSkills()

        val now = Date()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val humanFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

        val markdown = buildString {
            appendLine("---")
            appendLine("title: \"AEGIS Mobile Context Snapshot\"")
            appendLine("date: ${isoFormat.format(now)}")
            appendLine("type: mobile-sentinel-sync")
            appendLine("device: \"Samsung Galaxy M15 5G (SM-M156B)\"")
            appendLine("tags:")
            appendLine("  - aegis")
            appendLine("  - synapse-mas")
            appendLine("  - pcos")
            appendLine("  - stark-triad")
            appendLine("---")
            appendLine()
            appendLine("# 🛡️ AEGIS Mobile Context Snapshot — Project S.T.A.R.K.")
            appendLine("> **Timestamp**: ${humanFormat.format(now)}")
            appendLine("> **Device**: Samsung Galaxy M15 (Android 16)")
            appendLine("> **Sync Directive**: $userSummary")
            appendLine()
            appendLine("## 🧠 Episodic Facts (`UserFact`)")
            if (facts.isEmpty()) {
                appendLine("*No episodic facts recorded in local SQLite.*")
            } else {
                for (fact in facts) {
                    appendLine("- **[[${fact.factKey}]]**: ${fact.factValue}")
                }
            }
            appendLine()
            appendLine("## 💬 Captured Message Context (WhatsApp & Gmail)")
            if (notifications.isEmpty()) {
                appendLine("*No recent buffered notifications captured.*")
            } else {
                for (n in notifications) {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.timestamp))
                    appendLine("- **[$time] [${n.appTitle}]** `[[${n.sender}]]`: ${n.content}")
                }
            }
            appendLine()
            appendLine("## ⚡ Learned Autonomous Skills (`SkillDao`)")
            if (skills.isEmpty()) {
                appendLine("*No cached ReAct action sequences recorded.*")
            } else {
                for (skill in skills) {
                    appendLine("- **Goal**: \"${skill.goalPattern}\" (Successes: ${skill.successCount})")
                    appendLine("  - Actions: `${skill.planJson}`")
                }
            }
            appendLine()
            appendLine("---")
            appendLine("*Generated autonomously by com.krishna.aegis for Project S.T.A.R.K. / SYNAPSE-MAS.*")
        }

        // Write to public Documents/STARK_SYNC directory
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val syncDir = File(docsDir, "STARK_SYNC")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }

        val latestFile = File(syncDir, "aegis_context_latest.md")
        val archiveFile = File(syncDir, "aegis_context_${fileStampFormat.format(now)}.md")

        return try {
            latestFile.writeText(markdown)
            archiveFile.writeText(markdown)
            ToolOutcome.Ok("Project S.T.A.R.K. context sync complete! Wrote ${markdown.length} bytes to:\n${latestFile.absolutePath}\n${archiveFile.absolutePath}")
        } catch (e: Exception) {
            ToolOutcome.TransientErr("Failed to write STARK sync markdown: ${e.message}")
        }
    }
}
