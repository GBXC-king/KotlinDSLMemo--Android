package com.example.memo.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 带高亮效果的文本组件
 *
 * 该组件用于在搜索结果中高亮显示匹配的关键词。
 * 当搜索关键词非空且文本中包含该关键词时，会将所有匹配的部分以蓝色加粗样式显示，
 * 其余文本保持普通样式。
 *
 * 实现原理：使用 Compose 的 buildAnnotatedString 构建带样式的注解字符串，
 * 通过 withStyle 为匹配片段应用 SpanStyle，实现局部文本的样式差异化。
 *
 * @param text           要显示的完整文本
 * @param highlightQuery 搜索关键词（为空时不高亮，直接显示普通文本）
 * @param highlightColor 高亮文本的颜色，默认为蓝色 (0xFF2962FF)
 * @param normalColor    普通文本的颜色，默认为黑色
 * @param fontSize       文本字号
 * @param fontWeight     文本字重（粗细）
 * @param maxLines       最大显示行数，超出部分省略
 */
@Composable
fun HighlightText(
    text: String,
    highlightQuery: String,
    highlightColor: Color = Color(0xFF2962FF),
    normalColor: Color = Color.Black,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    // 如果搜索关键词为空，或文本中不包含关键词，则直接显示普通文本
    if (highlightQuery.isBlank() || !text.contains(highlightQuery, ignoreCase = true)) {
        Text(
            text = text,
            color = normalColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines
        )
    } else {
        // 构建带高亮样式的注解字符串
        val annotatedString = buildAnnotatedString {
            var startIndex = 0
            val lowerText = text.lowercase()          // 转小写用于不区分大小写的匹配
            val lowerQuery = highlightQuery.lowercase()
            var queryIndex = lowerText.indexOf(lowerQuery, startIndex)

            // 遍历文本中所有匹配的关键词位置
            while (queryIndex != -1) {
                // 添加匹配位置之前的普通文本
                if (queryIndex > startIndex) {
                    append(text.substring(startIndex, queryIndex))
                }

                // 添加匹配的关键词文本，并应用高亮样式（蓝色加粗）
                withStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(queryIndex, queryIndex + highlightQuery.length))
                }

                // 移动起始位置，继续查找下一个匹配
                startIndex = queryIndex + highlightQuery.length
                queryIndex = lowerText.indexOf(lowerQuery, startIndex)
            }

            // 添加最后一个匹配位置之后的剩余普通文本
            if (startIndex < text.length) {
                append(text.substring(startIndex))
            }
        }

        Text(
            text = annotatedString,
            fontSize = fontSize,
            maxLines = maxLines
        )
    }
}
