# 회의록 작업실 0.1 prototype

This Android source supports microphone recording while the app is open, manual speaker/utterance entry, XLSX generation, and sharing to a recipient-prefilled email app. Sending requires the user to confirm in the email app and requires internet.

**Incomplete:** Korean speech recognition, automatic speaker diarization, long-running background recording, transcription review, automatic email sending. This build does not satisfy the full requested feature set. Open in Android Studio (SDK 35), run `:app:assembleDebug`. An APK has not been built or tested here.

Next: integrate the official sherpa-onnx Android diarization example and Korean ASR models, align diarization segments with text, provide correction UI, then benchmark with an actual 15-minute meeting. Reference: https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/android.html
