# Instructions for future coding assistants

Before changing this repository, read [docs/ANDROID-AUDIO-NETWORK-HANDOFF.md](docs/ANDROID-AUDIO-NETWORK-HANDOFF.md) when touching Android audio, USB, ZTF, or networking.

## Non-negotiable project scope

- Unless the user explicitly expands scope, change **Android APK/Kotlin only**.
- Do not edit FM-1 firmware, embedded patches, native C/C++ audio engine, or unrelated projects.
- The user's label **SCL means SEL**. Do not report or rename it as a defect.
- Keep work on a dedicated branch; do not rewrite or force-push unrelated branches/PRs.
- Do not claim hardware validation from a successful CI build. State separately what was built, what was tested on a device, and what remains unverified.
- ZuluLink is UDP control/tempo messaging, not an IP audio stream. Do not describe it as network audio.
- USB-MIDI SysEx transport is not PCM audio. Do not treat `smpRec` as a PCM injection API.
- Do not publish private ZuluLink protocol specifications. Document only implementation-relevant facts and known limitations.

## Engineering expectations

- Preserve the 4-byte USB-MIDI event packet framing (CIN/cable header plus three MIDI data bytes).
- Release claimed USB interfaces and close USB connections on every failure and shutdown path.
- Respect Android runtime permissions before starting capture.
- Never block the audio callback on locks or I/O.
- For every change, run the Android CI build and inspect the changed-file list to ensure scope compliance.
