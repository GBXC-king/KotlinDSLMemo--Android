package com.example.memo.autoui.model

data class UiElement(
    val id: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val packageName: String,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val depth: Int,
    val childCount: Int
) {
    val centerX: Int
        get() = (boundsLeft + boundsRight) / 2

    val centerY: Int
        get() = (boundsTop + boundsBottom) / 2

    val width: Int
        get() = boundsRight - boundsLeft

    val height: Int
        get() = boundsBottom - boundsTop

    val displayText: String
        get() = text.ifBlank { contentDescription }.ifBlank { className.substringAfterLast('.') }

    fun toShortString(): String {
        val type = when {
            isEditable -> "输入框"
            isClickable -> "按钮"
            isScrollable -> "滚动区"
            isCheckable -> if (isChecked) "复选框✓" else "复选框"
            else -> "文本"
        }
        return "[元素$id] $type: \"$displayText\" (${centerX},${centerY})"
    }
}
