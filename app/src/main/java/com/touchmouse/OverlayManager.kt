package com.touchmouse

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import android.view.Gravity

/**
 * Gère tout l'overlay :
 * - Curseur flottant (rond rouge)
 * - Zone touchpad (dans partie tactile encore fonctionnelle, configurable)
 * - Barre d'actions (clic, long, scroll, back...)
 * - Bouton Whisper Flow flottant
 *
 * Inspiration : "souris" pour écran cassé à 75% -> l'utilisateur place le pad dans les 25% qui marchent
 */
class OverlayManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isShowing = false

    // Views
    private var cursorView: View? = null
    private var padContainer: FrameLayout? = null
    private var whisperButton: View? = null
    private var controlsView: View? = null

    // Cursor position
    private var cursorX = 500f
    private var cursorY = 800f
    private var screenW = 0
    private var screenH = 0

    // Prefs cache
    private var sensitivity = 1.5f
    private var dwellEnabled = false
    private var dwellTime = 800
    private var volumeControl = true

    // Dwell handler
    private val handler = Handler(Looper.getMainLooper())
    private var dwellRunnable: Runnable? = null

    // Gyro
    private var sensorManager: SensorManager? = null
    private var gyroEnabled = false
    private var gyroListener: SensorEventListener? = null

    // Whisper helper
    private var whisperHelper: WhisperHelper? = null

    fun show() {
        if (isShowing) return
        isShowing = true
        val dm = ctx.resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        cursorX = screenW / 2f
        cursorY = screenH / 2f

        scope.launch {
            val prefs = ctx.prefsFlow().first()
            sensitivity = prefs.sensitivity
            dwellEnabled = prefs.dwellClick
            dwellTime = prefs.dwellTime
            volumeControl = prefs.volumeControl
            gyroEnabled = prefs.gyroControl
            createCursor()
            createPad(prefs)
            createControls()
            if (prefs.whisperEnabled) createWhisperButton(prefs)
            if (gyroEnabled) enableGyro()
            startDwellIfNeeded()
        }
    }

    fun hide() {
        isShowing = false
        removeAll()
        try { scope.cancel() } catch(_:Exception){}
        disableGyro()
    }

    fun destroy() = hide()

    // Déplacement curseur depuis touches volume / gyro externe
    fun moveCursorBy(dx: Float, dy: Float) {
        if (!isShowing) return
        cursorX = (cursorX + dx).coerceIn(0f, screenW.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, screenH.toFloat())
        updateCursor()
    }

    fun getCursor(): Pair<Float, Float> = cursorX to cursorY

    private fun removeAll() {
        listOf(cursorView, padContainer, whisperButton, controlsView).forEach {
            try { if (it != null) wm.removeView(it) } catch (_: Exception) {}
        }
        cursorView = null; padContainer = null; whisperButton = null; controlsView = null
    }

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            w, h, type,
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
        }
    }

    // ========== CURSOR ==========
    private fun createCursor() {
        val size = 28
        val dp = (size * ctx.resources.displayMetrics.density).toInt()
        val v = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
                setStroke((2*ctx.resources.displayMetrics.density).toInt(), Color.WHITE)
            }
            elevation = 8f
        }
        val params = overlayParams(dp, dp, cursorX.toInt() - dp/2, cursorY.toInt() - dp/2, touchable = false)
        try { wm.addView(v, params); cursorView = v } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateCursor() {
        val dp = (28 * ctx.resources.displayMetrics.density).toInt()
        val v = cursorView ?: return
        val p = v.layoutParams as WindowManager.LayoutParams
        p.x = (cursorX - dp/2).toInt().coerceIn(0, screenW - dp)
        p.y = (cursorY - dp/2).toInt().coerceIn(0, screenH - dp)
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
        scheduleDwell()
    }

    // ========== PAD ==========
    private fun createPad(prefs: AppPrefs) {
        val density = ctx.resources.displayMetrics.density
        val padSizePx = (prefs.padSize * density).toInt()
        val alpha = prefs.padAlpha

        val container = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24*density
                setColor(Color.parseColor("#CC212121"))
                setStroke((2*density).toInt(), Color.parseColor("#66FFFFFF"))
            }
            alpha = alpha
            elevation = 12f
        }

        // Label
        val label = TextView(ctx).apply {
            text = "◉ PAD  • glisse = déplace  • tap = clic"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            setPadding((8*density).toInt(), (6*density).toInt(), (8*density).toInt(), (4*density).toInt())
        }

        // Zone tactile intérieure
        val touchArea = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16*density
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }

        // Boutons rapides dans le pad
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun miniBtn(txt: String, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = txt
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding((10*density).toInt(), (6*density).toInt(), (10*density).toInt(), (6*density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12*density
                    setColor(Color.parseColor("#44FFFFFF"))
                }
                setOnClickListener { onClick() }
            }.also { btnRow.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (4*density).toInt(); marginStart = (4*density).toInt()
            })}
        }
        miniBtn("CLIC") { TouchMouseService.instance?.clickAt(cursorX, cursorY); vibrate(20) }
        miniBtn("LONG") { TouchMouseService.instance?.longClickAt(cursorX, cursorY); vibrate(30) }
        miniBtn("SEL") {
            // Sélection : 1) essaie Tout sélectionner si champ texte focus, 2) sinon double-clic = sélectionne mot
            val svc = TouchMouseService.instance
            if (svc?.selectAllFocused() != true) {
                svc?.doubleClickAt(cursorX, cursorY)
            }
            vibrate(30)
        }
        miniBtn("↔ DRAG") { isDragging = !isDragging; label.text = if(isDragging) "DRAG ON • glisse puis tap pour déposer / sélectionner" else "◉ PAD  • glisse = déplace  • tap = clic" }

        // Layout interne
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(touchArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins((8*density).toInt(), (4*density).toInt(), (8*density).toInt(), (4*density).toInt())
            })
            addView(btnRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((8*density).toInt(), 0, (8*density).toInt(), (8*density).toInt())
            })
        }
        container.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Position initiale selon préférence ou custom
        // Optimisé pour bande gauche 25% : middle_left = collé à gauche, centré verticalement
        var initX: Int
        var initY: Int
        if (prefs.padX >= 0 && prefs.padY >= 0) {
            initX = prefs.padX; initY = prefs.padY
        } else {
            initX = when(prefs.padPosition) {
                "bottom_right", "top_right", "middle_right" -> screenW - padSizePx - (8*density).toInt()
                else -> (8*density).toInt() // middle_left, bottom_left, top_left : collé à gauche
            }
            initY = when(prefs.padPosition) {
                "top_left", "top_right" -> (80*density).toInt()
                "middle_left", "middle_right" -> (screenH - padSizePx) / 2
                else -> screenH - padSizePx - (120*density).toInt()
            }
        }

        val params = overlayParams(padSizePx, padSizePx, initX, initY, touchable = true)

        // Touch handling
        var lastX = 0f; var lastY = 0f
        var startX = 0f; var startY = 0f
        var downTime = 0L
        var isMovingPad = false
        var padStartX = 0; var padStartY = 0
        var totalDx = 0f; var totalDy = 0f

        // Déplacement du pad : long press sur bord/label
        label.setOnTouchListener { _, ev ->
            when(ev.action){
                MotionEvent.ACTION_DOWN -> { isMovingPad = true; padStartX = params.x; padStartY = params.y; startX=ev.rawX; startY=ev.rawY; true }
                MotionEvent.ACTION_MOVE -> if(isMovingPad){
                    params.x = (padStartX + (ev.rawX - startX)).toInt()
                    params.y = (padStartY + (ev.rawY - startY)).toInt()
                    try{ wm.updateViewLayout(container, params)}catch(_:Exception){}
                    true
                } else false
                MotionEvent.ACTION_UP -> {
                    isMovingPad=false
                    // Sauve position
                    scope.launch {
                        ctx.dataStore.edit { it[PrefsKeys.PAD_X]=params.x; it[PrefsKeys.PAD_Y]=params.y }
                    }
                    true
                }
                else -> false
            }
        }

        var dragStartCursorX = 0f
        var dragStartCursorY = 0f
        touchArea.setOnTouchListener { _, ev ->
            when(ev.action){
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX; lastY = ev.rawY
                    downTime = System.currentTimeMillis()
                    totalDx=0f; totalDy=0f
                    dragStartCursorX = cursorX
                    dragStartCursorY = cursorY
                    if(isDragging){
                        // début drag système
                        TouchMouseService.instance?.let { /* on fera swipe au UP */ }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - lastX) * sensitivity
                    val dy = (ev.rawY - lastY) * sensitivity
                    totalDx += dx; totalDy += dy
                    cursorX = (cursorX + dx).coerceIn(0f, screenW.toFloat())
                    cursorY = (cursorY + dy).coerceIn(0f, screenH.toFloat())
                    updateCursor()
                    lastX = ev.rawX; lastY = ev.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dt = System.currentTimeMillis() - downTime
                    val dist = kotlin.math.hypot(totalDx, totalDy)
                    if(dist < 12 && dt < 300){
                        // TAP = clic ou drop si dragging
                        if(isDragging){
                            TouchMouseService.instance?.drag(dragStartCursorX, dragStartCursorY, cursorX, cursorY)
                            isDragging=false
                            label.text="◉ PAD  • glisse = déplace  • tap = clic"
                            vibrate(40)
                        } else {
                            TouchMouseService.instance?.clickAt(cursorX, cursorY)
                            vibrate(15)
                            // feedback visuel
                            cursorView?.animate()?.scaleX(1.6f)?.scaleY(1.6f)?.setDuration(80)?.withEndAction {
                                cursorView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start()
                            }?.start()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        // Scroll avec 2 doigts simulé : barre dédiée dans controls, mais aussi swipe à 2 doigts ?
        // On ajoute gesture detector simple pour scroll
        try { wm.addView(container, params); padContainer = container } catch (e: Exception){ e.printStackTrace() }
    }

    private var isDragging = false
    private fun vibrate(ms: Long){
        try{
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else vib.vibrate(ms)
        }catch(_:Exception){}
    }

    // ========== CONTROLS ==========
    private fun createControls(){
        val density = ctx.resources.displayMetrics.density
        val w = (screenW * 0.92).toInt()
        val h = (56*density).toInt()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28*density
                setColor(Color.parseColor("#DD212121"))
            }
            elevation = 10f
            setPadding((12*density).toInt(), (6*density).toInt(), (12*density).toInt(), (6*density).toInt())
        }
        fun btn(icon: String, desc: String, action: ()->Unit){
            val tv = TextView(ctx).apply {
                text = icon
                contentDescription = desc
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 18f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#33FFFFFF"))
                }
                setOnClickListener { action(); vibrate(12) }
            }
            container.addView(tv, LinearLayout.LayoutParams((44*density).toInt(), (44*density).toInt()).apply {
                marginEnd = (6*density).toInt()
            })
        }
        btn("◀", "Retour"){ TouchMouseService.instance?.globalBack() }
        btn("⌂", "Home"){ TouchMouseService.instance?.globalHome() }
        btn("▢", "Recents"){ TouchMouseService.instance?.globalRecents() }
        btn("⬇", "Notif"){ TouchMouseService.instance?.globalNotifications() }
        // scroll
        btn("↑", "Scroll haut"){ TouchMouseService.instance?.scrollAt(cursorX, cursorY, 0f, -420f) }
        btn("↓", "Scroll bas"){ TouchMouseService.instance?.scrollAt(cursorX, cursorY, 0f, 420f) }
        btn("×2", "Double clic"){ TouchMouseService.instance?.doubleClickAt(cursorX, cursorY) }
        // masquer / déplacer
        val hide = TextView(ctx).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF8A80"))
            gravity = Gravity.CENTER
            textSize = 14f
            setOnClickListener {
                scope.launch { ctx.dataStore.edit { it[PrefsKeys.OVERLAY_ENABLED]=false } }
                hide()
            }
        }
        container.addView(hide, LinearLayout.LayoutParams((36*density).toInt(), (36*density).toInt()))

        val params = overlayParams(w, h, (screenW - w)/2, (28*density).toInt(), touchable = true)
        // Rendre draggable aussi
        var startX=0f; var startY=0; var downRawX=0f
        container.setOnTouchListener { v, ev ->
            when(ev.action){
                MotionEvent.ACTION_DOWN -> { downRawX=ev.rawX; startX=params.x.toFloat(); startY=params.y; v.parent?.requestDisallowInterceptTouchEvent(true); true}
                MotionEvent.ACTION_MOVE -> {
                    // si déplacement horizontal important, bouge la barre, sinon laisse clics boutons
                    val dx = ev.rawX - downRawX
                    if(kotlin.math.abs(dx) > 18){
                        params.x = (startX + dx).toInt()
                        try{ wm.updateViewLayout(container, params)}catch(_:Exception){}
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP -> false
                else -> false
            }
        }
        try{ wm.addView(container, params); controlsView=container }catch(e:Exception){ e.printStackTrace() }
    }

    // ========== WHISPER FLOW BUTTON ==========
    private fun createWhisperButton(prefs: AppPrefs){
        whisperHelper = WhisperHelper(ctx) { recognizedText ->
            // Injection via service
            val ok = TouchMouseService.instance?.insertText(recognizedText) ?: false
            // feedback
            android.widget.Toast.makeText(ctx, if(ok) "✓ \"$recognizedText\"" else "Copié: $recognizedText (colle manuellement)", android.widget.Toast.LENGTH_SHORT).show()
        }
        val density = ctx.resources.displayMetrics.density
        val size = (62*density).toInt()
        val btn = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF1E88E5"))
                setStroke((2*density).toInt(), Color.WHITE)
            }
            elevation = 14f
        }
        val icon = TextView(ctx).apply {
            text = "🎤"
            gravity = Gravity.CENTER
            textSize = 26f
        }
        btn.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Position par défaut à droite milieu, déplaçable
        val params = overlayParams(size, size, screenW - size - (14*density).toInt(), screenH/2, touchable = true)
        var isLongPress = false
        var downRawX=0f; var downRawY=0f; var startX=0; var startY=0
        val longPressRunnable = Runnable {
            isLongPress=true
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE53935"))
                setStroke((2*density).toInt(), Color.WHITE)
            }
            vibrate(30)
            whisperHelper?.startListening(prefs.whisperMode, prefs.whisperLanguage, prefs.whisperApiKey)
            icon.text="●"
        }
        btn.setOnTouchListener { _, ev ->
            when(ev.action){
                MotionEvent.ACTION_DOWN -> {
                    downRawX=ev.rawX; downRawY=ev.rawY; startX=params.x; startY=params.y
                    isLongPress=false
                    handler.postDelayed(longPressRunnable, 180)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX; val dy = ev.rawY - downRawY
                    if(!isLongPress && (kotlin.math.abs(dx)>14 || kotlin.math.abs(dy)>14)){
                        handler.removeCallbacks(longPressRunnable)
                        // Déplacement du bouton
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        try{ wm.updateViewLayout(btn, params)}catch(_:Exception){}
                        return@setOnTouchListener true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if(isLongPress){
                        whisperHelper?.stopListening()
                        btn.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#FF1E88E5"))
                            setStroke((2*density).toInt(), Color.WHITE)
                        }
                        icon.text="🎤"
                        vibrate(15)
                    } else {
                        // tap court = toggle dictée courte (3s)
                        if(whisperHelper?.isListening()==true){
                            whisperHelper?.stopListening()
                        } else {
                            whisperHelper?.startListening(prefs.whisperMode, prefs.whisperLanguage, prefs.whisperApiKey)
                            // auto stop après 6s si pas de stop
                            handler.postDelayed({
                                if(whisperHelper?.isListening()==true){
                                    whisperHelper?.stopListening()
                                    icon.text="🎤"
                                }
                            }, 6000)
                            icon.text="●"
                            vibrate(20)
                            handler.postDelayed({ icon.text="🎤" }, 6000)
                        }
                    }
                    isLongPress=false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if(isLongPress) whisperHelper?.stopListening()
                    isLongPress=false
                    true
                }
                else -> false
            }
        }
        try{ wm.addView(btn, params); whisperButton=btn }catch(e:Exception){ e.printStackTrace() }
    }

    // ========== DWELL CLICK ==========
    private fun startDwellIfNeeded(){
        if(!dwellEnabled) return
        scheduleDwell()
    }
    private fun scheduleDwell(){
        if(!dwellEnabled) return
        dwellRunnable?.let{ handler.removeCallbacks(it) }
        dwellRunnable = Runnable {
            TouchMouseService.instance?.clickAt(cursorX, cursorY)
            vibrate(25)
            cursorView?.animate()?.scaleX(1.8f)?.scaleY(1.8f)?.setDuration(100)?.withEndAction {
                cursorView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
            }?.start()
        }
        handler.postDelayed(dwellRunnable!!, dwellTime.toLong())
    }

    // ========== GYRO ==========
    private fun enableGyro(){
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        gyroListener = object: SensorEventListener{
            override fun onSensorChanged(event: SensorEvent?) {
                if(event==null) return
                // Utilise accéléromètre : inclinaison -> déplacement curseur
                val ax = event.values[0]
                // ay inverse pour naturel
                val ay = event.values[1]
                // seuil
                if(kotlin.math.abs(ax) < 0.6 && kotlin.math.abs(ay) < 0.6) return
                cursorX = (cursorX - ax * 4 * sensitivity).coerceIn(0f, screenW.toFloat())
                cursorY = (cursorY + ay * 4 * sensitivity).coerceIn(0f, screenH.toFloat())
                updateCursor()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(gyroListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }
    private fun disableGyro(){
        try{ sensorManager?.unregisterListener(gyroListener)}catch(_:Exception){}
        gyroListener=null
    }
}
