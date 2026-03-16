package ai.octomil.app.voice

/**
 * Voice transcription state machine — independent from chat UiState.
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object Recording : VoiceState()
    object LoadingModel : VoiceState()
    object Transcribing : VoiceState()
    data class Error(val message: String) : VoiceState()
}
