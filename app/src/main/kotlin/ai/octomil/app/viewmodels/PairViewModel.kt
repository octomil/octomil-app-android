package ai.octomil.app.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight state holder for pairing code entry in the app.
 *
 * The actual pairing flow is handled by the SDK's [ai.octomil.pairing.ui.PairingViewModel].
 * This ViewModel only tracks whether a pairing code has been received (e.g., via deep link
 * or local HTTP server) so the navigation layer can route to the SDK's PairingScreen.
 */
class PairViewModel : ViewModel() {

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode

    private val _host = MutableStateFlow<String?>(null)
    val host: StateFlow<String?> = _host

    fun setPairingCode(code: String, host: String? = null) {
        _pairingCode.value = code
        _host.value = host
    }

    fun reset() {
        _pairingCode.value = null
        _host.value = null
    }
}
