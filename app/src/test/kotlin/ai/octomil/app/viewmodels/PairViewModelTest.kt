package ai.octomil.app.viewmodels

import ai.octomil.app.OctomilApplication
import ai.octomil.client.OctomilClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for [PairViewModel] state management.
 *
 * The PairViewModel delegates pairing to OctomilClient.pair() which returns
 * a Flow<PairingSession>. Since the pair() method is not yet defined on
 * OctomilClient (pending SDK API stabilization), we test only the state
 * management aspects that don't depend on it: initial state and reset.
 *
 * Once the pair() method is added to OctomilClient, expand these tests
 * to cover startPairing flow collection and session state transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PairViewModel
    private lateinit var mockApp: OctomilApplication
    private lateinit var mockClient: OctomilClient

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockClient = mockk(relaxed = true)
        mockApp = mockk(relaxed = true) {
            every { client } returns mockClient
        }

        mockkObject(OctomilApplication.Companion)
        every { OctomilApplication.instance } returns mockApp

        viewModel = PairViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // =========================================================================
    // Initial state
    // =========================================================================

    @Test
    fun `initial session is null`() {
        assertNull(viewModel.session.value)
    }

    @Test
    fun `session StateFlow is accessible and non-null as a flow`() {
        // Verify the StateFlow itself is initialized (not null), even though its value is null
        val flow = viewModel.session
        assertNull(flow.value)
    }

    // =========================================================================
    // reset
    // =========================================================================

    @Test
    fun `reset clears session to null`() {
        viewModel.reset()
        assertNull("Session should be null after reset", viewModel.session.value)
    }

    @Test
    fun `reset is idempotent`() {
        viewModel.reset()
        viewModel.reset()
        viewModel.reset()
        assertNull(viewModel.session.value)
    }

    @Test
    fun `reset on fresh viewModel does not throw`() {
        // This verifies that reset is safe to call without prior pairing
        viewModel.reset()
        assertNull(viewModel.session.value)
    }
}
