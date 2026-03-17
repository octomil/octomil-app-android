package ai.octomil.app.speech;

import ai.octomil.app.speech.ISpeechCallback;

interface ISpeechService {
    /** Create a streaming session for the given model. */
    void createSession(String modelName, ISpeechCallback callback);

    /** Feed 16kHz mono float PCM samples to the active session. */
    void feedAudio(in float[] samples);

    /** Finalize the session and return the final transcript. */
    String finalizeSession();

    /** Release the active session and free native resources. */
    void releaseSession();
}
