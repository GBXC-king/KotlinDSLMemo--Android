package com.example.memo.agent

import org.json.JSONObject

data class ReActStep(
    val thought: String?,
    val action: String?,
    val actionInput: Map<String, String>?,
    val finalAnswer: String?,
    val rawText: String
) {
    val hasAction: Boolean get() = action != null && actionInput != null
    val hasFinalAnswer: Boolean get() = finalAnswer != null
}

object ReActParser {

    fun parse(text: String): ReActStep {
        var thought: String? = null
        var action: String? = null
        var actionInput: Map<String, String>? = null
        var finalAnswer: String? = null

        val thoughtRegex = Regex("思考[:：]\\s*(.+?)(?=\\n行动[:：]|\\n最终答案[:：]|$)", RegexOption.DOT_MATCHES_ALL)
        val actionRegex = Regex("行动[:：]\\s*(.+?)(?=\\n行动输入[:：]|\\n最终答案[:：]|$)", RegexOption.DOT_MATCHES_ALL)
        val actionInputRegex = Regex("行动输入[:：]\\s*(.+?)(?=\\n思考[:：]|\\n最终答案[:：]|$)", RegexOption.DOT_MATCHES_ALL)
        val finalAnswerRegex = Regex("最终答案[:：]\\s*(.+)", RegexOption.DOT_MATCHES_ALL)

        thoughtRegex.find(text)?.groupValues?.get(1)?.trim()?.let {
            thought = it
        }

        actionRegex.find(text)?.groupValues?.get(1)?.trim()?.let {
            action = it
        }

        actionInputRegex.find(text)?.groupValues?.get(1)?.trim()?.let { inputStr ->
            actionInput = parseActionInput(inputStr)
        }

        finalAnswerRegex.find(text)?.groupValues?.get(1)?.trim()?.let {
            finalAnswer = it
        }

        if (thought == null && action == null && finalAnswer == null) {
            finalAnswer = text.trim()
        }

        return ReActStep(
            thought = thought,
            action = action,
            actionInput = actionInput,
            finalAnswer = finalAnswer,
            rawText = text
        )
    }

    private fun parseActionInput(input: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        val cleanedInput = input
            .replace(Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()

        val jsonStr = extractJsonString(cleanedInput)
        if (jsonStr != null) {
            try {
                val jsonObject = JSONObject(jsonStr)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!jsonObject.isNull(key)) {
                        val value = jsonObject.getString(key)
                        result[key] = value
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
            }
        }

        val jsonPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        jsonPattern.findAll(cleanedInput).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (key.isNotBlank()) {
                result[key] = value
            }
        }

        if (result.isNotEmpty()) return result

        val keyValuePattern = Regex("(\\w+)\\s*[:=]\\s*[\"']?([^\"',\\n]+)[\"']?", RegexOption.IGNORE_CASE)
        keyValuePattern.findAll(cleanedInput).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
            if (key.isNotBlank() && !key.equals("action", ignoreCase = true)) {
                result[key] = value
            }
        }

        if (result.isEmpty() && cleanedInput.isNotBlank()) {
            result["input"] = cleanedInput.trim()
        }

        return result
    }

    private fun extractJsonString(input: String): String? {
        val startIndex = input.indexOf('{')
        if (startIndex == -1) return null

        var depth = 0
        var inString = false
        var escape = false

        for (i in startIndex until input.length) {
            val char = input[i]
            if (escape) {
                escape = false
                continue
            }
            when (char) {
                '\\' -> escape = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) {
                        return input.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }
}
