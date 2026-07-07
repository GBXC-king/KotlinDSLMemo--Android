package com.example.memo.autoui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.memo.R
import com.example.memo.agent.AgentConfig
import com.example.memo.agent.AgentEngine
import com.example.memo.agent.AgentStepRecord
import com.example.memo.agent.AgentToolRegistry
import com.example.memo.agent.PendingAppSelection
import com.example.memo.autoui.engine.UiAutoController
import com.example.memo.data.Note
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.NoteViewModel
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingBallService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_ball_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingBall: View? = null
    private var inputPanel: View? = null
    private var isInputPanelShowing = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentAgentEngine: AgentEngine? = null

    private var resultTextView: TextView? = null
    private var sendButton: Button? = null
    private var editText: EditText? = null
    private var pendingSendText: String? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        showFloatingBall()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        UiAutoController.clearOnStopCallback()
        removeFloatingBall()
        removeInputPanel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI助手悬浮球，可随时唤起AI对话"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI助手悬浮球")
            .setContentText("点击悬浮球可随时唤起AI助手")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFloatingBall() {
        if (floatingBall != null) return

        val ballSize = dpToPx(56f)
        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = resources.displayMetrics.heightPixels / 3

        val ball = createFloatingBallView()
        floatingBall = ball
        windowManager.addView(ball, params)
    }

    private fun createFloatingBallView(): View {
        val ball = Button(this).apply {
            text = "AI"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(android.graphics.Color.WHITE)
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.OVAL
            shape.setColor(0xFFE91E63.toInt())
            shape.setStroke(dpToPx(2f), 0xFFFFFFFF.toInt())
            background = shape
        }

        ball.setOnTouchListener { view, event ->
            val params = view.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx < dpToPx(5f) && dy < dpToPx(5f)) {
                        toggleInputPanel()
                    }
                    true
                }
                else -> false
            }
        }

        return ball
    }

    private fun toggleInputPanel() {
        if (isInputPanelShowing) {
            removeInputPanel()
        } else {
            showInputPanel()
        }
    }

    private fun showInputPanel() {
        if (inputPanel != null) return

        val panelWidth = (resources.displayMetrics.widthPixels * 0.85f).toInt()
        val panelHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()

        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        val panel = createInputPanelView()
        inputPanel = panel
        windowManager.addView(panel, params)
        isInputPanelShowing = true
    }

    private fun createInputPanelView(): View {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            val radius = dpToPx(16f).toFloat()
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            shape.setColor(0xFFFFFFFF.toInt())
            shape.cornerRadius = radius
            background = shape
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }

        val titleText = TextView(this).apply {
            text = "AI 助手"
            textSize = 18f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, dpToPx(12f))
        }

        val resultScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val resultText = TextView(this).apply {
            text = "请输入您的指令，AI将自动帮您操作手机\n\n例如：\n• 帮我打开爱奇艺找免费电视剧\n• 帮我在设置里找关于手机\n• 打开微信给张三发消息"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setLineSpacing(0f, 1.5f)
        }
        resultTextView = resultText
        resultScroll.addView(resultText)

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(12f), 0, 0)
        }

        val editText = EditText(this).apply {
            hint = "输入指令..."
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            isFocusable = true
            isFocusableInTouchMode = true
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    val text = this.text.toString()
                    if (text.isNotBlank()) {
                        pendingSendText = text
                        sendButton?.performClick()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            shape.setColor(0xFFF5F5F5.toInt())
            shape.cornerRadius = dpToPx(8f).toFloat()
            background = shape
        }
        this.editText = editText

        val sendButton = Button(this).apply {
            text = "发送"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(8f)
            }
            setOnClickListener {
                val textToSend = pendingSendText ?: editText?.text?.toString()
                if (!textToSend.isNullOrBlank()) {
                    pendingSendText = null
                    editText?.text?.clear()
                    executeAgentTaskDirect(textToSend)
                }
            }
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            shape.setColor(0xFF2962FF.toInt())
            shape.cornerRadius = dpToPx(8f).toFloat()
            background = shape
        }
        this.sendButton = sendButton

        val stopButton = Button(this).apply {
            text = "停止"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            tag = "stopButton"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(8f)
            }
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            shape.setColor(0xFFFF5252.toInt())
            shape.cornerRadius = dpToPx(8f).toFloat()
            background = shape
            visibility = View.GONE
        }

        val closeButton = Button(this).apply {
            text = "关闭"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(8f)
            }
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            shape.setColor(0xFFEEEEEE.toInt())
            shape.cornerRadius = dpToPx(8f).toFloat()
            background = shape
        }

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                executeAgentTask(text, stopButton)
            }
        }

        stopButton.setOnClickListener {
            currentAgentEngine?.cancel()
            UiAutoController.stopOperation("用户点击停止")
            resultTextView?.text = "${resultTextView?.text}\n\n⚠️ 操作已被用户终止"
            stopButton.visibility = View.GONE
            sendButton?.isEnabled = true
            editText.isEnabled = true
        }

        closeButton.setOnClickListener {
            currentAgentEngine?.cancel()
            UiAutoController.stopOperation("用户关闭面板")
            removeInputPanel()
        }

        inputLayout.addView(editText)
        inputLayout.addView(sendButton)
        inputLayout.addView(stopButton)
        inputLayout.addView(closeButton)

        mainLayout.addView(titleText)
        mainLayout.addView(resultScroll)
        mainLayout.addView(inputLayout)

        return mainLayout
    }

    private fun executeAgentTask(userInput: String, stopButton: Button) {
        val editText = this.editText ?: return
        val sendButton = this.sendButton ?: return

        editText.isEnabled = false
        sendButton.isEnabled = false
        stopButton.visibility = View.VISIBLE
        resultTextView?.text = "正在处理：$userInput\n请稍候..."

        UiAutoController.startOperation()

        val app = application as Application
        val noteViewModel = NoteViewModel(app)
        val ledgerViewModel = LedgerViewModel(app)
        val memoryViewModel = MemoryViewModel(app)

        serviceScope.launch {
            val autoControlEnabled = AgentConfig.getAutoControlEnabled(this@FloatingBallService)
            val toolRegistry = AgentToolRegistry(
                context = this@FloatingBallService,
                ledgerViewModel = ledgerViewModel,
                noteViewModel = noteViewModel,
                memoryViewModel = memoryViewModel,
                onCreateNote = { title, content ->
                    val note = Note(title = title, content = content, timestamp = System.currentTimeMillis())
                    noteViewModel.addNote(note)
                },
                onAppSelectionNeeded = { selection ->
                    resultTextView?.post {
                        resultTextView?.text = "${resultTextView?.text}\n\n找到 ${selection.apps.size} 个匹配的应用，请在弹窗中选择（功能完善中）"
                    }
                },
                autoControlEnabled = autoControlEnabled
            )

            val agentEngine = AgentEngine(toolRegistry, maxSteps = 15)
            currentAgentEngine = agentEngine

            UiAutoController.setOnStopCallback {
                agentEngine.cancel()
            }
            agentEngine.run(
                userInput = userInput,
                callback = object : AgentEngine.AgentCallback {
                    override fun onStepUpdate(step: AgentStepRecord) {
                        resultTextView?.post {
                            val sb = StringBuilder()
                            sb.append("【步骤 ${step.stepIndex}】\n")
                            if (!step.thought.isNullOrBlank()) {
                                sb.append("思考：${step.thought}\n")
                            }
                            if (!step.action.isNullOrBlank()) {
                                sb.append("动作：${step.action}\n")
                                if (!step.actionInput.isNullOrEmpty()) {
                                    sb.append("参数：${step.actionInput}\n")
                                }
                            }
                            if (!step.observation.isNullOrBlank()) {
                                val obs = if (step.observation.length > 200) {
                                    step.observation.take(200) + "..."
                                } else {
                                    step.observation
                                }
                                sb.append("结果：$obs\n")
                            }
                            sb.append("\n")
                            resultTextView?.text = "${resultTextView?.text}\n$sb"
                        }
                    }

                    override fun onFinalAnswer(answer: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n✅ 任务完成：\n$answer"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                            editText.text.clear()
                        }
                        UiAutoController.stopOperation("任务完成")
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }

                    override fun onError(error: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n❌ 出错了：$error"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                        }
                        UiAutoController.stopOperation("出错：$error")
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }

                    override fun onStopped(reason: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n⚠️ $reason"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                        }
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }
                }
            )
        }
    }

    fun executeAgentTaskDirect(userInput: String) {
        val editText = this.editText ?: return
        val sendButton = this.sendButton ?: return
        val stopButton = inputPanel?.let {
            it.findViewWithTag<android.widget.Button>("stopButton")
        } ?: return

        editText.isEnabled = false
        sendButton.isEnabled = false
        stopButton.visibility = View.VISIBLE
        resultTextView?.text = "正在处理：$userInput\n请稍候..."

        UiAutoController.startOperation()

        val app = application as Application
        val noteViewModel = NoteViewModel(app)
        val ledgerViewModel = LedgerViewModel(app)
        val memoryViewModel = MemoryViewModel(app)

        serviceScope.launch {
            val autoControlEnabled = AgentConfig.getAutoControlEnabled(this@FloatingBallService)
            val toolRegistry = AgentToolRegistry(
                context = this@FloatingBallService,
                ledgerViewModel = ledgerViewModel,
                noteViewModel = noteViewModel,
                memoryViewModel = memoryViewModel,
                onCreateNote = { title, content ->
                    val note = Note(title = title, content = content, timestamp = System.currentTimeMillis())
                    noteViewModel.addNote(note)
                },
                onAppSelectionNeeded = { selection ->
                    resultTextView?.post {
                        resultTextView?.text = "${resultTextView?.text}\n\n找到 ${selection.apps.size} 个匹配的应用，请在弹窗中选择（功能完善中）"
                    }
                },
                autoControlEnabled = autoControlEnabled
            )

            val agentEngine = AgentEngine(toolRegistry, maxSteps = 15)
            currentAgentEngine = agentEngine

            UiAutoController.setOnStopCallback {
                agentEngine.cancel()
            }
            agentEngine.run(
                userInput = userInput,
                callback = object : AgentEngine.AgentCallback {
                    override fun onStepUpdate(step: AgentStepRecord) {
                        resultTextView?.post {
                            val sb = StringBuilder()
                            sb.append("【步骤 ${step.stepIndex}】\n")
                            if (!step.thought.isNullOrBlank()) {
                                sb.append("思考：${step.thought}\n")
                            }
                            if (!step.action.isNullOrBlank()) {
                                sb.append("动作：${step.action}\n")
                                if (!step.actionInput.isNullOrEmpty()) {
                                    sb.append("参数：${step.actionInput}\n")
                                }
                            }
                            if (!step.observation.isNullOrBlank()) {
                                val obs = if (step.observation.length > 200) {
                                    step.observation.take(200) + "..."
                                } else {
                                    step.observation
                                }
                                sb.append("结果：$obs\n")
                            }
                            sb.append("\n")
                            resultTextView?.text = "${resultTextView?.text}\n$sb"
                        }
                    }

                    override fun onFinalAnswer(answer: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n✅ 任务完成：\n$answer"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                        }
                        UiAutoController.stopOperation("任务完成")
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }

                    override fun onError(error: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n❌ 出错了：$error"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                        }
                        UiAutoController.stopOperation("出错：$error")
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }

                    override fun onStopped(reason: String) {
                        resultTextView?.post {
                            resultTextView?.text = "${resultTextView?.text}\n\n⚠️ $reason"
                            editText.isEnabled = true
                            sendButton.isEnabled = true
                            stopButton.visibility = View.GONE
                        }
                        UiAutoController.clearOnStopCallback()
                        currentAgentEngine = null
                    }
                }
            )
        }
    }

    private fun removeInputPanel() {
        inputPanel?.let {
            windowManager.removeView(it)
            inputPanel = null
        }
        isInputPanelShowing = false
        resultTextView = null
        sendButton = null
        editText = null
    }

    private fun removeFloatingBall() {
        floatingBall?.let {
            windowManager.removeView(it)
            floatingBall = null
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
