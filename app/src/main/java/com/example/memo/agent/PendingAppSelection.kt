package com.example.memo.agent

data class PendingAppSelection(
    val category: String,
    val apps: List<String>,
    val packageNames: List<String>,
    val descriptions: List<String> = emptyList(),
    // 与 apps/packageNames 平行的可选深链（Scheme）列表，用于跨应用跳转（如视频搜索/免费专区）
    // 为 null 或越界时表示该应用无深链，回退到普通打开应用
    val deepLinks: List<String?> = emptyList()
)
