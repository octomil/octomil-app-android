package ai.octomil.app.speech;

interface ISpeechCallback {
    /** Called when the session is ready for audio input. */
    void onSessionReady();

    /** Called with updated transcript text (partial or final). */
    void onTranscriptUpdate(String text);

    /** Called when an error occurs. */
    void onError(String message);
}
