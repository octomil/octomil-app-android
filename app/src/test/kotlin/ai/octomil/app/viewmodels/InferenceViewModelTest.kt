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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for [InferenceViewModel] state management.
 *
 * The InferenceViewModel calls OctomilClient.createBenchmarkCollector()
 * and OctomilClient.downloadModel() which are pending SDK API additions.
 * We test the initial state contracts and the state properties that are
 * managed purely by the ViewModel.
 *
 * Once those client methods are available, expand these tests to cover
 * the full loadAndBenchmark success/error paths using mocked client.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InferenceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: InferenceViewModel
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

        viewModel = InferenceViewModel()
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
    fun `initial isLoading is false`() {
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `initial result is null`() {
        assertNull(viewModel.result.value)
    }

    @Test
    fun `initial error is null`() {
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initial isModelLoaded is false`() {
        assertFalse(viewModel.isModelLoaded)
    }

    // =========================================================================
    // StateFlow accessibility
    // =========================================================================

    @Test
    fun `isLoading StateFlow is initialized and accessible`() {
        val flow = viewModel.isLoading
        assertFalse(flow.value)
    }

    @Test
    fun `result StateFlow is initialized and accessible`() {
        val flow = viewModel.result
        assertNull(flow.value)
    }

    @Test
    fun `error StateFlow is initialized and accessible`() {
        val flow = viewModel.error
        assertNull(flow.value)
    }
}
