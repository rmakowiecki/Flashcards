import { HttpError } from "./httpError";

const SCRIBE_ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text";
const SCRIBE_MODEL_ID = "scribe_v1";

interface ScribeResponse {
  text: string;
}

/**
 * Forwards the obfuscated WAV to ElevenLabs Scribe and returns the raw transcript.
 * The audio buffer is never written to disk and is discarded once this call returns
 * (design doc's "Data retention" section) — the caller must not persist `wavBuffer`.
 * The request is aborted after `timeoutMs`, and that ends the call with a 504.
 */
export async function transcribeWithElevenLabsScribe(
  wavBuffer: Buffer,
  apiKey: string,
  timeoutMs: number,
): Promise<string> {
  const formData = new FormData();
  formData.append("model_id", SCRIBE_MODEL_ID);
  formData.append("file", new Blob([wavBuffer], { type: "audio/wav" }), "answer.wav");

  // The timer covers reading the body too: fetch resolves once the headers arrive.
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  let parsed: ScribeResponse;
  try {
    const response = await fetch(SCRIBE_ENDPOINT, {
      method: "POST",
      headers: { "xi-api-key": apiKey },
      body: formData,
      signal: controller.signal,
    });
    if (!response.ok) {
      const body = await response.text();
      console.error(`ElevenLabs Scribe error (${response.status}):`, body.slice(0, 500));
      throw new HttpError(502, "Transcription service error");
    }
    parsed = (await response.json()) as ScribeResponse;
  } catch (error) {
    if (controller.signal.aborted) {
      throw new HttpError(504, `Transcription timed out after ${timeoutMs} ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }

  if (!parsed.text) {
    throw new HttpError(502, "ElevenLabs Scribe returned no transcript text");
  }
  return parsed.text;
}
