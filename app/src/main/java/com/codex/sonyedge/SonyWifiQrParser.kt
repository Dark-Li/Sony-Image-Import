package com.codex.sonyedge

import java.util.Locale

data class SonyWifiQrPayload(
    val ssidSuffix: String,
    val password: String,
    val cameraModel: String,
    val cameraIdentity: String,
) {
    val ssid: String = "DIRECT-$ssidSuffix:$cameraModel"
}

object SonyWifiQrParser {
    private const val PREFIX = "W01:"
    private val requiredKeys = setOf("S", "P", "C", "M")

    fun parse(rawValue: String): SonyWifiQrPayload {
        val value = rawValue.trim()
        require(value.startsWith(PREFIX)) { "This is not a Sony camera Wi-Fi QR code." }

        val fields = linkedMapOf<String, String>()
        value.removePrefix(PREFIX)
            .split(';')
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val separator = token.indexOf(':')
                require(separator > 0) { "The Sony QR code contains an invalid field." }
                val key = token.substring(0, separator).uppercase(Locale.US)
                if (key !in requiredKeys) return@forEach
                require(key !in fields) { "The Sony QR code contains duplicate $key fields." }
                fields[key] = token.substring(separator + 1)
            }

        val missing = requiredKeys - fields.keys
        require(missing.isEmpty()) {
            "The Sony QR code is missing ${missing.sorted().joinToString()}."
        }

        val suffix = fields.getValue("S").trim()
        val password = fields.getValue("P")
        val model = fields.getValue("C").trim()
        val identity = fields.getValue("M")
            .replace(":", "")
            .replace("-", "")
            .uppercase(Locale.US)

        require(suffix.isNotEmpty() && suffix.length <= 16 && ':' !in suffix) {
            "The Sony QR code contains an invalid Wi-Fi suffix."
        }
        require(model.isNotEmpty() && model.length <= 24 && ':' !in model) {
            "The Sony QR code contains an invalid camera model."
        }
        require(password.length in 8..63) {
            "The Sony QR code contains an invalid Wi-Fi password."
        }
        require(identity.matches(Regex("[0-9A-F]{12}"))) {
            "The Sony QR code contains an invalid camera identity."
        }

        val payload = SonyWifiQrPayload(
            ssidSuffix = suffix,
            password = password,
            cameraModel = model,
            cameraIdentity = identity,
        )
        require(payload.ssid.toByteArray(Charsets.UTF_8).size <= 32) {
            "The Sony QR code produces an invalid Wi-Fi name."
        }
        return payload
    }

    fun cameraIdentityFromUdn(udn: String): String? =
        Regex("(?i)([0-9a-f]{12})$")
            .find(udn.trim())
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.US)
}
