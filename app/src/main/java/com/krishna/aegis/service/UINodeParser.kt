package com.krishna.aegis.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object UINodeParser {

    fun findClickableNode(root: AccessibilityNodeInfo?, targetQuery: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val q = targetQuery.trim().lowercase()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (text.contains(q) || desc.contains(q) || viewId.contains(q)) {
                // If this node is clickable, return it
                if (node.isClickable) return node

                // Walk up parent chain if the label itself isn't clickable but its container row is
                var current: AccessibilityNodeInfo? = node.parent
                while (current != null) {
                    if (current.isClickable) return current
                    current = current.parent
                }
                // Return node even if not marked clickable as performAction(ACTION_CLICK) can still propagate
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findEditableNode(root: AccessibilityNodeInfo?, targetQuery: String? = null): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val q = targetQuery?.trim()?.lowercase()

        var firstEditable: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable || node.isFocused) {
                if (q.isNullOrEmpty()) {
                    return node
                }
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.lowercase() ?: ""
                } else ""

                if (text.contains(q) || desc.contains(q) || hint.contains(q)) {
                    return node
                }
                if (firstEditable == null && node.isEditable) {
                    firstEditable = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return firstEditable
    }

    fun summarizeScreen(root: AccessibilityNodeInfo?, packageName: String): String {
        if (root == null) return "Screen unavailable."
        val elements = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && elements.size < 40) {
            val node = queue.removeFirst()
            val label = (node.text ?: node.contentDescription)?.toString()?.trim()

            if (!label.isNullOrEmpty()) {
                val tag = when {
                    node.isEditable -> "[Input]"
                    node.isCheckable -> if (node.isChecked) "[Toggle:ON]" else "[Toggle:OFF]"
                    node.isClickable -> "[Button]"
                    else -> "[Text]"
                }
                elements.add("$tag $label")
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return "App: $packageName | Elements: ${elements.joinToString(", ")}"
    }
}
