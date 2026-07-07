package com.example.memo.agent

import com.example.memo.data.AIAction
import com.example.memo.data.ActionType

object AgentActionParser {

    fun buildPlanFromActions(actions: List<AIAction>): AgentPlan {
        val steps = actions.mapIndexed { index, action ->
            AgentStep(
                id = index,
                description = getActionDescription(action),
                actionType = action.type.name,
                details = getActionDetails(action)
            )
        }
        val summary = "AI识别到 ${steps.size} 个任务，将按顺序执行。"
        return AgentPlan(steps = steps, summary = summary)
    }

    private fun getActionDescription(action: AIAction): String {
        return when (action.type) {
            ActionType.CREATE_NOTE -> "创建笔记：${action.title ?: "无标题"}"
            ActionType.CREATE_CONTACT -> "创建联系人：${action.contactName ?: "未知"}"
            ActionType.DELETE_CONTACT -> "删除联系人：${action.contactName ?: "未知"}"
            ActionType.UPDATE_CONTACT -> "更新联系人：${action.contactName ?: "未知"}"
            ActionType.SEARCH_CONTACT -> "查询联系人：${action.contactName ?: "未知"}"
            ActionType.SEARCH_PHONE_NUMBER -> "查询号码：${action.phoneNumber ?: "未知"}"
            ActionType.CREATE_EVENT -> "创建日程：${action.title ?: "无标题"}"
            ActionType.CREATE_ALARM -> "创建闹钟：${action.title ?: "闹钟"}"
            ActionType.CALL_PHONE -> "拨打电话：${action.phoneNumber ?: "未知"}"
            ActionType.SEARCH_CONTACT_FOR_CALL -> "查找联系人并拨打：${action.contactName ?: "未知"}"
            ActionType.FLASHLIGHT_ON -> "打开手电筒"
            ActionType.FLASHLIGHT_OFF -> "关闭手电筒"
            ActionType.CREATE_LEDGER -> "创建账本：${action.ledgerName ?: "未知"}"
            ActionType.DELETE_LEDGER -> "删除账本：${action.ledgerName ?: "未知"}"
            ActionType.CREATE_TRANSACTION -> "添加记账：${action.ledgerName ?: "未知"} ${action.transactionAmount ?: 0}元"
            ActionType.DELETE_TRANSACTION -> "删除记账记录"
            ActionType.QUERY_LEDGER -> "查询账本：${action.ledgerName ?: "未知"}"
            ActionType.MATCH_LEDGER -> "匹配账本：${action.matchKeyword ?: "未知"}"
            ActionType.OPEN_APP -> "打开应用：${action.appName ?: "未知"}"
            ActionType.RECOMMEND_APP -> "推荐应用：${action.recommendCategory ?: "未知"}"
            ActionType.WATCH_VIDEO -> "看视频：${action.videoQuery ?: "未知"}（${action.videoMode ?: "search"}）"
            ActionType.PLAY_IN_BROWSER -> "内置浏览器播放：${action.movieTitle ?: "未知"}"
            ActionType.QUERY_PENDING -> "查询待看：${action.movieType ?: "未知"}"
            ActionType.SHOW_PENDING -> "展示待看列表"
            ActionType.SHOW_RECOMMENDATIONS -> "展示推荐结果：${action.movieTitles.size}个"
            ActionType.QUERY_WATCHED -> "查询已看历史"
            ActionType.NONE -> "无操作"
        }
    }

    private fun getActionDetails(action: AIAction): Map<String, String> {
        val details = mutableMapOf<String, String>()
        action.title?.let { details["title"] = it }
        action.content?.let { details["content"] = it }
        action.contactName?.let { details["contactName"] = it }
        action.phoneNumber?.let { details["phoneNumber"] = it }
        action.ledgerName?.let { details["ledgerName"] = it }
        action.appName?.let { details["appName"] = it }
        action.transactionAmount?.let { details["amount"] = it.toString() }
        return details
    }
}
