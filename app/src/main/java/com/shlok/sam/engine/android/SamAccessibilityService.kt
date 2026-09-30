package com.shlok.sam.engine.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityController @Inject constructor() {
    fun available(): Boolean = SamAccessibilityService.instance != null
    fun service(): SamAccessibilityService? = SamAccessibilityService.instance
}

class SamAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        lastPackage = rootInActiveWindow?.packageName?.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let { lastPackage = it }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun activePackage(): String? = lastPackage ?: rootInActiveWindow?.packageName?.toString()

    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun findByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return search(root, text)
    }

    private fun search(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val t = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (t.contains(text, ignoreCase = true) && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = search(child, text)
            if (found != null) return found
        }
        return null
    }

    fun clickText(text: String): Boolean {
        val node = findByText(text) ?: return false
        return clickNode(node)
    }

    fun clickContentDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByContentDesc(root, desc) ?: return false
        return clickNode(node)
    }

    private fun findByContentDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findByContentDesc(child, desc)?.let { return it }
        }
        return null
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 650)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (direction == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        if (scrollNode(root, action)) return true
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y = dm.heightPixels / 2f
        return if (direction == "up") swipe(x, y * 0.35f, x, y * 0.75f) else swipe(x, y * 0.75f, x, y * 0.35f)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable && node.performAction(action)) return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (scrollNode(c, action)) return true
        }
        return false
    }

    fun typeInFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findEditable(c)?.let { return it }
        }
        return null
    }

    fun dumpVisibleText(limit: Int = 40): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<String>()
        collectText(root, out, limit)
        return out
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, limit: Int) {
        if (out.size >= limit) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collectText(c, out, limit)
        }
    }

    suspend fun waitForPackage(packageName: String, timeoutMs: Long = 6000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (activePackage() == packageName) return true
            delay(200)
        }
        return activePackage() == packageName
    }

    suspend fun waitForText(text: String, timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (findByText(text) != null) return true
            delay(250)
        }
        return findByText(text) != null
    }

    companion object {
        @Volatile
        var instance: SamAccessibilityService? = null
            private set
        @Volatile
        private var lastPackage: String? = null
    }
}
