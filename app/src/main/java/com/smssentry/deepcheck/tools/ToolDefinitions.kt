package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.model.Tool
import kotlinx.serialization.json.Json

object ToolDefinitions {

    private val jsonSchema = Json { prettyPrint = false }

    val toolList: List<Tool> = listOf(
        Tool(
            name = "lookup_allowlist",
            description = "Check if a sender or domain is on the trusted allowlist.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "sender": {"type": "string", "description": "The SMS sender name or number"},
                    "domain": {"type": "string", "description": "A domain extracted from the SMS"}
                }
            }
            """.trimIndent()
        ),
        Tool(
            name = "search_personal_db",
            description = "Search the personal scam history database for a previously seen SMS pattern.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "sender": {"type": "string", "description": "The SMS sender"},
                    "sms_prefix": {"type": "string", "description": "First 10 characters of the SMS body"}
                },
                "required": ["sender", "sms_prefix"]
            }
            """.trimIndent()
        ),
        Tool(
            name = "offline_reputation_check",
            description = "Check if any URLs in the SMS are found in the offline scam/phishing database.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "urls": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "List of URLs found in the SMS"
                    }
                },
                "required": ["urls"]
            }
            """.trimIndent()
        ),
        Tool(
            name = "brand_mismatch_check",
            description = "Check if the SMS claims to be from a known brand but links point to an unofficial domain.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "sms_text": {"type": "string", "description": "The full SMS text"},
                    "urls": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "URLs found in the SMS"
                    }
                },
                "required": ["sms_text", "urls"]
            }
            """.trimIndent()
        ),
        Tool(
            name = "whois_lookup",
            description = "Look up domain registration details (age, registrar) via the privacy proxy. Requires network.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "domain": {"type": "string", "description": "The domain to look up"}
                },
                "required": ["domain"]
            }
            """.trimIndent()
        ),
        Tool(
            name = "compare_official_site",
            description = "Compare a linked domain against the known official domain for a claimed entity.",
            parameters = """
            {
                "type": "object",
                "properties": {
                    "claimed_entity": {"type": "string", "description": "The entity the SMS claims to be from"},
                    "linked_domain": {"type": "string", "description": "The domain the SMS links to"}
                },
                "required": ["claimed_entity", "linked_domain"]
            }
            """.trimIndent()
        )
    )
}
