package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.Ledger
import com.example.memo.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 创建账本工具
 */
class CreateLedgerTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_ledger"
    override val description = "创建一个新的账本"
    override val parameters = listOf(
        ParamSpec("name", ParamType.STRING, "账本名称"),
        ParamSpec("unit", ParamType.STRING, "账本单位（如元、根、个）", required = false, defaultValue = "元")
    )
    
    override val promptFragment = """
        ### 记账规则
        - 用户说"记账"、"记一下"、"记录开销"等 → 使用 create_transaction
        - type 参数：0=支出，1=收入
        - 如果账本不存在会自动创建，unit 参数仅在创建新账本时有效
        - 示例：
          - "记一下，今天买早饭花了15元" → type=0, amount=15, unit=元, ledger_name=日常开销
          - "记一下账，今天我做好了两根渔网" → type=1, amount=2, unit=根, ledger_name=渔网
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val name = params["name"] ?: return ToolResult.failure("缺少账本名称")
        val unit = params["unit"] ?: "元"
        val existing = withContext(Dispatchers.IO) {
            deps.ledgerViewModel.findLedgerByTitle(name)
        }
        if (existing != null) {
            return ToolResult.failure("账本\"$name\"已存在")
        }
        val ledger = Ledger(title = name, unit = unit, color = (Math.random() * 10).toInt())
        deps.ledgerViewModel.addLedger(ledger)
        return ToolResult.success("已创建账本：$name（单位：$unit）")
    }
}

/**
 * 查询账本工具
 */
class QueryLedgerTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "query_ledger"
    override val description = "查询账本信息和最近的交易记录"
    override val parameters = listOf(
        ParamSpec("name", ParamType.STRING, "账本名称关键词")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val name = params["name"] ?: return ToolResult.failure("缺少账本名称参数")
        val ledgers = withContext(Dispatchers.IO) {
            deps.ledgerViewModel.allLedgers.first()
        }
        val matched = ledgers.filter { it.title.contains(name) }
        if (matched.isEmpty()) {
            return ToolResult.failure("未找到包含\"$name\"的账本")
        }
        val sb = StringBuilder("找到 ${matched.size} 个账本：\n")
        matched.take(5).forEach { ledger ->
            sb.append("• ${ledger.title}（单位：${ledger.unit}）\n")
        }
        return ToolResult.success(sb.toString().trim())
    }
}

/**
 * 创建交易记录工具
 */
class CreateTransactionTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_transaction"
    override val description = "向指定账本添加一笔交易记录（支出会自动归类到细分账本，并同步写入\"日常开销\"主账本）"
    override val parameters = listOf(
        ParamSpec(
            "ledger_name", ParamType.STRING,
            "账本名称；支出场景下不传或传\"日常开销\"，系统会自动从备注归类到细分账本（餐饮/出行/...），收入场景必须传具体账本名",
            required = false
        ),
        ParamSpec("type", ParamType.INT, "类型：0=支出，1=收入", enumValues = listOf("0", "1"), required = false, defaultValue = "0"),
        ParamSpec("amount", ParamType.DOUBLE, "金额/数量"),
        ParamSpec("unit", ParamType.STRING, "单位，创建新账本时使用", required = false, defaultValue = "元"),
        ParamSpec("note", ParamType.STRING, "备注说明", required = false, defaultValue = ""),
        ParamSpec(
            "date", ParamType.STRING,
            "交易日期：today=今天、yesterday=昨天、day_before_yesterday=前天、YYYY-MM-DD=具体日期；不传或传空时默认 today",
            required = false,
            defaultValue = "today",
            enumValues = listOf("today", "yesterday", "day_before_yesterday")
        )
    )

    override val promptFragment = """
        ### 记账规则
        - 用户说"记账"、"记一下"、"记录开销"等 → 使用 create_transaction
        - type 参数：0=支出，1=收入
        - **账本自动归类**（支出场景）：工具会从备注中识别细分账本（餐饮/住宿/出行/购物/娱乐/医疗/学习/通讯/美容），同时在"日常开销"主账本中再记一笔，做到总账+分类账双写
        - **支出场景下不要把 ledger_name 传成"日常开销"**——主账本由系统自动维护。可省略 ledger_name（最常用），让系统自动归类
        - **支出场景下如果用户明确指定了账本**（如"记到工作账本"），才传 ledger_name
        - **收入场景下必须传 ledger_name**（如"工资"、"奖金"），不会自动归类也不会写主账本
        - **不要传 timestamp，直接传 date 字符串**，由工具内部换算为那一天的时间戳
        - date 参数映射规则（根据用户口语识别）：
          - "今天" / 未指定时间 / "刚才" / 默认 → date="today"（今天的当前时间）
          - "昨晚" / "昨天" / "昨天买了…" → date="yesterday"（昨天 12:00）
          - "前天" → date="day_before_yesterday"（前天 12:00）
          - 具体日期，如"6月30日"、"6月1号"、"2026-06-30" → date="YYYY-MM-DD"（该天 12:00）
        - 如果用户只说"昨晚花了50"，账本/类型/金额/日期都能确定时，必须把 date 设为 "yesterday" 而不是默认 today
        - 示例：
          - "记一下，今天买早饭花了15元" → 不传 ledger_name，系统自动归到"餐饮"+"日常开销"
          - "昨晚打车花了30块" → 不传 ledger_name，系统自动归到"出行"+"日常开销"
          - "前天收到工资5000" → type=1, ledger_name=工资
          - "6月30日吃饭花了80" → 不传 ledger_name，系统自动归到"餐饮"+"日常开销"
          - "记到工作账本，买书花了200" → ledger_name=工作账本，系统自动归到"学习"+"日常开销"
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        return try {
            val ledgerName = params["ledger_name"]?.trim()?.takeIf { it.isNotEmpty() }
            val typeStr = params["type"] ?: "0"
            val amountStr = params["amount"] ?: return ToolResult.failure("缺少金额/数量")
            val unit = params["unit"] ?: "元"
            val note = params["note"] ?: ""
            val dateStr = params["date"]?.trim()?.takeIf { it.isNotEmpty() } ?: "today"
            val timestamp = resolveTransactionTimestamp(dateStr)

            val type = typeStr.toIntOrNull() ?: run {
                return ToolResult.failure("type参数格式不正确，应为0（支出）或1（收入）")
            }
            val amount = amountStr.toDoubleOrNull() ?: run {
                return ToolResult.failure("金额/数量格式不正确")
            }

            // 决定细分账本名
            val subLedgerName: String = if (type == Transaction.TYPE_EXPENSE) {
                // 支出场景：AI 未指定 / 误传了主账本 → 自动从备注归类
                if (ledgerName == null || ledgerName == MASTER_LEDGER_NAME) {
                    detectCategory(note) ?: DEFAULT_CATEGORY
                } else {
                    ledgerName
                }
            } else {
                // 收入场景：必须由 AI 显式指定账本
                ledgerName ?: return ToolResult.failure("收入记账必须指定账本名（ledger_name）")
            }

            val subLedger = withContext(Dispatchers.IO) {
                findOrCreateLedger(subLedgerName, unit, exactOnly = false)
            }

            // 写入细分账本
            val subTransaction = Transaction(
                ledgerId = subLedger.id,
                type = type,
                amount = amount,
                note = note,
                timestamp = timestamp
            )
            deps.ledgerViewModel.addTransaction(subTransaction)

            // 支出场景：同时写入"日常开销"主账本（如果细分账本本身不是主账本）
            val masterLedger: Ledger? = if (
                type == Transaction.TYPE_EXPENSE && subLedger.title != MASTER_LEDGER_NAME
            ) {
                withContext(Dispatchers.IO) {
                    findOrCreateLedger(MASTER_LEDGER_NAME, "元", exactOnly = true)
                }.also { master ->
                    val masterTransaction = Transaction(
                        ledgerId = master.id,
                        type = type,
                        amount = amount,
                        note = note,
                        timestamp = timestamp
                    )
                    deps.ledgerViewModel.addTransaction(masterTransaction)
                }
            } else null

            val typeStr2 = if (type == 1) "收入" else "支出"
            val dateLabel = formatDateLabel(timestamp)
            val ledgerDesc = if (masterLedger != null) {
                "\"${subLedger.title}\"和\"${masterLedger.title}\""
            } else {
                "\"${subLedger.title}\""
            }
            ToolResult.success("已向${ledgerDesc}添加${typeStr2}记录：$amount${subLedger.unit}（$dateLabel，备注：$note）")
        } catch (e: Exception) {
            ToolResult.failure("创建交易记录失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 查找账本（先按标题精确匹配，再按包含关系模糊匹配，找不到则按给定单位创建）
     * @param exactOnly 为 true 时不做模糊匹配；用于"日常开销"主账本，避免误命中用户自定义的相似名字
     */
    private suspend fun findOrCreateLedger(name: String, unit: String, exactOnly: Boolean = false): Ledger {
        deps.ledgerViewModel.findLedgerByTitle(name)?.let { return it }
        if (!exactOnly) {
            val allLedgers = deps.ledgerViewModel.allLedgers.first()
            allLedgers.find { it.title.contains(name) || name.contains(it.title) }?.let { return it }
        }
        val newLedger = Ledger(title = name, unit = unit, color = (Math.random() * 10).toInt())
        val newId = deps.ledgerViewModel.addLedgerSync(newLedger)
        return newLedger.copy(id = newId)
    }

    /**
     * 从备注中识别细分账本类别。命中第一个匹配的关键词即返回类别。
     * linkedMapOf 保持声明顺序，更具体/低歧义的类别排在前面。
     */
    private fun detectCategory(text: String): String? {
        if (text.isBlank()) return null
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            for (keyword in keywords) {
                if (text.contains(keyword)) {
                    return category
                }
            }
        }
        return null
    }

    /**
     * 根据 date 参数解析为该天的时间戳。
     * - today：当前时刻
     * - yesterday / day_before_yesterday：该天 12:00
     * - YYYY-MM-DD：该天 12:00
     * - 解析失败：退回当前时刻
     */
    private fun resolveTransactionTimestamp(dateStr: String): Long {
        val cal = Calendar.getInstance()
        when (dateStr.lowercase()) {
            "today" -> return System.currentTimeMillis()
            "yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            "day_before_yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -2)
            }
            else -> {
                // YYYY-MM-DD：设到当天 12:00，解析失败则回退到当前时刻
                val parsed = try {
                    val parts = dateStr.split("-")
                    if (parts.size == 3) {
                        cal.set(Calendar.YEAR, parts[0].toInt())
                        cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                        cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                        applyNoon(cal)
                        cal.timeInMillis
                    } else {
                        System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                return parsed
            }
        }
        applyNoon(cal)
        return cal.timeInMillis
    }

    /**
     * 把日历时间归零到当天 12:00
     */
    private fun applyNoon(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    /**
     * 把时间戳格式化为 "MM-dd HH:mm" 的可读字符串
     */
    private fun formatDateLabel(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            "%02d-%02d %02d:%02d",
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    companion object {
        /** 主账本：所有支出在这里也会留一份，作为总账 */
        private const val MASTER_LEDGER_NAME = "日常开销"
        /** 识别不到任何类别时的兜底细分账本 */
        private const val DEFAULT_CATEGORY = "其他支出"
        /** 类别关键词映射（顺序敏感：前面的类别优先匹配，避免误归类） */
        private val CATEGORY_KEYWORDS: LinkedHashMap<String, List<String>> = linkedMapOf(
            "医疗" to listOf("看病", "挂号", "体检", "住院", "门诊", "药费", "手术", "处方", "诊所", "医院", "药店", "医药", "买药"),
            "学习" to listOf("买书", "培训", "课程", "学费", "文具", "教材", "网课", "考试", "报名"),
            "通讯" to listOf("话费", "流量", "宽带", "网费", "电话费", "充值"),
            "美容" to listOf("美容", "美发", "美甲", "spa", "理发", "面膜", "染发", "烫发"),
            "住宿" to listOf("酒店", "宾馆", "旅馆", "民宿", "租房", "房租", "住宿", "过夜", "客栈", "旅店"),
            "出行" to listOf("打车", "地铁", "公交", "火车", "高铁", "飞机票", "机票", "出租", "滴滴", "油费", "过路费", "停车费", "停车", "出行", "交通", "车票", "骑车", "共享单车", "快递"),
            "娱乐" to listOf("电影", "KTV", "游戏", "演唱会", "演出", "娱乐", "旅游", "景点", "门票", "游乐园", "网吧"),
            "购物" to listOf("衣服", "鞋子", "包包", "化妆品", "日用品", "购物", "超市", "商场", "菜市场", "网购", "淘宝", "京东", "拼多多", "水果", "零食", "买菜", "买酒"),
            "餐饮" to listOf("吃饭", "喝饮料", "早餐", "午餐", "晚餐", "宵夜", "外卖", "餐厅", "饭店", "聚餐", "喝酒", "奶茶", "咖啡", "米线", "面条", "饺子", "包子", "汉堡", "薯条", "烧烤")
        )
    }
}
