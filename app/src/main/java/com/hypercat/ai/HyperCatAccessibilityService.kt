package com.hypercat.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HyperCatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun execute(command: String): Boolean = when (command.trim()) {
        "返回", "后退", "返回上一页" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "主页", "回到桌面", "返回桌面" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "最近任务", "打开最近任务" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "通知栏", "下拉通知栏" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "快速设置", "打开快捷设置" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "向上滑动", "上滑" -> swipe(true)
        "向下滑动", "下滑" -> swipe(false)
        else -> if (command.startsWith("点击")) clickText(command.removePrefix("点击").trim()) else false
    }

    private fun clickText(label: String): Boolean {
        if (label.isBlank()) return false
        val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(label) ?: return false
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isVisibleToUser && current.isEnabled && current.isClickable &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                current = current.parent
            }
        }
        return false
    }

    private fun swipe(up: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = if (up) metrics.heightPixels * 0.78f else metrics.heightPixels * 0.25f
        val endY = if (up) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.78f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 380)).build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile private var instance: HyperCatAccessibilityService? = null
        fun executeCommand(command: String): Boolean = instance?.execute(command) ?: false
        fun isEnabled(): Boolean = instance != null
    }
}
