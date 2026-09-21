package com.touchmouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Service cœur : injecte gestes + gère texte Whisper Flow
 * + délègue l'overlay à OverlayManager
 */
class TouchMouseService : AccessibilityService() {

    companion object {
        var instance: TouchMouseService? = null
        const val TAG = "TouchMouseService"
    }

    private var overlayManager: OverlayManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var volumeControlEnabled = true
    private var axisVertical = true
    private var lastVolumeToast = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Service connected")
        overlayManager = OverlayManager(this)
        // L'overlay ne peut être créé que si SYSTEM_ALERT_WINDOW est accordé
        // On laisse MainActivity/OverlayService le déclencher
        scope.launch {
            try {
                val prefs = prefsFlow().first()
                volumeControlEnabled = prefs.volumeControl
                if (prefs.overlayEnabled) tryShowOverlayIfPermitted()
            } catch (e: Exception) {
                Log.w(TAG, "prefs read failed", e)
                tryShowOverlayIfPermitted()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // Volume +/- = secours pour déplacer le curseur sans tactile
    // Mode vertical par défaut : Vol+ = haut, Vol- = bas
    // Appui LONG sur volume = bascule axe vertical/horizontal
    // En mode horizontal : Vol+ = droite, Vol- = gauche
    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event == null) return false
        // Refresh cache de façon paresseuse (évite runBlocking)
        val code = event.keyCode
        if (code != android.view.KeyEvent.KEYCODE_VOLUME_UP &&
            code != android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (!volumeControlEnabled) return false
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            // Appui long (repeat) = change d'axe
            if (event.repeatCount == 1) {
                axisVertical = !axisVertical
                val msg = if (axisVertical) "Axe VERTICAL (Vol+ = haut, Vol- = bas)"
                          else "Axe HORIZONTAL (Vol+ = droite, Vol- = gauche)"
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                return true
            }
            if (event.repeatCount > 1) return true
            val step = 45f
            val mgr = overlayManager ?: return false
            when (code) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP ->
                    if (axisVertical) mgr.moveCursorBy(0f, -step) else mgr.moveCursorBy(step, 0f)
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN ->
                    if (axisVertical) mgr.moveCursorBy(0f, step) else mgr.moveCursorBy(-step, 0f)
            }
            // Rafraîchit le flag depuis DataStore sans bloquer (une fois de temps en temps)
            if (System.currentTimeMillis() - lastVolumeToast > 5000) {
                lastVolumeToast = System.currentTimeMillis()
                scope.launch {
                    try { volumeControlEnabled = prefsFlow().first().volumeControl }
                    catch (_: Exception) {}
                }
            }
            return true
        }
        // Consomme aussi le UP pour ne pas changer le volume système
        if (event.action == android.view.KeyEvent.ACTION_UP) return true
        return false
    }

    fun setVolumeControl(enabled: Boolean) { volumeControlEnabled = enabled }

    override fun onUnbind(intent: Intent?): Boolean {
        overlayManager?.destroy()
        overlayManager = null
        instance = null
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlayManager?.destroy()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    fun tryShowOverlayIfPermitted() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            overlayManager?.show()
        }
    }

    fun hideOverlay() {
        overlayManager?.hide()
    }

    // ===== GESTURES API =====

    fun clickAt(x: Float, y: Float, onDone: (() -> Unit)? = null) {
        dispatchGesture(
            GestureDescription.Builder().apply {
                val path = Path().apply { moveTo(x, y) }
                addStroke(GestureDescription.StrokeDescription(path, 0, 40))
            }.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onDone?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onDone?.invoke()
                }
            }, null
        )
    }

    fun longClickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 600))
                .build(), null, null
        )
    }

    fun doubleClickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
                .addStroke(GestureDescription.StrokeDescription(path, 100, 40))
                .build(), null, null
        )
    }

    fun scrollAt(x: Float, y: Float, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + dx, y + dy)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
                .build(), null, null
        )
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long = 300) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build(), null, null
        )
    }

    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        swipe(fromX, fromY, toX, toY, 400)
    }

    fun globalBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun globalNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun globalQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun globalPowerDialog() = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    // Sélection / copier-coller : fonctionne dans toutes les apps avec champ texte
    fun selectAllFocused(): Boolean {
        val n = findFocusedEditText(rootInActiveWindow) ?: return false
        return n.performAction(AccessibilityNodeInfo.ACTION_SELECT_ALL)
    }
    fun copyFocused(): Boolean {
        val n = findFocusedEditText(rootInActiveWindow) ?: return false
        return n.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }
    fun cutFocused(): Boolean {
        val n = findFocusedEditText(rootInActiveWindow) ?: return false
        return n.performAction(AccessibilityNodeInfo.ACTION_CUT)
    }
    fun pasteFocused(): Boolean {
        val n = findFocusedEditText(rootInActiveWindow) ?: return false
        if (n.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true
        return insertViaClipboard(getClipboardText())
    }
    private fun getClipboardText(): String {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (_: Exception) { "" }
    }

    // ===== WHISPER FLOW - Injection texte =====
    /**
     * Injecte texte comme le fait Wispr Flow : fonctionne partout
     * 1) Essaie ACTION_SET_TEXT sur le champ focus
     * 2) Sinon clipboard + ACTION_PASTE
     */
    fun insertText(text: String): Boolean {
        val root = rootInActiveWindow
        val focused = findFocusedEditText(root)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            // On essaie de remplacer ou insérer : on récupère texte existant
            val existing = focused.text?.toString() ?: ""
            val toInsert = if (existing.isEmpty()) text else existing + " " + text
            val b2 = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, toInsert)
            }
            val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b2)
            if (ok) {
                Log.i(TAG, "insertText via SET_TEXT")
                return true
            }
        }
        // Fallback clipboard
        return insertViaClipboard(text)
    }

    private fun insertViaClipboard(text: String): Boolean {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("whisper", text))
            val root = rootInActiveWindow
            val focused = findFocusedEditText(root)
            if (focused != null) {
                // Essaie PASTE
                if (focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    Log.i(TAG, "insertText via PASTE")
                    return true
                }
                // Sinon SET_TEXT avec clipboard
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
            }
            // Dernier recours : focus + paste global (certaines apps nécessitent un clic avant)
            // On notifie l'utilisateur
            Log.w(TAG, "Clipboard fallback: texte copié, colle manuellement si besoin")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "insertViaClipboard error", e)
            return false
        }
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.className?.contains("EditText") == true) return node
        // Certains champs ne reportent pas isFocused mais isAccessibilityFocused ou isEditable
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditText(child)
            if (found != null) return found
            // sinon chercher champ éditable focusé via action
            if (child.isFocused) {
                // BFS : si on trouve un EditText dans le subtree du focus
                val edit = findEditTextInSubtree(child)
                if (edit != null) return edit
            }
        }
        // fallback : cherche le premier EditText focusable
        return findEditTextInSubtree(node)?.takeIf { it.isFocused || it.isAccessibilityFocused }
    }

    private fun findEditTextInSubtree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findEditTextInSubtree(c)
            if (f != null) return f
        }
        return null
    }
}
