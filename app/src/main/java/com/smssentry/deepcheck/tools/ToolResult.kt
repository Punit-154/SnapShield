package com.smssentry.deepcheck.tools

sealed interface ToolResult {
    val message: String

    data class Success(override val message: String) : ToolResult
    data class Evidence(override val message: String, val severity: String = "HIGH") : ToolResult
    data class Error(override val message: String) : ToolResult
}
