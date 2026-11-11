package com.example.util

/**
 * Redacts API keys, Authorization tokens, project identifiers, hashes, and query parameters
 * before exposing diagnostics or technical error snippets to logs or UI.
 */
fun sanitizeSensitiveDetails(raw: String, explicitSecretToRedact: String? = null): String {
    if (raw.isBlank()) return ""
    var cleaned = raw
    val trimmedExplicit = explicitSecretToRedact?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmedExplicit != null) {
        cleaned = cleaned.replace(trimmedExplicit, "[CLAVE_OCULTA]")
    }
    return cleaned
        .replace(Regex("""AIza[0-9A-Za-z_\-]{10,}"""), "[CLAVE_OCULTA]")
        .replace(Regex("""([?&](?:key|api_key)=)[^&\s]+""", RegexOption.IGNORE_CASE), "$1[CLAVE_OCULTA]")
        .replace(Regex("""(x-goog-api-key\s*[:=]\s*)[^\s,;]+""", RegexOption.IGNORE_CASE), "$1[CLAVE_OCULTA]")
        .replace(Regex("""(Bearer\s+)[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE), "$1[TOKEN_OCULTO]")
        .replace(Regex("""6L[0-9A-Za-z_\-]{10,}"""), "[SITE_KEY_OCULTA]")
        .replace(Regex("""projects/[^/\s,)"']+""", RegexOption.IGNORE_CASE), "projects/[PROYECTO]")
        .replace(Regex("""project\s+\d{6,}""", RegexOption.IGNORE_CASE), "project [PROYECTO]")
        .replace(Regex("""eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}(?:\.[A-Za-z0-9_\-]*)?"""), "[JWT_OCULTO]")
        .replace(Regex("""([0-9A-Fa-f]{2}:){10,}[0-9A-Fa-f]{2}"""), "[HASH_OCULTO]")
        .replace(Regex("""\b\d+:\d+:[a-z]+:[0-9a-fA-F]{6,}\b"""), "[APP_ID]")
        .replace(Regex("""apps/(?!\[APP_ID\])[^/\s,:)"']+""", RegexOption.IGNORE_CASE), "apps/[APP_ID]")
        .replace(Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""), "[TOKEN_OCULTO]")
        .replace(Regex("""\b[0-9A-Fa-f]{32,}\b"""), "[HASH_OCULTO]")
        .replace(Regex("""\b\d{10,}\b"""), "[ID_OCULTO]")
        .trim()
}
