package com.musiqa.player.lyrics

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.musiqa.player.MainActivity
import com.musiqa.player.MusiqaApplication
import com.musiqa.player.audio.PlayerController
import com.musiqa.player.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

class FloatingLyricService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var playPauseButton: ImageButton? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLyrics: List<LyricLine> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PlayerController.setFloatingWindowActive(true)
        setupOverlayView()
        observePlayback()
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        // Root container: Floating Pill
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F0101014")) // 94% AMOLED black
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#44E32E27")) // subtle crimson border
            }
            background = bg
            setPadding(dp(8), dp(6), dp(8), dp(6))
            elevation = dp(12).toFloat()
        }

        // Left Disc Icon (Tapping brings Musiqa to front)
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.parseColor("#E32E27"))
            val iconBg = GradientDrawable().apply {
                setColor(Color.parseColor("#222226"))
                cornerRadius = dp(12).toFloat()
            }
            background = iconBg
            setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                val openIntent = Intent(this@FloatingLyricService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(openIntent)
            }
        }
        root.addView(iconView)

        // Center Content (Title & Synced Lyrics/Artist)
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }

        titleView = TextView(this).apply {
            text = "Musiqa"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textContainer.addView(titleView)

        subtitleView = TextView(this).apply {
            text = "♫ Ready"
            setTextColor(Color.parseColor("#9E9EA4"))
            textSize = 10f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textContainer.addView(subtitleView)
        root.addView(textContainer)

        // Previous Button
        val prevBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                PlayerController.skipToPrevious()
            }
        }
        root.addView(prevBtn)

        // Play / Pause Button
        playPauseButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#E32E27")) // Crimson
                shape = GradientDrawable.OVAL
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener {
                PlayerController.togglePlayPause()
            }
        }
        root.addView(playPauseButton)

        // Next Button
        val nextBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                PlayerController.skipToNext()
            }
        }
        root.addView(nextBtn)

        // Dedicated Close Action Deck Button ("✕")
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val closeBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A30"))
                shape = GradientDrawable.OVAL
            }
            background = closeBg
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginStart = dp(2)
            }
            isClickable = true
            isFocusable = false
            setOnClickListener {
                PlayerController.setFloatingWindowActive(false)
                stopSelf()
            }
        }
        root.addView(closeBtn)

        overlayView = root

        // Drag listener on text container to allow moving floating window without blocking button clicks
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        textContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val openIntent = Intent(this@FloatingLyricService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(openIntent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {}
    }

    private fun observePlayback() {
        // Observe current song
        serviceScope.launch {
            PlayerController.currentSong.collectLatest { song ->
                currentLyrics = if (song != null) {
                    LrcParser.findLrcForSong(song.path) ?: emptyList()
                } else {
                    emptyList()
                }

                titleView?.text = song?.title ?: "Musiqa"
                if (currentLyrics.isEmpty()) {
                    subtitleView?.text = if (song != null) "♫ ${song.artist}" else "♫ Idle"
                    subtitleView?.setTextColor(Color.parseColor("#9E9EA4"))
                }
            }
        }

        // Observe playing state
        serviceScope.launch {
            PlayerController.isPlaying.collectLatest { isPlaying ->
                playPauseButton?.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        }

        // Observe synced lyrics position
        serviceScope.launch {
            PlayerController.currentPosition.collectLatest { position ->
                if (currentLyrics.isNotEmpty()) {
                    val activeIndex = LrcParser.getActiveIndex(currentLyrics, position)
                    if (activeIndex in currentLyrics.indices) {
                        subtitleView?.text = currentLyrics[activeIndex].text
                        subtitleView?.setTextColor(Color.parseColor("#FF4D4D")) // Crimson highlight
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        PlayerController.setFloatingWindowActive(false)
        serviceScope.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
