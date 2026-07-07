package com.example.memo.autoui.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.memo.autoui.service.AutoAccessibilityService

object AccessibilityHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedClassName = "${context.packageName}/${AutoAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedClassName, ignoreCase = true) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun formatUiElementsForAI(elements: List<com.example.memo.autoui.model.UiElement>): String {
        val clickableElements = elements.filter { it.isClickable && it.displayText.isNotBlank() }
        val editableElements = elements.filter { it.isEditable && it.displayText.isNotBlank() }
        val scrollableElements = elements.filter { it.isScrollable }
        val otherElements = elements.filter {
            !it.isClickable && !it.isEditable && !it.isScrollable && it.displayText.isNotBlank() && it.text.length > 1
        }.take(20)

        val sb = StringBuilder()
        sb.append("=== 当前屏幕UI元素 ===\n")
        sb.append("当前应用: ${elements.firstOrNull()?.packageName ?: "未知"}\n\n")

        if (clickableElements.isNotEmpty()) {
            sb.append("【可点击元素】(${clickableElements.size}个):\n")
            clickableElements.take(30).forEach {
                sb.append("  ${it.toShortString()}\n")
            }
            if (clickableElements.size > 30) {
                sb.append("  ...还有${clickableElements.size - 30}个\n")
            }
            sb.append("\n")
        }

        if (editableElements.isNotEmpty()) {
            sb.append("【输入框】(${editableElements.size}个):\n")
            editableElements.take(10).forEach {
                sb.append("  ${it.toShortString()}\n")
            }
            sb.append("\n")
        }

        if (scrollableElements.isNotEmpty()) {
            sb.append("【可滚动区域】(${scrollableElements.size}个):\n")
            scrollableElements.take(5).forEach {
                sb.append("  ${it.toShortString()}\n")
            }
            sb.append("\n")
        }

        if (otherElements.isNotEmpty()) {
            sb.append("【其他文本元素】(${otherElements.size}个展示):\n")
            otherElements.forEach {
                sb.append("  ${it.toShortString()}\n")
            }
        }

        return sb.toString()
    }
}
