# KotlinDSLMemo--Android
KotlinDSLMemo 是一款基于 Jetpack Compose 的 Android 备忘录类应用，核心功能包括：笔记记录、账本管理（多账本+流水）、记忆卡片、闹钟提醒、AI 智能体聊天（支持 DeepSeek/智谱 GLM/阿里云 DashScope 三大供应商及多模态附件识别）、基于 ReAct 范式与无障碍服务的手机自动控制（用户一句话即可完成多步任务）、影视搜索与观看记录管理，以及内置集成广告拦截、反指纹、反钓鱼、视频播放隔离四大能力的浏览器。

---

## 重要：隐私与凭据说明

本项目出于安全考虑，**所有个人敏感信息（邮箱、API Key、邮箱授权码等）均已替换为中文占位符**，克隆后无法直接使用 AI 聊天/日志邮件功能，必须按本文说明填入真实凭据。

### 已替换的占位符一览

| 占位符 | 所在文件 | 真实凭据来源 |
|---|---|---|
| `你的DeepSeek API Key` | `app/src/main/java/com/example/memo/data/AppConfig.kt:49` | DeepSeek 控制台 |
| `你的智谱 GLM API Key ID` | `app/src/main/java/com/example/memo/data/AppConfig.kt:53` | 智谱 BigModel 控制台 |
| `你的智谱 GLM Secret` | `app/src/main/java/com/example/memo/data/AppConfig.kt:54` | 智谱 BigModel 控制台 |
| `你的阿里云 DashScope API Key` | `app/src/main/java/com/example/memo/data/AppConfig.kt:60` | 阿里云百炼控制台 |
| `你的发件人邮箱` | `app/src/main/java/com/example/memo/util/LogSyncScheduler.kt:75` | 任意支持 SMTP 的邮箱 |
| `你的邮箱授权码` | `app/src/main/java/com/example/memo/util/LogSyncScheduler.kt:76` | 邮箱后台生成 |
| `你的收件人邮箱` | `app/src/main/java/com/example/memo/util/LogSyncScheduler.kt:77` | 接收崩溃/日志邮件的邮箱 |

---

## 修改方式（推荐：运行时配置）

### 方式一：通过应用内设置界面（推荐）

绝大多数凭据**不需要修改源码**，首次启动后在应用内填写：

1. 打开 App → **设置**
2. 切换到对应的供应商标签页：
   - **DeepSeek** → 填入 `API Key`
   - **智谱 GLM** → 分别填入 `API Key ID` 与 `签名密钥 secret`
   - **阿里云 DashScope** → 填入 `API Key`
3. **日志同步** 入口（在二级设置窗口中） → 填入发件人邮箱 / 授权码 / 收件人邮箱 / SMTP 服务器（默认 `smtp.qq.com:465`）
4. 凭据会保存到本地 SharedPreferences，下次启动自动加载

> 优点：升级代码时不会覆盖你的凭据；可以随时切换不同供应商。

### 方式二：直接修改源码默认值

如确需修改源码（不推荐，会随 `git pull` 丢失）：

1. 打开 [AppConfig.kt](app/src/main/java/com/example/memo/data/AppConfig.kt)
2. 把第 49/53/54/60 行的中文占位符替换为真实凭据
3. 打开 [LogSyncScheduler.kt](app/src/main/java/com/example/memo/util/LogSyncScheduler.kt)
4. 把第 75/76/77 行的中文占位符替换为真实邮箱与授权码
5. 重新编译：`./gradlew assembleDebug`

---

## 各供应商 API Key 申请地址

| 供应商 | 控制台 | 备注 |
|---|---|---|
| DeepSeek | https://platform.deepseek.com/api_keys | 以 `sk-` 开头 |
| 智谱 GLM | https://bigmodel.cn/usercenter/apikeys | 需同时拿 **API Key ID** 与 **Secret**，格式 `{id}.{secret}` |
| 阿里云 DashScope | https://dashscope.console.aliyun.com/apiKey | 以 `sk-` 开头，OpenAI 兼容模式 |
| QQ 邮箱授权码 | https://service.mail.qq.com/detail/0/75 | 开启 SMTP 服务后生成，**非登录密码** |

