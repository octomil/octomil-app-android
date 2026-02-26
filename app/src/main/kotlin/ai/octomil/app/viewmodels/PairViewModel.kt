package ai.octomil.app.viewmodels

import ai.octomil.app.OctomilApplication
import ai.octomil.client.pairing.PairingSession
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PairViewModel : ViewModel() {

    private val _session = MutableStateFlow<PairingSession?>(null)
    val session: StateFlow<PairingSession?> = _session

    fun startPairing(code: String) {
        viewModelScope.launch {
            val client = OctomilApplication.instance.client
            client.pair(code).collect { session ->
                _session.value = session
            }
        }
    }

    fun reset() {
        _session.value = null
    }
}
