package com.example.memo.data

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    // 用户消息携带的附件（图片/PDF/Word/Excel/PPT/TXT），仅在 USER 角色下使用
    //  - 由 sendMessage 在入栈前从 attachments 状态拷贝过来
    //  - MessageBubble 在用户气泡上方渲染横向滚动预览
    val attachments: List<Attachment> = emptyList()
)

data class AIAction(
    val type: ActionType,
    val title: String? = null,
    val content: String? = null,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val dateTime: Long? = null,
    val reminderMessage: String? = null,
    val ledgerName: String? = null,
    val ledgerUnit: String? = null,
    val transactionType: Int? = null,
    val transactionAmount: Double? = null,
    val transactionNote: String? = null,
    val transactionDate: Long? = null,
    val queryType: String? = null,
    val matchKeyword: String? = null,
    val location: String? = null,
    val appName: String? = null,
    val recommendCategory: String? = null,
    val videoQuery: String? = null,
    val videoMode: String? = null,
    val alarmHour: Int? = null,
    val alarmMinute: Int? = null,
    val alarmRepeatDays: Int? = null,
    // 影视相关（内置浏览器方案）
    val movieTitle: String? = null,
    val movieType: String? = null,
    val movieTitles: List<String> = emptyList()
)

enum class ActionType {
    CREATE_NOTE,
    CREATE_CONTACT,
    DELETE_CONTACT,
    UPDATE_CONTACT,
    SEARCH_CONTACT,
    SEARCH_PHONE_NUMBER,
    CREATE_EVENT,
    CREATE_ALARM,
    CALL_PHONE,
    SEARCH_CONTACT_FOR_CALL,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    CREATE_LEDGER,
    DELETE_LEDGER,
    CREATE_TRANSACTION,
    DELETE_TRANSACTION,
    QUERY_LEDGER,
    MATCH_LEDGER,
    OPEN_APP,
    RECOMMEND_APP,
    WATCH_VIDEO,
    // 影视相关（内置浏览器方案）
    PLAY_IN_BROWSER,
    QUERY_PENDING,
    SHOW_PENDING,
    SHOW_RECOMMENDATIONS,
    QUERY_WATCHED,
    NONE
}
