package com.krishna.aegis.tools

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchLocalFilesTool : AegisTool {
    override val name = "search_local_files"
    override val description = "Searches for files on local storage (Downloads, Documents, etc.) matching a filename query or extension."
    override val parameterSchema = mapOf(
        "query" to "File name keyword or extension to search (e.g. 'resume', 'invoice', '.pdf', '.apk')",
        "directory" to "Optional specific directory to search (e.g. 'Downloads', 'Documents'). Defaults to all common folders.",
        "limit" to "Maximum number of files to return (default: 10)"
    )

    override suspend fun execute(args: Map<String, Any?>, context: ToolServiceContext): ToolOutcome {
        val query = args["query"]?.toString()?.trim()?.lowercase()
            ?: return ToolOutcome.LogicErr("Missing required argument: 'query'")
        val dirFilter = args["directory"]?.toString()?.trim()?.lowercase()
        val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 30)

        val rootDirs = mutableListOf<File>()
        val extStorage = Environment.getExternalStorageDirectory()

        if (dirFilter != null) {
            when {
                dirFilter.contains("download") -> rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                dirFilter.contains("doc") -> rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
                dirFilter.contains("dcim") || dirFilter.contains("photo") -> rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                else -> rootDirs.add(File(extStorage, dirFilter))
            }
        } else {
            rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            rootDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            rootDirs.add(File(extStorage, "Documents"))
            rootDirs.add(File(extStorage, "Download"))
        }

        val results = JSONArray()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (dir in rootDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            scanDirectory(dir, query, results, dateFormat, limit)
            if (results.length() >= limit) break
        }

        return if (results.length() == 0) {
            ToolOutcome.Ok("No local files found matching query: \"$query\"")
        } else {
            ToolOutcome.Ok(results.toString(2))
        }
    }

    private fun scanDirectory(
        dir: File,
        query: String,
        results: JSONArray,
        dateFormat: SimpleDateFormat,
        limit: Int
    ) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (results.length() >= limit) return
            if (f.isDirectory && !f.name.startsWith(".")) {
                scanDirectory(f, query, results, dateFormat, limit)
            } else if (f.isFile) {
                val nameLower = f.name.lowercase()
                if (nameLower.contains(query) || (query.startsWith(".") && nameLower.endsWith(query))) {
                    val sizeMb = String.format(Locale.US, "%.2f MB", f.length() / (1024.0 * 1024.0))
                    val obj = JSONObject().apply {
                        put("name", f.name)
                        put("path", f.absolutePath)
                        put("size", sizeMb)
                        put("lastModified", dateFormat.format(Date(f.lastModified())))
                    }
                    results.put(obj)
                }
            }
        }
    }
}