---

## 注意事项

### 1. 替换后的占位符无法直接运行

`DEFAULT_*_API_KEY` 字段是常量字符串，编译时直接打包进 APK。占位符状态下首次启动会因：

```
Authorization: Bearer 你的DeepSeek API Key
```

直接返回 401。**必须**先通过方式一在设置界面填入真实凭据。

### 2. 邮箱授权码 ≠ 登录密码

QQ/163/Gmail 等邮箱的"授权码"是专门为第三方登录（如 SMTP、IMAP）生成的临时密码，**与登录密码不同**。在邮箱后台开启 SMTP/IMAP 服务后单独生成。

### 3. SMTP 默认值

| 邮箱 | SMTP 服务器 | 端口 | 加密 |
|---|---|---|---|
| QQ 邮箱 | smtp.qq.com | 465 | SSL |
| 163 邮箱 | smtp.163.com | 465/994 | SSL |
| Gmail | smtp.gmail.com | 465 | SSL |

如需修改 SMTP 主机/端口，前往 **设置 → 日志同步**。

注意：发件人邮箱和收件人邮箱可以是同一个

### 4. 智谱 GLM 的 Key 格式

智谱 API 完整 Key 由 **API Key ID + `.` + Secret** 拼接而成.

应用内部会自动按 `id.secret` 拼接，无需手动拼。

### 5. 阿里云模型兼容性

默认 `qwen3.5-omni-plus-2026-03-15`（全模态）仅在 **OpenAI 兼容模式** 下可用。旧版 `liveportrait` 模型已废弃，App 启动时会自动迁移。

---

## 项目功能一览

- 笔记 / 账本 / 交易流水 / 记忆卡片 / 闹钟
- AI 智能体（基于 ReAct 范式，支持 DeepSeek/智谱 GLM/阿里云 DashScope）
- 多模态附件（图片/PDF/Word/Excel/PPT/TXT）
- 手机自动控制（无障碍 + UI 检测 + LLM 决策）
- 内置浏览器（广告拦截 / 反指纹 / 反钓鱼 / 视频播放隔离）
- 影视搜索 + 观看记录
- 本地日志落盘 + 邮件同步

更多技术细节见源码内注释。

---

## 如何使用AI助手？
## 一、入口

在 App 任意主界面（笔记 / 账本 / 记忆 / 闹钟），点击顶部 AI 浮动按钮或底部菜单的"AI 助手"图标即可唤起 `AIChatDialog` 对话框，输入自然语言即可。

> ⚠️ 使用前需先在 **设置** 中配置 LLM 供应商 API Key（DeepSeek / 智谱 GLM / 阿里云 DashScope 任选其一）。

---

## 二、核心功能详解

### 1. 笔记记录

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 记一下：明天下午 3 点开组会 | `create_note` | 新增一条笔记 |
| 帮我记录"Redis 缓存击穿的三种解决方案" | `create_note` | 新增技术笔记 |
| 搜一下我之前写的关于 Kotlin 协程的笔记 | `search_note` | 返回相关笔记列表 |
| 把那条关于 XXX 的笔记改成 XXX | `update_note` | 修改指定笔记 |
| 删除那条 XXX 笔记 | `delete_note` | 删除笔记 |

### 2. 账本管理（多账本 + 流水）

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 创建一个新账本叫"旅行基金" | `create_ledger` | 新建账本 |
| 在日常开销里记一笔：午饭花了 35 元 | `create_transaction` | 添加支出流水 |
| 在旅行基金里记一笔收入 5000 元 | `create_transaction` | 添加收入流水 |
| 看看我这个月日常开销花了多少 | `query_ledger` | 返回账本+流水汇总 |
| 查询旅行基金 | `query_ledger` | 返回该账本交易记录 |

