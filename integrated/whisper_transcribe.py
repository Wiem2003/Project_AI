import sys
import os
import json
import whisper

def transcribe_audio(audio_path, model_name="base"):
    try:
        if not os.path.exists(audio_path):
            print(json.dumps({"error": f"Fichier audio non trouvé: {audio_path}"}))
            sys.exit(1)

        model = whisper.load_model(model_name)
        result = model.transcribe(audio_path, fp16=False)
        text = result.get("text", "").strip()
        print(json.dumps({"text": text, "language": result.get("language", "fr")}, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: python whisper_transcribe.py <audio_file_path> [model_name]"}))
        sys.exit(1)

    file_path = sys.argv[1]
    model = sys.argv[2] if len(sys.argv) > 2 else "base"
    transcribe_audio(file_path, model)
