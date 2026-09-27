# 회의록 작업실 0.2 experimental

This Android source records 16 kHz mono WAV, runs offline sherpa-onnx speaker diarization and Korean SenseVoice ASR after recording, produces an XLSX, and opens an email app with the recipient prefilled. Sending requires user confirmation and internet. Speaker numbers are anonymous, and the transcription must be reviewed.

**Limits:** recording and processing require the app to remain open; one recording is limited to 20 minutes; no background service, per-line edit UI or automatic email sending. Speaker IDs may be wrong or inconsistent. A 1-hour meeting and on-device speed/accuracy have not been validated. An APK is only distributable if GitHub Actions succeeds; device testing is still required.

The GitHub workflow downloads pinned v1.13.8 Android AAR plus official pyannote segmentation, 3D-Speaker embedding, and Korean-capable SenseVoice int8 model. Large binaries are excluded from git. Run the workflow before attempting a local Gradle build, or populate the same app/libs and assets paths locally. Reference: https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/android.html
