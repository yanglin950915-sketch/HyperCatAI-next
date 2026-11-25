package com.hypercat.ai.agent

enum class AgentPermission { NONE, USER_CONFIRMATION, SYSTEM_PERMISSION }

data class AgentTool(
    val id: String,
    val title: String,
    val description: String,
    val permission: AgentPermission
)

object AgentToolCatalog {
    val tools = listOf(
        AgentTool("device_info", "设备信息", "读取应用可访问的设备基础信息", AgentPermission.NONE),
        AgentTool("open_settings", "打开系统设置", "通过系统 Intent 打开设置页面", AgentPermission.NONE),
        AgentTool("create_reminder", "创建提醒", "创建提醒前向用户确认时间与内容", AgentPermission.USER_CONFIRMATION),
        AgentTool("send_message", "发送消息", "发送前展示收件人和完整内容并确认", AgentPermission.USER_CONFIRMATION),
        AgentTool("change_system_setting", "修改系统设置", "需要系统授权，且由用户明确确认", AgentPermission.SYSTEM_PERMISSION)
    )
}
