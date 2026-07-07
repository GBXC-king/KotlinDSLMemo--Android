package com.example.memo.autoui.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.memo.autoui.model.UiElement
import com.example.memo.autoui.engine.UiAutoController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoAccessibility"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected

        private var instance: AutoAccessibilityService? = null

        fun getInstance(): AutoAccessibilityService? = instance

        fun isConnected(): Boolean = _isServiceConnected.value
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!UiAutoController.isRunning.value) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                UiAutoController.onUserClickDetected()
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
        Log.d(TAG, "无障碍服务已断开")
    }

    fun getAllUiElements(): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        var idCounter = 1
        traverseNode(root, elements, 0, idCounter) { _, _ -> idCounter++ }
        return elements
    }

    fun getClickableElements(): List<UiElement> {
        return getAllUiElements().filter { it.isClickable && it.displayText.isNotBlank() }
    }

    fun getCurrentPackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<UiElement>,
        depth: Int,
        startId: Int,
        onIdAssigned: (Int, AccessibilityNodeInfo) -> Unit
    ): Int {
        var currentId = startId
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val element = UiElement(
            id = currentId,
            text = node.text?.toString() ?: "",
            contentDescription = node.contentDescription?.toString() ?: "",
            className = node.className?.toString() ?: "",
            packageName = node.packageName?.toString() ?: "",
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            boundsLeft = rect.left,
            boundsTop = rect.top,
            boundsRight = rect.right,
            boundsBottom = rect.bottom,
            depth = depth,
            childCount = node.childCount
        )
        elements.add(element)
        onIdAssigned(currentId, node)
        currentId++

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                currentId = traverseNode(child, elements, depth + 1, currentId, onIdAssigned)
            }
        }
        return currentId
    }

    fun findElementById(elementId: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var idCounter = 1
        var result: AccessibilityNodeInfo? = null
        traverseNode(root, mutableListOf(), 0, idCounter) { id, node ->
            if (id == elementId) {
                result = node
            }
        }
        return result
    }

    fun clickElementById(elementId: Int): Boolean {
        val node = findElementById(elementId) ?: return false
        val result = if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val parent = findClickableParent(node)
            parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    fun clickByText(text: String): Boolean {
        val elements = getAllUiElements()
        val target = elements.firstOrNull {
            it.text.contains(text, ignoreCase = true) ||
            it.contentDescription.contains(text, ignoreCase = true)
        } ?: return false
        return clickElementById(target.id)
    }

    fun clickByCoordinate(x: Int, y: Int): Boolean {
        return performClickGesture(x, y)
    }

    private fun performClickGesture(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        val result = dispatchGesture(gesture, null, null)
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    fun swipe(direction: String, durationMs: Long = 500): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val coords = when (direction.lowercase()) {
            "up" -> floatArrayOf(width / 2f, height * 0.7f, width / 2f, height * 0.3f)
            "down" -> floatArrayOf(width / 2f, height * 0.3f, width / 2f, height * 0.7f)
            "left" -> floatArrayOf(width * 0.8f, height / 2f, width * 0.2f, height / 2f)
            "right" -> floatArrayOf(width * 0.2f, height / 2f, width * 0.8f, height / 2f)
            else -> return false
        }
        val startX = coords[0]
        val startY = coords[1]
        val endX = coords[2]
        val endY = coords[3]

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val result = dispatchGesture(gesture, null, null)
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    fun inputTextById(elementId: Int, text: String): Boolean {
        val node = findElementById(elementId) ?: return false
        if (!node.isEditable) {
            val editableChild = findEditableChild(node) ?: return false
            return inputTextToNode(editableChild, text)
        }
        return inputTextToNode(node, text)
    }

    private fun findEditableChild(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableChild(child)
            if (found != null) return found
        }
        return null
    }

    private fun inputTextToNode(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    fun pressBack(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    fun pressHome(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        if (result) {
            UiAutoController.notifyAiActionPerformed()
        }
        return result
    }

    fun pressRecentApps(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun scrollForwardById(elementId: Int): Boolean {
        val node = findElementById(elementId) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackwardById(elementId: Int): Boolean {
        val node = findElementById(elementId) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
}
