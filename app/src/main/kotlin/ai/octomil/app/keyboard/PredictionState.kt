package ai.octomil.app.keyboard

/**
 * State for keyboard next-word prediction — independent from chat UiState.
 */
sealed class PredictionState {
    object Idle : PredictionState()
    object Loading : PredictionState()
    data class Ready(val suggestions: List<String>) : PredictionState()
}
