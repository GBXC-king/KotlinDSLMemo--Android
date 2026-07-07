package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.autoui.service.AutoAccessibilityService
import com.example.memo.autoui.util.AccessibilityHelper

/**
 * 获取屏幕UI元素工具
 */
class GetScreenUiTool : AgentTool {
    override val name = "get_screen_ui"
    override val description = "获取当前手机屏幕上的所有UI元素（按钮、输入框、文本等），用于分析当前界面并决定下一步操作"
    override val parameters = listOf(
        ParamSpec("filter", ParamType.STRING, "过滤类型", enumValues = listOf("clickable", "editable", "all"), required = false, defaultValue = "all")
    )
    
    override val promptFragment = """
        ### 手机自动操控规则
        当用户要求在某个App中完成操作时：
        1. 先用 open_app 打开目标App
        2. 调用 get_screen_ui 获取当前屏幕UI元素
        3. 分析UI元素，找到需要操作的元素
        4. 调用 click_element / input_text / swipe_screen 执行操作
        5. **重复步骤2-4直到用户需求被满足**（打开App不等于任务完成！）
        
        **工具使用策略**：
        - get_screen_ui：每次操作后重新调用，确认页面变化
        - click_element：优先用 element_id（最准确），也可用 text 模糊匹配
        - input_text：向输入框输入文字
        - swipe_screen：滑动翻页，direction: up/down/left/right
        - press_back：按返回键
        - press_home：按Home键
        
        **重要原则**：
        - 找不到目标元素时尝试滑动页面
        - 连续两次相同操作无效果则停止并说明
        - 涉及支付、转账等敏感操作时提醒用户确认
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val elements = service.getAllUiElements()
        if (elements.isEmpty()) {
            return ToolResult.failure("当前屏幕没有获取到UI元素，可能是锁屏或界面未加载完成")
        }

        val filter = params["filter"] ?: "all"
        val filteredElements = when (filter) {
            "clickable" -> elements.filter { it.isClickable && it.displayText.isNotBlank() }
            "editable" -> elements.filter { it.isEditable }
            else -> elements
        }

        return ToolResult.success(AccessibilityHelper.formatUiElementsForAI(filteredElements))
    }
}

/**
 * 点击元素工具
 */
class ClickElementTool : AgentTool {
    override val name = "click_element"
    override val description = "点击屏幕上的某个UI元素，可以通过元素ID、文字内容或坐标来指定"
    override val parameters = listOf(
        ParamSpec("element_id", ParamType.INT, "元素ID（推荐，最准确）", required = false),
        ParamSpec("text", ParamType.STRING, "元素的文字内容（模糊匹配）", required = false),
        ParamSpec("x", ParamType.INT, "点击的x坐标（和y一起使用）", required = false),
        ParamSpec("y", ParamType.INT, "点击的y坐标（和x一起使用）", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val elementIdStr = params["element_id"]
        val text = params["text"]
        val xStr = params["x"]
        val yStr = params["y"]

        val success = when {
            elementIdStr != null -> {
                val elementId = elementIdStr.toIntOrNull() ?: return ToolResult.failure("element_id格式不正确")
                service.clickElementById(elementId)
            }
            !text.isNullOrBlank() -> {
                service.clickByText(text)
            }
            xStr != null && yStr != null -> {
                val x = xStr.toIntOrNull() ?: return ToolResult.failure("x坐标格式不正确")
                val y = yStr.toIntOrNull() ?: return ToolResult.failure("y坐标格式不正确")
                service.clickByCoordinate(x, y)
            }
            else -> {
                return ToolResult.failure("请指定 element_id、text 或 x,y 参数")
            }
        }

        val desc = when {
            elementIdStr != null -> "元素ID:$elementIdStr"
            !text.isNullOrBlank() -> "文字包含:\"$text\""
            xStr != null -> "坐标($xStr,$yStr)"
            else -> "未知元素"
        }

        return if (success) {
            ToolResult.success("已成功点击$desc")
        } else {
            ToolResult.failure("点击失败：$desc，请检查元素是否存在或可点击")
        }
    }
}

/**
 * 输入文本工具
 */
class InputTextTool : AgentTool {
    override val name = "input_text"
    override val description = "向指定的输入框中输入文字"
    override val parameters = listOf(
        ParamSpec("element_id", ParamType.INT, "输入框元素ID（推荐）"),
        ParamSpec("text", ParamType.STRING, "要输入的文字内容")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val elementIdStr = params["element_id"] ?: return ToolResult.failure("缺少 element_id 参数")
        val text = params["text"] ?: return ToolResult.failure("缺少 text 参数")

        val elementId = elementIdStr.toIntOrNull() ?: return ToolResult.failure("element_id格式不正确")

        val success = service.inputTextById(elementId, text)
        return if (success) {
            ToolResult.success("已向输入框(元素ID:$elementId)输入文字：$text")
        } else {
            ToolResult.failure("输入失败：找不到输入框或输入框不可编辑")
        }
    }
}

/**
 * 滑动屏幕工具
 */
class SwipeScreenTool : AgentTool {
    override val name = "swipe_screen"
    override val description = "在屏幕上滑动，用于滚动页面、翻页等操作"
    override val parameters = listOf(
        ParamSpec("direction", ParamType.STRING, "滑动方向", enumValues = listOf("up", "down", "left", "right")),
        ParamSpec("duration", ParamType.LONG, "滑动持续时间（毫秒）", required = false, defaultValue = "500")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val direction = params["direction"] ?: return ToolResult.failure("缺少 direction 参数")
        val duration = params["duration"]?.toLongOrNull() ?: 500

        val validDirections = listOf("up", "down", "left", "right")
        if (direction.lowercase() !in validDirections) {
            return ToolResult.failure("direction 参数必须是 ${validDirections.joinToString("/")} 之一")
        }

        val success = service.swipe(direction, duration)
        return if (success) {
            ToolResult.success("已向${direction}方向滑动屏幕")
        } else {
            ToolResult.failure("滑动失败")
        }
    }
}

/**
 * 按返回键工具
 */
class PressBackTool : AgentTool {
    override val name = "press_back"
    override val description = "按下返回键，返回上一个页面"
    override val parameters = emptyList<ParamSpec>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val success = service.pressBack()
        return if (success) {
            ToolResult.success("已按下返回键")
        } else {
            ToolResult.failure("返回失败")
        }
    }
}

/**
 * 按Home键工具
 */
class PressHomeTool : AgentTool {
    override val name = "press_home"
    override val description = "按下Home键，回到手机主屏幕"
    override val parameters = emptyList<ParamSpec>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = AutoAccessibilityService.getInstance()
            ?: return ToolResult.failure("无障碍服务未启动，请先在设置中开启无障碍权限")

        val success = service.pressHome()
        return if (success) {
            ToolResult.success("已按下Home键，回到主屏幕")
        } else {
            ToolResult.failure("返回主屏幕失败")
        }
    }
}
