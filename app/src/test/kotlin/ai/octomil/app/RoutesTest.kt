package ai.octomil.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests that navigation route constants have expected values and are unique.
 *
 * This matters because these strings are used as deep link components,
 * NavHost destinations, and bottom-nav selection checks. If they change
 * accidentally, navigation silently breaks.
 */
class RoutesTest {

    @Test
    fun `HOME route has expected value`() {
        assertEquals("home", Routes.HOME)
    }

    @Test
    fun `PAIR route has expected value`() {
        assertEquals("pair", Routes.PAIR)
    }

    @Test
    fun `MODEL_DETAIL route has expected value`() {
        assertEquals("model_detail", Routes.MODEL_DETAIL)
    }

    @Test
    fun `SETTINGS route has expected value`() {
        assertEquals("settings", Routes.SETTINGS)
    }

    @Test
    fun `all routes are unique`() {
        val routes = listOf(Routes.HOME, Routes.PAIR, Routes.MODEL_DETAIL, Routes.SETTINGS)
        assertEquals(
            "All route constants must be unique",
            routes.size,
            routes.toSet().size,
        )
    }

    @Test
    fun `routes do not contain slashes or special characters`() {
        val routes = listOf(Routes.HOME, Routes.PAIR, Routes.MODEL_DETAIL, Routes.SETTINGS)
        for (route in routes) {
            assert(!route.contains("/")) { "Route '$route' should not contain slashes" }
            assert(!route.contains("?")) { "Route '$route' should not contain query markers" }
            assert(!route.contains("{")) { "Route '$route' should not contain path params" }
        }
    }
}
