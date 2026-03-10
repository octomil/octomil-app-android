package ai.octomil.app.viewmodels

import ai.octomil.app.OctomilApplication
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InferenceViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var isModelLoaded = false
        private set

    fun loadAndBenchmark(modelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _result.value = null

            try {
                val client = OctomilApplication.instance.client

                val loadStart = System.nanoTime()
                val model = client.updateModel()
                val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0

                model.getOrThrow()
                isModelLoaded = true
                _result.value = "Model loaded in ${String.format("%.1f", loadMs)}ms"
            } catch (e: Exception) {
                _error.value = e.message ?: "Benchmark failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
