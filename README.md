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
