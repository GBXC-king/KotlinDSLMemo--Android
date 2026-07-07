package com.example.memo.data.parser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Function Calling 工具定义
 * 定义所有可用的工具函数及其参数 schema
 */
object ToolDefinitions {
    
    /**
     * 获取所有工具定义的 JSON 数组
     */
    fun getToolsDefinition(): JSONArray {
        return JSONArray().apply {
            put(createEventTool())
            put(createNoteTool())
            put(createContactTool())
            put(deleteContactTool())
            put(searchContactTool())
            put(searchPhoneNumberTool())
            put(callPhoneTool())
            put(createLedgerTool())
            put(deleteLedgerTool())
            put(createTransactionTool())
            put(matchLedgerTool())
            put(openAppTool())
            put(recommendAppTool())
            put(watchVideoTool())
            put(flashlightTool())
        }
    }
    
    /**
     * 创建日程提醒
     */
    private fun createEventTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_event")
                put("description", "创建日程提醒，在指定时间提醒用户做某事")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "string")
                            put("description", "事件标题")
                        })
                        put("time", JSONObject().apply {
                            put("type", "string")
                            put("description", "提醒时间，格式：YYYY-MM-DD HH:MM")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("title")
                        put("time")
                    })
                })
            })
        }
    }
    
    /**
     * 创建笔记
     */
    private fun createNoteTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_note")
                put("description", "创建一条笔记备忘录")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "string")
                            put("description", "笔记标题")
                        })
                        put("content", JSONObject().apply {
                            put("type", "string")
                            put("description", "笔记内容")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("title")
                    })
                })
            })
        }
    }
    
    /**
     * 创建联系人
     */
    private fun createContactTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_contact")
                put("description", "创建一个新的联系人")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "联系人姓名")
                        })
                        put("phone", JSONObject().apply {
                            put("type", "string")
                            put("description", "电话号码")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("name")
                        put("phone")
                    })
                })
            })
        }
    }
    
    /**
     * 删除联系人
     */
    private fun deleteContactTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_contact")
                put("description", "删除指定姓名的联系人")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "联系人姓名")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("name")
                    })
                })
            })
        }
    }
    
    /**
     * 搜索联系人
     */
    private fun searchContactTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_contact")
                put("description", "根据姓名搜索联系人")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "联系人姓名关键词")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("name")
                    })
                })
            })
        }
    }
    
    /**
     * 搜索电话号码
     */
    private fun searchPhoneNumberTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_phone_number")
                put("description", "根据电话号码查找联系人")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("phone", JSONObject().apply {
                            put("type", "string")
                            put("description", "电话号码")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("phone")
                    })
                })
            })
        }
    }
    
    /**
     * 拨打电话
     */
    private fun callPhoneTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "call_phone")
                put("description", "拨打电话，可以是电话号码或联系人姓名")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("target", JSONObject().apply {
                            put("type", "string")
                            put("description", "电话号码或联系人姓名")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("target")
                    })
                })
            })
        }
    }
    
    /**
     * 创建账本
     */
    private fun createLedgerTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_ledger")
                put("description", "创建一个新的账本")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "账本名称")
                        })
                        put("unit", JSONObject().apply {
                            put("type", "string")
                            put("description", "货币单位，默认为'元'")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("name")
                    })
                })
            })
        }
    }
    
    /**
     * 删除账本
     */
    private fun deleteLedgerTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_ledger")
                put("description", "删除指定的账本")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "账本名称")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("name")
                    })
                })
            })
        }
    }
    
    /**
     * 创建记账记录
     */
    private fun createTransactionTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_transaction")
                put("description", "添加一条记账记录")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("ledger_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "账本名称")
                        })
                        put("amount", JSONObject().apply {
                            put("type", "string")
                            put("description", "金额")
                        })
                        put("note", JSONObject().apply {
                            put("type", "string")
                            put("description", "备注")
                        })
                        put("date", JSONObject().apply {
                            put("type", "string")
                            put("description", "日期，格式：YYYY-MM-DD")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("ledger_name")
                        put("amount")
                    })
                })
            })
        }
    }
    
    /**
     * 匹配账本
     */
    private fun matchLedgerTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "match_ledger")
                put("description", "根据关键词匹配账本")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "账本关键词")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("keyword")
                    })
                })
            })
        }
    }
    
    /**
     * 打开应用
     */
    private fun openAppTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_app")
                put("description", "打开指定的应用")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("app_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "应用名称")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("app_name")
                    })
                })
            })
        }
    }
    
    /**
     * 推荐应用
     */
    private fun recommendAppTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "recommend_app")
                put("description", "根据需求推荐应用")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("category", JSONObject().apply {
                            put("type", "string")
                            put("description", "应用类别或需求描述")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("category")
                    })
                })
            })
        }
    }
    
    /**
     * 观看视频
     */
    private fun watchVideoTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "watch_video")
                put("description", "搜索或观看视频内容")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "视频内容名称或描述")
                        })
                        put("mode", JSONObject().apply {
                            put("type", "string")
                            put("description", "模式：search（搜索具体内容）或 free（免费专区）")
                            put("enum", JSONArray().apply {
                                put("search")
                                put("free")
                            })
                        })
                    })
                    put("required", JSONArray().apply {
                        put("query")
                    })
                })
            })
        }
    }
    
    /**
     * 手电筒控制
     */
    private fun flashlightTool(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "flashlight")
                put("description", "控制手电筒开关")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("state", JSONObject().apply {
                            put("type", "string")
                            put("description", "手电筒状态")
                            put("enum", JSONArray().apply {
                                put("on")
                                put("off")
                            })
                        })
                    })
                    put("required", JSONArray().apply {
                        put("state")
                    })
                })
            })
        }
    }
}
