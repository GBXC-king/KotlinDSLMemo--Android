package com.example.memo.agent

data class AgentStep(
    val id: Int,
    val description: String,
    val actionType: String,
    val details: Map<String, String> = emptyMap()
)

data class AgentPlan(
    val steps: List<AgentStep>,
    val summary: String
)
