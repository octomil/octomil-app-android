// Companion-app environment profile resolution.
//
// The OctomilApp Android companion talks to one of three Octomil
// environments (production, staging, dev). This file is the
// single source of truth FOR THE APP for which environment to
// default to when the user hasn't pinned a custom server URL.
//
// Why duplicate SDK Profile?
// The Android SDK ships its own OctomilProfile / OctomilProfileResolver
// (see octomil-android PR #248). Once that PR merges and is published
// to a tag this app pins via Gradle, this file should be replaced
// with a thin wrapper around `ai.octomil.config.OctomilProfileResolver`.
// Until then — and to land this companion-app change INDEPENDENTLY of
// the SDK PR — the app keeps its own copy of the URL constants.
// **Any change here MUST be mirrored in the SDK** or the SDK boots
// into a different env than the app's UI shows.
//
// Mirrors octomil-app-ios PR #26 in Kotlin.

package ai.octomil.app

import java.net.URI
import java.net.URISyntaxException

/** Named environments the companion app can talk to. */
enum class AppProfile(val rawValue: String, val displayName: String) {
    Production("production", "Production"),
    Staging("staging", "Staging"),
    Dev("dev", "Local Dev");

    /**
     * Mirrors `octomil-android/.../config/Profile.kt`'s
     * OctomilProfileResolver.HOST_URLS map. Keep in lockstep.
     */
    val defaultHostUrl: String
        get() = when (this) {
            Production -> "https://api.octomil.com"
            Staging -> "https://api.staging.octomil.com"
            Dev -> "http://localhost:8000"
        }

    companion object {
        private val ALIASES = mapOf(
            "prod" to "production",
            "stg" to "staging",
            "staging-2" to "staging",
        )

        /** Case-insensitive lookup with `prod`/`stg` aliases. Returns null on unknown. */
        @JvmStatic
        fun from(raw: String): AppProfile? {
            val normalized = raw.trim().lowercase()
            if (normalized.isEmpty()) return null
            val resolved = ALIASES[normalized] ?: normalized
            return values().firstOrNull { it.rawValue == resolved }
        }
    }
}

/**
 * Static helpers for picking the default server URL when the user
 * hasn't configured one explicitly.
 *
 * Resolution order:
 *   1. OCTOMIL_PROFILE env var if set.
 *   2. OCTOMIL_API_BASE / OCTOMIL_API_URL host inference.
 *   3. Default Production.
 *
 * Note: companion app does not accept an "explicit profile" arg the
 * way the SDK does. Users override the URL via Settings; this
 * resolver only picks the FIRST-RUN default before the user has
 * configured anything.
 */
object AppProfileResolver {
    @JvmStatic
    @JvmOverloads
    fun resolveDefault(environment: Map<String, String>? = null): AppProfile {
        val env = environment ?: System.getenv()

        val rawEnv = (env["OCTOMIL_PROFILE"] ?: "").trim()
        if (rawEnv.isNotEmpty()) {
            AppProfile.from(rawEnv)?.let { return it }
        }

        // Trim BEFORE selecting so a whitespace OCTOMIL_API_BASE
        // doesn't mask a valid OCTOMIL_API_URL (codex post-debate N1).
        val baseTrimmed = (env["OCTOMIL_API_BASE"] ?: "").trim()
        val urlTrimmed = (env["OCTOMIL_API_URL"] ?: "").trim()
        val url = baseTrimmed.ifEmpty { urlTrimmed }
        inferFromUrl(url)?.let { return it }

        return AppProfile.Production
    }

    /**
     * Convenience: the profile-aware default URL string for use as
     * the SharedPreferences "server_url" initial value when the user
     * hasn't set one yet.
     */
    @JvmStatic
    @JvmOverloads
    fun defaultServerUrlString(environment: Map<String, String>? = null): String =
        resolveDefault(environment).defaultHostUrl

    private fun inferFromUrl(raw: String): AppProfile? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Use URI to parse; substring matching the raw URL would let
        // evil.test/?next=api.staging.octomil.com or
        // api.octomil.com.evil.test spoof the first-run server URL
        // (codex post-debate B1).
        val host = try {
            URI(trimmed).host?.lowercase()
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (host.isNullOrEmpty()) return null
        // Exact-host markers — staging FIRST (more specific).
        val markers = listOf(
            AppProfile.Staging to setOf("api.staging.octomil.com"),
            AppProfile.Production to setOf("api.octomil.com"),
            AppProfile.Dev to setOf("localhost", "127.0.0.1", "0.0.0.0"),
        )
        for ((profile, ms) in markers) {
            if (host in ms) return profile
        }
        return null
    }
}
