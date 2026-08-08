package com.example.lolipet.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import com.example.lolipet.Prefs
import com.example.lolipet.R
import com.example.lolipet.ai.ChatSession
import com.example.lolipet.ai.DeepSeekClient
import com.example.lolipet.tts.PetTts
import com.example.lolipet.ui.ChatActivity
import com.example.lolipet.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 桌面宠物悬浮窗前台服务。
 * 使用 WebView 渲染 Live2D 高清模型，支持拖动、点击、抚摸、AI 对话。
 */
class PetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var petView: View? = null
    private var webView: WebView? = null
    private var bubbleText: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private var tts: PetTts? = null

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var lastTapTime = 0L
    private var isTalking = false
    private var chatJob: Job? = null

    private val density: Float
        get() = resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        tts = PetTts(this)
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (petView == null) {
            showPet()
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun showPet() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val size = (190 * density).toInt()
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.pet_overlay, null)
        petView = view
        bubbleText = view.findViewById(R.id.bubble)
        webView = view.findViewById(R.id.live2d)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = (screenHeight() * 0.25).toInt()
        params = lp

        setupWebView()
        setupTouch(view)

        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = webView ?: return
        wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.allowFileAccess = true
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 用 WebViewAssetLoader 提供 HTTPS 虚拟域，支持 fetch/XMLHttpRequest 加载 assets
        val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        wv.webViewClient = object : androidx.webkit.WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }
        }
        val url = "https://appassets.androidplatform.net/assets/live2d/pet.html?model=${Prefs.petModel}"
        wv.loadUrl(url)
    }

    private fun setupTouch(view: View) {
        view.setOnTouchListener { _, event ->
            val wm = windowManager ?: return@setOnTouchListener false
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        isDragging = true
                    }
                    if (isDragging) {
                        lp.x = initialX + dx.toInt()
                        lp.y = initialY + dy.toInt()
                        try {
                            wm.updateViewLayout(view, lp)
                        } catch (e: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        handleTap(event)
                    } else {
                        // 拖拽结束，回弹到屏幕边缘吸附
                        snapToEdge()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    /** 吸附到最近边缘 */
    private fun snapToEdge() {
        val wm = windowManager ?: return
        val view = petView ?: return
        val lp = params ?: return
        val sw = screenWidth()
        val vw = view.width
        val targetX = if (lp.x + vw / 2 < sw / 2) 0 else sw - vw
        scope.launch {
            var x = lp.x
            val step = if (targetX > lp.x) 24 else -24
            while ((step > 0 && x < targetX) || (step < 0 && x > targetX)) {
                x += step
                lp.x = x
                try {
                    wm.updateViewLayout(view, lp)
                } catch (e: Exception) {
                    break
                }
                delay(10)
            }
            lp.x = targetX
            try {
                wm.updateViewLayout(view, lp)
            } catch (e: Exception) {
            }
        }
    }

    /** 点击交互：根据位置触发反应，短按说话，双击随机反应 */
    private fun handleTap(event: MotionEvent) {
        val now = SystemClock.elapsedRealtime()
        val view = petView ?: return
        val h = view.height
        val y = event.rawY - (params?.y ?: 0)

        // 双击检测
        if (now - lastTapTime < 350) {
            lastTapTime = 0
            reactRandom()
            return
        }
        lastTapTime = now

        // 根据点击高度触发不同情绪
        if (y < h * 0.3f) {
            js("window.__api.setMood('happy')")
            showBubble("呀！你戳到我的头啦~")
        } else if (y < h * 0.7f) {
            js("window.__api.setMood('shy')")
            showBubble("嘿嘿…不要乱摸啦 >///<")
        } else {
            js("window.__api.setMood('happy')")
            showBubble("主人找我有什么事呀？")
        }

        // 触发 AI 说话
        if (y > h * 0.4f) {
            talkWithAi("主人戳了戳我")
        }
    }

    /** 随机小动作 */
    private fun reactRandom() {
        val reacts = arrayOf(
            "f01" to "嘿嘿，我最喜欢主人了！",
            "f02" to "唔…有点难过呢",
            "f03" to "呀！你吓到我了…",
            "f04" to "哼！人家才不怕呢！"
        )
        val (expr, text) = reacts.random()
        js("window.__api.setMood('happy')")
        showBubble(text)
    }

    /** 与 AI 对话并展示回复 */
    fun talkWithAi(userText: String) {
        if (chatJob?.isActive == true) return
        chatJob = scope.launch {
            showBubble("让我想想…")
            js("window.__api.startTalking()")
            isTalking = true
            try {
                ChatSession.addUser(userText)
                var full = ""
                val reply = DeepSeekClient.chatStream(ChatSession.getMessages()) { delta ->
                    full += delta
                    showBubble(full)
                }
                if (reply.isBlank()) {
                    replyState()
                    showBubble("唔…我刚刚走神了，再说一次好不好？")
                    return@launch
                }
                ChatSession.addAssistant(reply)
                replyState()
                showBubble(reply)
                tts?.speak(reply)
            } catch (e: Exception) {
                replyState()
                showBubble("网络好像不太好的样子…请检查设置里的 API Key 哦")
            }
        }
    }

    private fun replyState() {
        isTalking = false
        js("window.__api.stopTalking()")
    }

    private fun js(code: String) {
        webView?.post {
            webView?.evaluateJavascript(code, null)
        }
    }

    private fun showBubble(text: String) {
        bubbleText?.post {
            bubbleText?.text = text
            bubbleText?.visibility = View.VISIBLE
        }
        scope.launch {
            delay(if (isTalking) 8000L else 4000L)
            if (!isTalking) {
                bubbleText?.post {
                    bubbleText?.visibility = View.GONE
                }
            }
        }
    }

    private fun screenWidth(): Int =
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.width

    private fun screenHeight(): Int =
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.height

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠常驻",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, ChatActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val settingsIntent = Intent(this, MainActivity::class.java)
        val pendingSettings = PendingIntent.getActivity(
            this, 1, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("小萝莉在这里")
            .setContentText("点击打开聊天，和小萝莉互动吧")
            .setContentIntent(pendingOpen)
            .addAction(0, "设置", pendingSettings)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        chatJob?.cancel()
        tts?.shutdown()
        petView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
            }
        }
        webView?.destroy()
        Prefs.isRunning = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.lolipet.STOP"
        private const val CHANNEL_ID = "loli_pet_channel"

        fun start(context: Context) {
            val i = Intent(context, PetService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, PetService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
