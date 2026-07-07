package com.example.memo.agent.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.PhoneHelper

/**
 * 创建联系人工具
 */
class CreateContactTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_contact"
    override val description = "创建一个新的联系人"
    override val parameters = listOf(
        ParamSpec("name", ParamType.STRING, "联系人姓名"),
        ParamSpec("phone", ParamType.STRING, "电话号码")
    )
    
    override val promptFragment = """
        ### 联系人操作规则
        - 创建联系人：使用 create_contact，name 为姓名，phone 为电话号码
        - 搜索联系人：使用 search_contact，name 为姓名关键词
        - 删除联系人：使用 delete_contact，name 为姓名（精确匹配）
        - 根据号码查联系人：使用 search_phone，phone 为电话号码
        - 拨打电话：
          - 直接号码 → 使用 call_phone，phone 为号码
          - 姓名拨打 → 先 search_contact 找到号码，再用 call_phone 拨打
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val name = params["name"] ?: return ToolResult.failure("缺少联系人姓名")
        val phone = params["phone"] ?: return ToolResult.failure("缺少电话号码")
        val existing = PhoneHelper.searchContactByPhone(deps.context, phone)
        if (existing != null) {
            return ToolResult.failure("该号码已存在联系人：${existing.name}（${existing.phone}）")
        }
        PhoneHelper.createContact(deps.context, name, phone)
        return ToolResult.success("已创建联系人：$name（$phone）")
    }
}

/**
 * 搜索联系人工具
 */
class SearchContactTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "search_contact"
    override val description = "根据姓名搜索联系人"
    override val parameters = listOf(
        ParamSpec("name", ParamType.STRING, "联系人姓名关键词")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val name = params["name"] ?: return ToolResult.failure("缺少姓名参数")
        val hasPermission = ContextCompat.checkSelfPermission(deps.context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return ToolResult.failure("需要读取通讯录权限才能查询联系人")
        }
        val contacts = PhoneHelper.searchContactsByName(deps.context, name)
        if (contacts.isEmpty()) {
            return ToolResult.failure("未找到包含\"$name\"的联系人")
        }
        val sb = StringBuilder("找到 ${contacts.size} 个联系人：\n")
        contacts.take(10).forEach { c ->
            sb.append("• ${c.name}")
            c.phone?.let { sb.append("（$it）") }
            sb.append("\n")
        }
        if (contacts.size > 10) {
            sb.append("...还有${contacts.size - 10}个")
        }
        return ToolResult.success(sb.toString().trim())
    }
}

/**
 * 删除联系人工具
 */
class DeleteContactTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "delete_contact"
    override val description = "删除指定姓名的联系人（精确匹配）"
    override val parameters = listOf(
        ParamSpec("name", ParamType.STRING, "联系人姓名，必须精确匹配")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val name = params["name"] ?: return ToolResult.failure("缺少姓名参数")
        val hasPermission = ContextCompat.checkSelfPermission(deps.context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return ToolResult.failure("需要写入通讯录权限才能删除联系人")
        }
        val contacts = PhoneHelper.searchContactsByName(deps.context, name)
        val exactMatch = contacts.find { it.name == name }
        if (exactMatch == null) {
            return ToolResult.failure("未找到精确匹配\"$name\"的联系人")
        }
        PhoneHelper.deleteContact(deps.context, exactMatch.id ?: return ToolResult.failure("联系人ID无效"))
        return ToolResult.success("已删除联系人：$name")
    }
}

/**
 * 搜索电话号码工具
 */
class SearchPhoneTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "search_phone"
    override val description = "根据电话号码查找对应的联系人"
    override val parameters = listOf(
        ParamSpec("phone", ParamType.STRING, "电话号码")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val phone = params["phone"] ?: return ToolResult.failure("缺少电话号码参数")
        val hasPermission = ContextCompat.checkSelfPermission(deps.context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return ToolResult.failure("需要读取通讯录权限")
        }
        val contact = PhoneHelper.searchContactByPhone(deps.context, phone)
        return if (contact != null) {
            ToolResult.success("号码 $phone 对应的联系人是：${contact.name}")
        } else {
            ToolResult.failure("未找到号码为 $phone 的联系人")
        }
    }
}

/**
 * 拨打电话工具
 */
class CallPhoneTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "call_phone"
    override val description = "拨打指定的电话号码"
    override val parameters = listOf(
        ParamSpec("phone", ParamType.STRING, "电话号码")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val phone = params["phone"] ?: return ToolResult.failure("缺少电话号码参数")
        val hasPermission = ContextCompat.checkSelfPermission(deps.context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = if (hasPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        deps.context.startActivity(intent)
        return if (hasPermission) {
            ToolResult.success("正在拨打 $phone")
        } else {
            ToolResult.success("已打开拨号界面，请手动确认拨打 $phone")
        }
    }
}
