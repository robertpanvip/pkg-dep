package depslens.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * 统一的 IntelliJ 通知工具：升级结果（成功/失败）等以气球提示反馈给用户。
 * 通知组在 plugin.xml 中声明（id = "DepsLens"）。
 */
object DepsLensNotifier {
    private const val GROUP_ID = "DepsLens"

    fun success(project: Project, pkg: String, target: String) {
        notify(project, "升级成功", "已将 $pkg 升级到 $target，并重新解析了依赖图。", NotificationType.INFORMATION)
    }

    fun error(project: Project, pkg: String, target: String, e: Throwable) {
        val firstLine = (e.message ?: e.toString()).lineSequence().firstOrNull().orEmpty().take(200)
        notify(project, "升级失败", "$pkg -> $target 失败：$firstLine", NotificationType.ERROR)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }
}
