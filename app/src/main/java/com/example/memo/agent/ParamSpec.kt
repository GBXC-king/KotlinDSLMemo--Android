package com.example.memo.agent

/**
 * 参数规格定义
 * 用于描述工具参数的类型、约束和说明
 */
data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,  // 枚举值约束
    val defaultValue: String? = null       // 默认值
) {
    /**
     * 生成用于提示词的参数描述
     */
    fun toPromptString(): String {
        val sb = StringBuilder()
        sb.append(name)
        
        // 类型
        sb.append(": ${type.displayName}")
        
        // 必填/可选
        if (!required) {
            sb.append("(可选")
            if (defaultValue != null) {
                sb.append(", 默认: $defaultValue")
            }
            sb.append(")")
        }
        
        // 枚举值
        if (!enumValues.isNullOrEmpty()) {
            sb.append(", 取值: ${enumValues.joinToString("|")}")
        }
        
        // 描述
        sb.append(" - $description")
        
        return sb.toString()
    }
}

/**
 * 参数类型枚举
 */
enum class ParamType(val displayName: String) {
    STRING("string"),
    INT("int"),
    LONG("long"),
    DOUBLE("double"),
    BOOLEAN("boolean")
}
