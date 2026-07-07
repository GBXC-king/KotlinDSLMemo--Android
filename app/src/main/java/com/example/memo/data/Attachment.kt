package com.example.memo.data

import android.net.Uri

/**
 * AI 聊天附件数据类型
 *  - 图片：保留缩略图 Bitmap 字段（用于 UI 渲染）
 *  - 文档/其他：保留 displayName + mimeType，用于显示文件类型图标
 *  - 发送时按 GLM 多模态格式组装为 base64 data URL
 */
data class Attachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: AttachmentType,
    // 图片缩略图（仅图片类型有效，文档为 null）
    val thumbnail: android.graphics.Bitmap? = null,
    // Base64 编码后的数据（发送时填充，UI 阶段可为空以节省内存）
    val base64Data: String? = null,
    // 拍照生成的本地图片文件路径（用于拍照后删除临时文件）
    val localFilePath: String? = null
) {
    /** 文件大小的人类可读字符串，例如 "1.2 MB" */
    fun readableSize(): String = formatSize(sizeBytes)

    companion object {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1 -> String.format("%.1f MB", mb)
                kb >= 1 -> String.format("%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}

enum class AttachmentType {
    IMAGE,
    DOCUMENT
}