> 系统会自动按"主账本 + 细分账本"双层归类（如"日常开销 → 餐饮 / 交通 / 购物"），用户无需关心。

### 3. 记忆卡片（长期记忆）

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 记住：我对青霉素过敏 | `find_similar_memories` → `create_memory` | 写入长期记忆 |
| 我之前说过我住在哪里来着？ | `search_memory` | 检索记忆 |
| 我对花粉过敏（已存在相似记忆） | `update_memory` | 更新而非新增 |
| 找一下所有跟"过敏"相关的记忆 | `search_memory` | 关键词检索 |

> AI 会先调用 `find_similar_memories` 判断是否需要新建，避免重复。

### 4. 闹钟提醒

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 设个闹钟，明早 7 点叫我起床 | `create_alarm` | 创建一次性闹钟 |
| 明天下午 5 点提醒我去接孩子 | `create_event`（系统日历）或 `create_alarm` | 创建日程 |
| 把起床那个闹钟改成 7:30 | `update_alarm` | 修改闹钟 |
| 我有哪些闹钟 | `query_alarm` | 列出全部闹钟 |
| 删除那个 7 点的起床闹钟 | `delete_alarm` | 删除闹钟 |

> 默认行为：不静音 + 响铃后自动删除 + 开启贪睡，除非用户明确说要安静 / 不重复。

### 5. 联系人 / 拨号

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 新建联系人：张三，电话 13800001111 | `create_contact` | 写入通讯录 |
| 找一下李四的电话 | `search_contact` | 返回匹配联系人 |
| 13800001111 是谁 | `search_phone` | 反查联系人 |
| 给妈妈打电话 | `call_phone` | 直接拨号 |
| 删除联系人王五 | `delete_contact` | 删除 |

---

## 三、看电影 / 电视剧

本 App 集成了"**影视推荐 + 待看清单 + 观看记录 + 内置浏览器播放**"完整闭环，所有能力都可通过 AI 一句话触发。

### 1. 影视推荐

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 推荐部电影 | `query_pending_count` + `show_movie_recommendations` | 弹窗展示 5 部推荐，可刷新换批 |
| 有什么好看的电视剧 | 同上 | 弹窗展示 |
| 我想看科幻片 | `query_pending_count` + `show_movie_recommendations` | 按类型推荐 |
| 待看列表里都有啥 | `show_pending_list` | 展示待看清单 |
| 我最近看了什么 | `show_recent_watched` | 展示最近 20 条观看记录 |

> 触发条件：用户表达"我想看 / 推荐 / 来一部"等模糊观影意图时，AI 才会调用。
> 如果待看列表 ≥ 15 部，AI 优先展示待看清单而非继续推荐。

### 2. 搜索并播放具体剧名（核心）

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 播放"漫长的季节" | `play_in_browser` | 内置浏览器打开 freeokk.pro 搜索页 |
| 我想看"庆余年 2" | `play_in_browser` | 内置浏览器打开 freeokk.pro 搜索页 |
| 搜一下"三体"电视剧 | `play_in_browser` | 内置浏览器打开 freeokk.pro 搜索页 |
| 找部电影"肖申克的救赎" | `play_in_browser` | 内置浏览器打开 freeokk.pro 搜索页 |

> ⚠️ **严格使用规则**：
> - **仅当用户说出具体剧名 / 电影名**时才会触发
> - **严禁**用于金价 / 银价 / 股价 / 天气 / 新闻 / 生活服务 / 知识问答
> - 触发词白名单黑名单双重校验（详见 `WatchListManager.isLikelyMovieTitle`）

### 3. 观看记录管理

| 用户说的话 | AI 内部动作 | 实际效果 |
|---|---|---|
| 把"三体"标记为已看 | `mark_watched` | 加入已看列表 |
| 我看过的电影里有国产片吗 | `search_watched` | 检索已看历史 |
| 清空我的观看记录 | `clear_watched` | 全部清空 |

