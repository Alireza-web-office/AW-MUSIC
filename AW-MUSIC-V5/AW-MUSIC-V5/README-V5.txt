AW MUSIC V5 — NEON PULSE

Version: 5.0.0
Application ID: ir.alirezaweb.awmusic

V5 starts as a clean independent project based on the supplied V4 source.
Included:
- New AW MUSIC neon logo used for the launcher icon and splash screen.
- Existing offline music library/player features carried forward.
- About the creator page retained and updated to AW MUSIC 5.0 / NEON PULSE.
- Playback speed selection changed to a compatible index lookup.
- Playback ticker started when the activity is created.
- GitHub Actions workflow for building a debug APK.

Build:
  gradle -p AW-MUSIC-V5 clean assembleDebug

GitHub:
Put the AW-MUSIC-V5 folder at the repository root, and put the supplied
AW-MUSIC-V5-GITHUB-WORKFLOW.yml contents at .github/workflows/android.yml.

Note: This source package has not been compiled in this environment. Run the
GitHub Actions workflow and review its build result before installing the APK.
