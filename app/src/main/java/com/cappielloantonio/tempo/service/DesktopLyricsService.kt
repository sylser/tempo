package com.cappielloantonio.tempo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.repository.SongRepository
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.subsonic.models.Line
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics
import com.cappielloantonio.tempo.util.Preferences
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class DesktopLyricsService : Service() {
    companion object {
        private const val TAG = "DesktopLyricsService"
        private const val CHANNEL_ID = "desktop_lyrics_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_UPDATE_LYRICS = "com.cappielloantonio.tempo.action.UPDATE_LYRICS"
        const val ACTION_UPDATE_SETTINGS = "com.cappielloantonio.tempo.action.UPDATE_SETTINGS"
        const val ACTION_SONG_CHANGED = "com.cappielloantonio.tempo.action.SONG_CHANGED"
        const val EXTRA_PREV_LYRIC = "com.cappielloantonio.tempo.extra.PREV_LYRIC"
        const val EXTRA_CURRENT_LYRIC = "com.cappielloantonio.tempo.extra.CURRENT_LYRIC"
        const val EXTRA_NEXT_LYRIC = "com.cappielloantonio.tempo.extra.NEXT_LYRIC"
        const val EXTRA_SONG_ID = "com.cappielloantonio.tempo.extra.SONG_ID"
    }

    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var currentLyrics: LyricsList? = null
    private var currentLineIndex = -1
    private val lyricHandler = Handler(Looper.getMainLooper())
    private var isLyricTickerActive = false
    private var lastUpdatePosition: Long = -1
    private val positionThreshold: Long = 50 // Only update if position changes by more than 50ms
    private val openRepository = com.cappielloantonio.tempo.repository.OpenRepository()
    
    // Flag to indicate if we're using self-driven lyrics mode
    private var isSelfDrivenMode = false
    
    // Cache the current line index to avoid repeated searches
    private var cachedCurrentLineIndex = -1

    private lateinit var windowManager: WindowManager
    private lateinit var lyricsView: View
    private lateinit var currentLyricsTextView: TextView
    private lateinit var prevLyricsTextView: TextView
    private lateinit var nextLyricsTextView: TextView
    
    // Drag and lock functionality
    private var isDragging = false
    private var isLocked = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val windowParams by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Add this flag to allow system keys to work
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Add this flag to allow system keys to work
                PixelFormat.TRANSLUCENT
            )
        }.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100 // Default position from bottom
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeLyricsView()
        initializeMediaController()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action != null) {
                when (action) {
                    ACTION_UPDATE_LYRICS -> {
                        // Only process external lyrics updates if not in self-driven mode
                        if (!isSelfDrivenMode) {
                            val prevLyric = intent.getStringExtra(EXTRA_PREV_LYRIC)
                            val currentLyric = intent.getStringExtra(EXTRA_CURRENT_LYRIC)
                            val nextLyric = intent.getStringExtra(EXTRA_NEXT_LYRIC)
                            updateLyrics(prevLyric, currentLyric, nextLyric)
                        }
                    }
                    ACTION_SONG_CHANGED -> {
                        val songId = intent.getStringExtra(EXTRA_SONG_ID)
                        if (songId != null) {
                            handleSongChange(songId)
                        }
                    }
                    ACTION_UPDATE_SETTINGS -> {
                        updateFontSizes()
                        updateOpacity()
                        updateFontColor()
                        updateLockState()
                    }
                }
            }
        }
        // Return START_NOT_STICKY so the service won't be automatically restarted by the system
        // This ensures that when the user turns off desktop lyrics, it stays off
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleSongChange(songId: String) {
        // Activate self-driven mode
        isSelfDrivenMode = true
        
        // Stop the old ticker immediately to prevent further updates
        lyricHandler.removeCallbacks(lyricRunnable)

        // Clear lyrics state completely
        currentLineIndex = -1
        currentLyrics = null
        cachedCurrentLineIndex = -1 // Reset cached index for new song
        lastUpdatePosition = -1 // Reset position tracking for new song
        lastLyric = null // Reset last lyric to ensure proper refresh

        // Clear the display and reset the internal lastLyric state by calling update with null
        updateLyrics(null, null, null)

        // Small delay to ensure old lyrics are cleared before loading new ones
        lyricHandler.postDelayed({
            // Load new song lyrics after clearing old ones
            loadLyricsForSong(songId)
        }, 200) // 200ms delay to ensure smooth transition
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: DesktopLyricsService is being destroyed")
        
        // Clean up the lyrics ticker
        lyricHandler.removeCallbacks(lyricRunnable)
        
        // Release MediaController
        try {
            val future = mediaControllerFuture
            if (future != null) {
                MediaController.releaseFuture(future)
                mediaController = null
                mediaControllerFuture = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media controller: ${e.message}")
        }
        
        // Reset self-driven mode flag
        isSelfDrivenMode = false
        
        // Reset cached indices and positions
        cachedCurrentLineIndex = -1
        lastUpdatePosition = -1
        lastLyric = null

        try {
            // Check if windowManager and lyricsView are initialized before removing
            if (::windowManager.isInitialized) {
                Log.d(TAG, "onDestroy: windowManager is initialized")
                if (::lyricsView.isInitialized) {
                    Log.d(TAG, "onDestroy: lyricsView is initialized, removing from window manager")
                    windowManager.removeView(lyricsView)
                    Log.d(TAG, "onDestroy: Successfully removed lyrics view")
                } else {
                    Log.d(TAG, "onDestroy: lyricsView is not initialized")
                }
            } else {
                Log.d(TAG, "onDestroy: windowManager is not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error removing lyrics view: ${e.message}")
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeMediaController() {
        try {
            val sessionToken = SessionToken(this, ComponentName(this, MediaService::class.java))
            mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            mediaControllerFuture?.addListener({
                try {
                    mediaController = mediaControllerFuture?.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create media controller", e)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media controller", e)
        }
    }

    private fun loadLyricsForSong(songId: String) {
        // Load lyrics for the given song ID
        openRepository.getLyricsBySongId(songId).observeForever { lyricsList ->
            currentLyrics = lyricsList
            // Reset state when new lyrics are loaded
            currentLineIndex = -1
            // Start the ticker after a delay to allow Media3 timeline to stabilize
            lyricHandler.postDelayed(lyricRunnable, 300)
        }
    }

    private fun findLineForPosition(positionMs: Long): Line? {
        val lyrics = currentLyrics?.structuredLyrics?.firstOrNull()?.line
        if (lyrics.isNullOrEmpty()) return null

        // Optimized search: start from cached index if available and nearby, otherwise use binary search
        if (cachedCurrentLineIndex >= 0 && cachedCurrentLineIndex < lyrics.size) {
            val cachedLine = lyrics[cachedCurrentLineIndex]
            val cachedStart = cachedLine.start
            if (cachedStart != null) {
                val cachedStartTime = cachedStart.toLong()
                
                // If we're close to the cached position, search incrementally from there
                if (kotlin.math.abs(cachedStartTime - positionMs) < 5000) { // Within 5 seconds
                    // Search forward if current position is ahead of cached
                    if (positionMs >= cachedStartTime) {
                        for (i in cachedCurrentLineIndex until lyrics.size) {
                            val line = lyrics[i]
                            val lineStart = line.start
                            if (lineStart != null) {
                                val startTime = lineStart.toLong()
                                if (startTime > positionMs) {
                                    return if (i > 0) lyrics[i - 1] else null
                                }
                            }
                        }
                        // If we reached the end, return the last line
                        return lyrics.lastOrNull()
                    } 
                    // Search backward if current position is behind cached
                    else {
                        for (i in cachedCurrentLineIndex downTo 0) {
                            val line = lyrics[i]
                            val lineStart = line.start
                            if (lineStart != null) {
                                val startTime = lineStart.toLong()
                                if (startTime <= positionMs) {
                                    return line
                                }
                            }
                        }
                        // If we reached the beginning, return the first line if it's within bounds
                        val firstLine = lyrics[0]
                        val firstLineStart = firstLine.start
                        if (lyrics.isNotEmpty() && firstLineStart != null && firstLineStart.toLong() <= positionMs) {
                            return firstLine
                        }
                    }
                }
            }
        }
        
        // Fallback to binary search if incremental search wasn't applicable
        var left = 0
        var right = lyrics.size - 1
        var bestMatchIndex = -1
        
        while (left <= right) {
            val mid = (left + right) / 2
            if (mid >= lyrics.size) break // Safety check
            
            val midLine = lyrics[mid]
            val midStart = midLine.start
            
            if (midStart != null) {
                val startTime = midStart.toLong()
                
                if (startTime <= positionMs) {
                    bestMatchIndex = mid
                    left = mid + 1 // Look for a later match
                } else {
                    right = mid - 1 // Look for an earlier match
                }
            } else {
                // Skip lines without start time
                left++
            }
        }
        
        return if (bestMatchIndex != -1) lyrics[bestMatchIndex] else null
    }

    private val lyricRunnable = object : Runnable {
        override fun run() {
            val controller = mediaController ?: return
            if (!controller.isConnected) return

            try {
                // Check if player is playing - only update if playing
                if (controller.playbackState == androidx.media3.common.Player.STATE_READY && controller.isPlaying) {
                    val position = controller.currentPosition
                    val line = findLineForPosition(position)

                    if (line != null) {
                        // Update cached index when we find a valid line
                         val lyrics = currentLyrics?.structuredLyrics?.firstOrNull()?.line
                         if (lyrics != null) {
                             // Find the index of the current line in the lyrics array
                             val lineStart = line.start
                             val lineValue = line.value
                             val foundIndex = lyrics.indexOfFirst { 
                                 val itStart = it.start 
                                 val itValue = it.value
                                 itStart != null && lineStart != null && itStart == lineStart && itValue == lineValue 
                             }
                             if (foundIndex != -1) {
                                 cachedCurrentLineIndex = foundIndex
                             }
                         }
                         
                        // Access the original lastLyric from the updateLyrics method context
                        // We need to call updateLyrics with the current line
                        // But we need to check if it's different from the current display
                        updateLyricsFromTicker(line.value, getNextLine(line)?.value)
                    }
                    // Update last position for the next cycle
                    lastUpdatePosition = position
                } else {
                    // If player is paused/stopped, reduce update frequency to save resources
                    // Also reset the lastUpdatePosition so when playback resumes, we update immediately
                    lastUpdatePosition = -1
                    // Continue the ticker but with longer interval when not playing
                    lyricHandler.postDelayed(this, 1000) // Check every second when not playing
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating lyrics", e)
            }

            // Continue the ticker with optimized interval
            lyricHandler.postDelayed(this, 300) // Reduced from 500ms to 300ms for better responsiveness while still optimizing
        }
    }

    private fun updateLyricsFromTicker(currentLyric: String?, nextLyric: String?) {
        // Update lyrics display without the duplicate lastLyric check
        // Just call the original updateLyrics method
        updateLyrics(null, currentLyric, nextLyric)
    }

    private fun getNextLine(currentLine: Line): Line? {
        val lyrics = currentLyrics?.structuredLyrics?.firstOrNull()?.line
        if (lyrics.isNullOrEmpty()) return null
        
        // Fallback to the original search method
        for (i in lyrics.indices) {
            if (lyrics[i] === currentLine) {  // Using reference equality for performance
                return if (i < lyrics.size - 1) lyrics[i + 1] else null
            }
        }
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Desktop Lyrics",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows lyrics on the home screen"
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Desktop Lyrics")
            .setContentText("Displaying lyrics on home screen")
            .setSmallIcon(R.drawable.ic_lyrics)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initializeLyricsView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lyricsView = LayoutInflater.from(this).inflate(R.layout.lyrics_overlay, null)

        prevLyricsTextView = lyricsView.findViewById(R.id.prev_lyrics_text_view)
        currentLyricsTextView = lyricsView.findViewById(R.id.current_lyrics_text_view)
        nextLyricsTextView = lyricsView.findViewById(R.id.next_lyrics_text_view)
        
        // Set touch listeners for drag and lock functionality
        setupDragAndLockListeners()
        
        // Load and apply saved lock state
        updateLockState()
        
        // Make sure prev_lyrics_text_view is hidden for two-line display
        prevLyricsTextView.visibility = View.GONE
        
        // Set default font sizes based on device characteristics
        updateFontSizes()
        
        // Set background opacity
        updateOpacity()

        // Restore saved position
        val savedX = Preferences.getDesktopLyricsPositionX()
        val savedY = Preferences.getDesktopLyricsPositionY()
        
        // Only restore position if it's not the default (0,0)
        if (savedX != 0 || savedY != 0) {
            windowParams.x = savedX
            windowParams.y = savedY
        }

        try {
            windowManager.addView(lyricsView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding lyrics view: ${e.message}")
        }
    }
    
    private fun updateOpacity() {
        // Get the saved opacity value (0.0-1.0)
        val opacity = Preferences.getDesktopLyricsOpacity()
        
        // Get the current text color
        val color = Preferences.getDesktopLyricsFontColor()
        
        // Apply opacity to the text color
        applyTextColorWithOpacity(color, opacity)
    }
    
    private fun updateFontColor() {
        // Get the saved color and opacity values
        val color = Preferences.getDesktopLyricsFontColor()
        val opacity = Preferences.getDesktopLyricsOpacity()
        
        // Apply color and opacity to the text
        applyTextColorWithOpacity(color, opacity)
    }
    
    private fun applyTextColorWithOpacity(hexColor: String, opacity: Float) {
        // Convert hex color to int with alpha channel
        val colorInt = Integer.parseInt(hexColor.replaceFirst("#", ""), 16)
        val alpha = (opacity * 255).toInt()
        val colorWithAlpha = (alpha shl 24) or (colorInt and 0x00FFFFFF)
        
        // Apply the color with opacity to both text views
        currentLyricsTextView.setTextColor(colorWithAlpha)
        nextLyricsTextView.setTextColor(colorWithAlpha)
    }

    private fun updateLockState() {
        isLocked = Preferences.getDesktopLyricsLocked()
        
        // When locked, make the window completely untouchable so events pass through
        // When unlocked, allow touch events for dragging
        if (isLocked) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        
        // Update the window with the new flags only if the view is attached to window manager
        try {
            // Check if the view is attached to a window before updating layout
            if (::windowManager.isInitialized && ::lyricsView.isInitialized && lyricsView.windowToken != null) {
                windowManager.updateViewLayout(lyricsView, windowParams)
                Log.d(TAG, "updateLockState: Successfully updated window flags")
            } else {
                Log.d(TAG, "updateLockState: View not attached to window manager, skipping flag update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateLockState: Error updating window flags: ${e.message}")
        }
    }

    private fun updateFontSizes() {
        val displayMetrics = resources.displayMetrics
        val densityDpi = displayMetrics.densityDpi
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = screenWidth > screenHeight
        val isWideScreen = isLandscape && screenWidth / displayMetrics.density > 600 // 600dp wide considered as wide screen

        // Base font size calculation
        var baseFontSize = when {
            densityDpi <= 160 -> 24f // mdpi - significantly larger
            densityDpi <= 240 -> 22f // hdpi - larger
            densityDpi <= 320 -> 20f // xhdpi - normal
            densityDpi <= 480 -> 18f // xxhdpi - normal
            else -> 16f // xxxhdpi - smaller
        }

        // Adjust for wide screens (like car displays)
        if (isWideScreen) {
            baseFontSize *= 1.2f // Increase by 20% for wide screens
        }

        // Apply settings if user has customized
        val savedFontSize = Preferences.getDesktopLyricsFontSize()
        val finalFontSize = savedFontSize.takeIf { it > 0f } ?: baseFontSize

        // Set font sizes with 15% difference between current and others
        currentLyricsTextView.textSize = finalFontSize
        prevLyricsTextView.textSize = finalFontSize * 0.85f
        nextLyricsTextView.textSize = finalFontSize * 0.85f

        // Set line spacing proportional to font size
        val lineSpacing = finalFontSize * 0.4f // 40% of font size as line spacing
        prevLyricsTextView.setLineSpacing(lineSpacing, 1f)
        currentLyricsTextView.setLineSpacing(lineSpacing, 1f)
        nextLyricsTextView.setLineSpacing(lineSpacing, 1f)
    }

    private fun setupDragAndLockListeners() {
        lyricsView.setOnTouchListener {_, event ->
            if (isLocked) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Start dragging
                    isDragging = true
                    initialX = windowParams.x
                    initialY = windowParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return@setOnTouchListener false
                    
                    // Calculate new position
                    // X axis direction is correct
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val newX = initialX + deltaX
                    
                    // Y axis direction was reversed - fix it by subtracting the delta instead of adding
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    val newY = initialY - deltaY
                    
                    // Update window position
                    windowParams.x = newX
                    windowParams.y = newY
                    
                    try {
                        windowManager.updateViewLayout(lyricsView, windowParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating lyrics view position: ${e.message}")
                    }
                    
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    // Stop dragging and save position
                    isDragging = false
                    
                    // Save the current position to preferences
                    Preferences.setDesktopLyricsPositionX(windowParams.x)
                    Preferences.setDesktopLyricsPositionY(windowParams.y)
                    
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
        
        // Double tap to lock/unlock position

    }
    private var lastLyric: String? = null
    fun updateLyrics(prevLyric: String?, currentLyric: String?, nextLyric: String?) {

        // 没有歌词 → 只处理显隐一次
        if (currentLyric == null) {
            if (lyricsView.visibility == View.VISIBLE) {
                lyricsView.visibility = View.GONE
            }
            lastLyric = null
            return
        }

        // 🔥 核心：同一句歌词，直接返回，不再动画
        if (currentLyric == lastLyric) {
            return
        }
        lastLyric = currentLyric

        // ===== 到这里，才是真正的“歌词切换节点” =====

        if (lyricsView.visibility != View.VISIBLE) {
            lyricsView.visibility = View.VISIBLE
        }

        // 取消当前正在进行的动画
        currentLyricsTextView.animate().cancel()
        nextLyricsTextView.animate().cancel()

        // 立即更新文本
        prevLyricsTextView.text = prevLyric.orEmpty()
        currentLyricsTextView.text = currentLyric
        nextLyricsTextView.text = nextLyric.orEmpty()

        currentLyricsTextView.post {

            // ===== 上一句：淡出 =====
            prevLyricsTextView.animate().cancel()
            prevLyricsTextView.alpha = 1f
            prevLyricsTextView.translationY = 0f

            prevLyricsTextView.animate()
                .translationY(-16f)
                .alpha(0f)
                .setDuration(240)
                .start()

            // ===== 当前句：淡入（你原来的逻辑）=====
            currentLyricsTextView.translationY = 24f
            currentLyricsTextView.alpha = 0f
            nextLyricsTextView.translationY = 24f
            nextLyricsTextView.alpha = 0f

            currentLyricsTextView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .start()

            // ===== 下一句：淡入（你原来的逻辑）=====
            nextLyricsTextView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .start()
        }
    }



}