> 观看记录会保存到本地，避免重复推荐。

### 4. 多步影视任务示例

> **用户**：我想看"三体"，如果我看过就告诉我

**AI 思考链**：
1. `search_watched` 查询"三体"是否已看
2. 若已看 → `show_recent_watched` 弹窗展示
3. 若未看 → `play_in_browser` 打开播放页

> **用户**：推荐几部科幻电影，把感兴趣的加到待看清单

**AI 思考链**：
1. `query_pending_count` 检查待看数量
2. `show_movie_recommendations` 弹窗展示 5 部
3. 用户点击"加入待看"后调用 `add_to_pending`

> **用户**：在爱奇艺搜一下"长相思"

**AI 思考链**：
1. `open_app` 打开爱奇艺（多步任务时此步放最后）
2. `get_screen_ui` 获取当前界面
3. `click_element` 点击搜索框
4. `input_text` 输入"长相思"
5. `click_element` 点击搜索按钮

### 5. 内置浏览器能力（影视场景）

内置浏览器在影视站（freeokk.pro 等）具备：
- **广告拦截**（AdBlocker）：网络层 + 美化层 + DOM 兜底
- **反指纹 / 防跟踪**（PrivacyProtector）
- **反钓鱼 / 安全盾牌**（SecurityShield）
- **视频播放隔离**（PlayerIsolator）：白名单重建 + 0.8s 兜底定时器
- **悬浮球 / 全屏播放**

---

## 四、一句话多步任务（智能体核心能力）

> 核心场景：用户只需要说一句话，AI 自动规划多步串行执行。

| 用户说的话 | AI 自动执行步骤 |
|---|---|
| 明天下午 3 点开组会，记到笔记里再设个闹钟提前 10 分钟提醒我 | ① `create_note` ② `create_alarm` |
| 搜索我之前关于"机器学习"的笔记，并把它们合并成一条总结 | ① `search_note` ② `update_note` / `create_note` |
| 创建一个"购物清单"账本，然后记一笔：买牛奶 30 元 | ① `create_ledger` ② `create_transaction` |
| 记住我对海鲜过敏，然后查一下今晚去哪家海鲜餐厅合适 | ① `find_similar_memories` + `create_memory` ② `open_app` 打开美团 / 大众点评 |
| 推荐几部科幻片，感兴趣的我加到待看 | ① `query_pending_count` ② `show_movie_recommendations` |
| 播放"漫长的季节"，如果没看过就帮我搜一下剧情简介 | ① `search_watched` ② `play_in_browser` 或 `search_note` |

---

## 五、手机自动控制（需开启无障碍服务）

| 用户说的话 | AI 内部动作 |
|---|---|
| 打开微信 | `open_app`（直接启动） |
| 我想刷抖音 | `recommend_app`（弹出选择框） |
| 帮我在淘宝搜一下蓝牙耳机 | ① `get_screen_ui` ② `click_element` / `input_text` / `swipe_screen` 多步 |
| 打开美团 | `open_app` |

> 打开 App 的动作永远放在多步任务的**最后**执行，避免打断前置操作。
> 执行过程中**点击屏幕立即终止**（安全机制）；可手动点"停止"按钮取消。

---

## 六、系统能力

| 用户说的话 | AI 内部动作 |
|---|---|
| 打开手电筒 / 关手电筒 | `flashlight` |
| 现在几点了 | `get_current_time` |
| 提醒我晚上 9 点做作业 | `create_event` + `create_alarm` 联动 |

---

## 七、使用要点

1. **自然语言即可**：不需要死记触发词，正常说话 AI 就会拆解任务。
2. **多步任务自动执行**：例如"先记笔记再设闹钟"AI 会按顺序串起来。
3. **打开 App 永远在最后**：避免一边操作一边被新启动的 App 打断。
4. **安全机制**：
   - 执行过程中**点击屏幕立即终止**
   - 可随时点"停止"取消
   - 删除类操作（联系人 / 笔记 / 账本 / 闹钟）会**先弹确认框**
