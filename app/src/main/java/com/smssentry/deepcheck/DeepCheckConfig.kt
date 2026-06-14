package com.smssentry.deepcheck

object DeepCheckConfig {
    const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    const val MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
    const val MIN_FILE_SIZE_BYTES = 3_659_000_000L
    const val MODEL_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    
    const val PROXY_HEALTH_CHECK_INTERVAL_MS = 300_000L // 5 minutes
    const val MODEL_LOAD_TIMEOUT_MS = 120_000L  // 120s: GPU ~30s, CPU fallback ~90s
    const val LLM_TURN_TIMEOUT_MS = 180_000L    // 180s: A14 takes ~140s/turn at 3.3 tok/s
    const val TOOL_EXECUTION_TIMEOUT_MS = 10_000L
    
    const val MAX_AGENT_TURNS = 2     // With pre-executed tools, verdict usually comes on turn 0
}
