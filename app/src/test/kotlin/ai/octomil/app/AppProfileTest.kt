// Tests for AppProfile / AppProfileResolver in the Android companion app.
//
// Mirrors the cross-repo profile suite (octomil-python, octomil-node,
// octomil-browser, octomil-ios SDK, octomil-android SDK, octomil-app-ios).

package ai.octomil.app

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppProfileTest {

    // ── rawValues match SDK ─────────────────────────────────────────

    @Test
    fun `rawValues match SDK manifest names`() {
        assertEquals("production", AppProfile.Production.rawValue)
        assertEquals("staging", AppProfile.Staging.rawValue)
        assertEquals("dev", AppProfile.Dev.rawValue)
    }

    // ── URL constants ───────────────────────────────────────────────

    @Test
    fun `production URL does not contain 'staging'`() {
        // Critical safety pin — if production URL ever drifts to a
        // staging-shaped URL, every user defaults to staging on
        // first launch.
        val url = AppProfile.Production.defaultHostUrl
        assertFalse(url.contains("staging"))
        assertEquals("https://api.octomil.com", url)
    }

    @Test
    fun `staging URL is distinct from production`() {
        assertEquals("https://api.staging.octomil.com", AppProfile.Staging.defaultHostUrl)
        assertNotEquals(AppProfile.Production.defaultHostUrl, AppProfile.Staging.defaultHostUrl)
    }

    @Test
    fun `dev URL is localhost-shaped`() {
        assertTrue(AppProfile.Dev.defaultHostUrl.startsWith("http://localhost"))
    }

    @Test
    fun `each profile has a distinct URL`() {
        val urls = AppProfile.values().map { it.defaultHostUrl }.toSet()
        assertEquals(AppProfile.values().size, urls.size)
    }

    // ── displayName ─────────────────────────────────────────────────

    @Test
    fun `displayNames are operator-friendly`() {
        assertEquals("Production", AppProfile.Production.displayName)
        assertEquals("Staging", AppProfile.Staging.displayName)
        assertEquals("Local Dev", AppProfile.Dev.displayName)
    }

    // ── AppProfile.from ────────────────────────────────────────────

    @Test
    fun `from accepts canonical names`() {
        assertEquals(AppProfile.Production, AppProfile.from("production"))
        assertEquals(AppProfile.Staging, AppProfile.from("staging"))
        assertEquals(AppProfile.Dev, AppProfile.from("dev"))
    }

    @Test
    fun `from is case-insensitive`() {
        assertEquals(AppProfile.Staging, AppProfile.from("STAGING"))
        assertEquals(AppProfile.Staging, AppProfile.from("Staging"))
    }

    @Test
    fun `from accepts aliases`() {
        assertEquals(AppProfile.Production, AppProfile.from("prod"))
        assertEquals(AppProfile.Staging, AppProfile.from("stg"))
    }

    @Test
    fun `from returns null for unknown or empty`() {
        assertNull(AppProfile.from("preview"))
        assertNull(AppProfile.from(""))
        assertNull(AppProfile.from("   "))
    }

    // ── AppProfileResolver — env ───────────────────────────────────

    @Test
    fun `resolveDefault picks staging from env`() {
        assertEquals(
            AppProfile.Staging,
            AppProfileResolver.resolveDefault(mapOf("OCTOMIL_PROFILE" to "staging"))
        )
    }

    @Test
    fun `resolveDefault empty profile falls through`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(mapOf("OCTOMIL_PROFILE" to ""))
        )
    }

    @Test
    fun `resolveDefault is case-insensitive`() {
        assertEquals(
            AppProfile.Staging,
            AppProfileResolver.resolveDefault(mapOf("OCTOMIL_PROFILE" to "STAGING"))
        )
    }

    @Test
    fun `resolveDefault unknown profile falls through silently`() {
        // Unknown env value must NOT crash the app boot.
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(mapOf("OCTOMIL_PROFILE" to "preview"))
        )
    }

    // ── AppProfileResolver — URL inference ─────────────────────────

    @Test
    fun `resolveDefault infers staging from OCTOMIL_API_BASE`() {
        assertEquals(
            AppProfile.Staging,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://api.staging.octomil.com/v1")
            )
        )
    }

    @Test
    fun `resolveDefault infers production from OCTOMIL_API_URL`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_URL" to "https://api.octomil.com")
            )
        )
    }

    @Test
    fun `resolveDefault infers dev from localhost`() {
        assertEquals(
            AppProfile.Dev,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "http://localhost:8000")
            )
        )
    }

    @Test
    fun `resolveDefault env profile overrides URL inference`() {
        assertEquals(
            AppProfile.Staging,
            AppProfileResolver.resolveDefault(
                mapOf(
                    "OCTOMIL_PROFILE" to "staging",
                    "OCTOMIL_API_BASE" to "https://api.octomil.com",
                )
            )
        )
    }

    @Test
    fun `resolveDefault unmatched URL falls through`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://example.com/api")
            )
        )
    }

    // ── default ─────────────────────────────────────────────────────

    @Test
    fun `resolveDefault no signals returns production`() {
        assertEquals(AppProfile.Production, AppProfileResolver.resolveDefault(emptyMap()))
    }

    // ── defaultServerUrlString convenience ─────────────────────────

    @Test
    fun `defaultServerUrlString picks staging`() {
        assertEquals(
            "https://api.staging.octomil.com",
            AppProfileResolver.defaultServerUrlString(mapOf("OCTOMIL_PROFILE" to "staging"))
        )
    }

    @Test
    fun `defaultServerUrlString defaults to production`() {
        assertEquals(
            "https://api.octomil.com",
            AppProfileResolver.defaultServerUrlString(emptyMap())
        )
    }

    // ── Hostile-URL inference safety (codex post-debate B1) ────────

    @Test
    fun `marker in query string does not spoof profile`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://evil.test/?next=api.staging.octomil.com")
            )
        )
    }

    @Test
    fun `marker in path does not spoof profile`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://evil.test/api.octomil.com/v1")
            )
        )
    }

    @Test
    fun `marker in userinfo does not spoof profile`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://api.staging.octomil.com@evil.test/v1")
            )
        )
    }

    @Test
    fun `superdomain does not spoof production`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "https://api.octomil.com.evil.test/v1")
            )
        )
    }

    @Test
    fun `unparseable URL falls through safely`() {
        assertEquals(
            AppProfile.Production,
            AppProfileResolver.resolveDefault(
                mapOf("OCTOMIL_API_BASE" to "not a url")
            )
        )
    }

    // ── Whitespace fallback (codex post-debate N1) ─────────────────

    @Test
    fun `whitespace API_BASE falls back to API_URL`() {
        assertEquals(
            AppProfile.Staging,
            AppProfileResolver.resolveDefault(
                mapOf(
                    "OCTOMIL_API_BASE" to "   ",
                    "OCTOMIL_API_URL" to "https://api.staging.octomil.com",
                )
            )
        )
    }
}