5. **失败回退**：精确闹钟被拒时自动回退为非精确闹钟；权限不足时给出友好提示。
6. **影视站触发白名单**：`play_in_browser` 仅在说出具体剧名时触发，含"金价 / 银价 / 股价 / 天气 / 新闻"等关键词会被拦截。

---

## 八、触发词速查表（仅供调试，普通用户无需记忆）

| 功能 | 触发词 |
|---|---|
| 闹钟 | 设个闹钟 / 几点叫我 / 定个闹钟 / 设闹钟 |
| 日程 | 提醒我 / 记个日程 / 安排一下 |
| 笔记 | 记一下 / 记录 / 备忘 / 笔记 |
| 账本 | 记账 / 花了 / 收入 / 支出 |
| 记忆 | 记住 / 别忘了 / 我对…过敏 |
| 影视 | 推荐 / 我想看 / 来一部 / 播放 / 搜一下 |
| 拨号 | 给 XX 打电话 / 拨打 |
| App | 打开 XX / 启动 XX |
| 手电筒 | 打开手电筒 / 关手电筒 |

---

## 九、典型多步任务完整示例

### 示例 1：会议安排

> **用户**：明天下午 3 点开组会，把要点记到笔记里，再设个闹钟提前 10 分钟提醒我

**AI 执行链**：
1. `create_note`（内容"明天下午 3 点开组会"）
2. `create_alarm`（14:50 提醒"组会"）

**AI 回复**：
> ✅ 已创建笔记"明天下午 3 点开组会"
> ✅ 已创建闹钟：每天 14:50 提醒"组会"（响铃后自动删除，开启贪睡）
> 任务完成 ✨

### 示例 2：过敏记录

> **用户**：记住我对青霉素过敏，以后别再推荐含青霉素的药

**AI 执行链**：
1. `find_similar_memories` 检查重复
2. `create_memory` 写入长期记忆

**AI 回复**：
> ✅ 已记录：对青霉素过敏
> 以后推荐用药时会避开此类成分 💊

### 示例 3：看剧

> **用户**：推荐几部最近热播的国产剧

**AI 执行链**：
1. `query_pending_count` 检查待看清单
2. `show_movie_recommendations` 弹窗展示 5 部
3. 用户点击"加入待看"→ `add_to_pending`

### 示例 4：播放具体剧

> **用户**：播放"庆余年 2"

**AI 执行链**：
1. `search_watched` 检查是否已看
2. `play_in_browser` 内置浏览器打开 freeokk 搜索页
3. `PlayerIsolator` 重建播放区，去除广告 / 弹窗

### 示例 5：多步混合任务

> **用户**：我明天要去杭州出差 3 天，帮我建一个"杭州出差"账本，记住出差日期，再设个闹钟明早 6 点叫我起床

**AI 执行链**：
1. `find_similar_memories` + `create_memory`（记忆：明天出差杭州 3 天）
2. `create_ledger`（"杭州出差"账本）
3. `create_alarm`（明早 6:00 起床闹钟）

---

## 十、故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| AI 一直转圈不出结果 | API Key 未配置或余额不足 | 设置 → 切换供应商 / 检查 Key |
| 401 / 403 错误 | Key 失效或被撤销 | 控制台重新生成 |
| 手机自动控制无反应 | 未开启无障碍服务 | 系统设置 → 无障碍 → 开启本应用 |
| 推荐电影总是重复 | 观看记录未更新 | 看完后告诉 AI"标记 XXX 为已看" |
| `play_in_browser` 不触发 | 触发词被黑名单拦截（如"金价"） | 改用明确剧名 |
| 闹钟不响 | 未授予 SCHEDULE_EXACT_ALARM | 系统设置 → 闹钟与提醒 → 允许 |
