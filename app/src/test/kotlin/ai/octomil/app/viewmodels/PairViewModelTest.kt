package ai.octomil.app.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for [PairViewModel] state management.
 *
 * The PairViewModel is a lightweight state holder for pairing code entry.
 * The actual pairing flow is handled by the SDK's PairingViewModel.
 */
class PairViewModelTest {

    private lateinit var viewModel: PairViewModel

    @Before
    fun setUp() {
        viewModel = PairViewModel()
    }

    // =========================================================================
    // Initial state
    // =========================================================================

    @Test
    fun `initial pairingCode is null`() {
        assertNull(viewModel.pairingCode.value)
    }

    @Test
    fun `initial host is null`() {
        assertNull(viewModel.host.value)
    }

    // =========================================================================
    // setPairingCode
    // =========================================================================

    @Test
    fun `setPairingCode updates code`() {
        viewModel.setPairingCode("ABC123")
        assertEquals("ABC123", viewModel.pairingCode.value)
    }

    @Test
    fun `setPairingCode with host updates both`() {
        viewModel.setPairingCode("ABC123", "https://api.octomil.com")
        assertEquals("ABC123", viewModel.pairingCode.value)
        assertEquals("https://api.octomil.com", viewModel.host.value)
    }

    @Test
    fun `setPairingCode without host leaves host null`() {
        viewModel.setPairingCode("ABC123")
        assertNull(viewModel.host.value)
    }

    // =========================================================================
    // reset
    // =========================================================================

    @Test
    fun `reset clears pairingCode and host`() {
        viewModel.setPairingCode("ABC123", "https://api.octomil.com")
        viewModel.reset()
        assertNull(viewModel.pairingCode.value)
        assertNull(viewModel.host.value)
    }

    @Test
    fun `reset is idempotent`() {
        viewModel.reset()
        viewModel.reset()
        viewModel.reset()
        assertNull(viewModel.pairingCode.value)
        assertNull(viewModel.host.value)
    }

    @Test
    fun `reset on fresh viewModel does not throw`() {
        viewModel.reset()
        assertNull(viewModel.pairingCode.value)
    }
}
