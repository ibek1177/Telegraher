# Preliminary safety audit — New Year 2027

Base: `graher_12.92.1`. This branch is isolated from the release branch.

## Confirmed observations (not necessarily application bugs)
- `README_BUILD.md` labels its own Docker instructions outdated (2026).
- `.github/workflows/graher-workbitch-v1.yml` assumes absolute runner paths and a fixed APK output path. This makes the release pipeline fragile if its layout changes.
- `TMessagesProj/config/release.keystore` is tracked in the public repository. **Do not rotate or remove it blindly**: determine whether it is a production signing key first, because existing Android installs depend on signing continuity.
- `com/evildayz/code/telegraher/ThePenisStuck.java`: `getVendor()` returns `BuildVars.BUILD_VENDOR` unconditionally via `if (true)`; the subsequent installer lookup is unreachable. This may be intentional, so leave unchanged until the intended behavior is confirmed.

## Safe implementation requirements
1. Identify the existing Ghost Mode settings screen and persistence keys before editing it.
2. Introduce a separate, default-OFF seasonal switch; never change existing ghost/privacy settings.
3. Keep snowfall visual-only, non-interactive, and stop animations when views detach or power saving is active.
4. Do not change authentication, networking, message sending/reading, signing, or release workflows as part of a visual feature.
5. Build and test a debug APK before proposing a merge. Do not merge without review.

## Verification status
Source inspection only. No Android build, emulator run, or device tests have been completed. No runtime defect is confirmed.
