# Stealth Flashlight — private app area behind a torch switch

An Android app whose default screen is an ordinary flashlight, and whose private app launcher
opens only after you hold the switch for five continuous seconds.

Kotlin · Jetpack Compose · `CameraManager` torch mode · `EncryptedSharedPreferences` ·
`BiometricPrompt`. No networking code, no analytics, no camera permission.

---

## 1. What the reference video turned out to be

The spec asked me to find the reference clip in the project folder. The folder was **empty**,
and the only video attached was named:

```
From Klickpin.com- Refresh these budget-friendly soft life quote ideas everyone will ask you
about using simple ideas you can actually pull off ad.mp4
```

Despite that title, the file is the flashlight UI. It is H.264, 720×1280, 25 fps, 11.05 s,
with an AAC audio track. It orbits a 3D camera around a pill-shaped ON/OFF switch and settles
head-on for the last three seconds, alternating the two switch states.

It now lives at `reference/flashlight-ui-reference.mp4`, with the analysis in
[reference/DESIGN_SPEC.md](reference/DESIGN_SPEC.md) and the key frames alongside it.

Two things the clip settled that a guess would have got wrong:

- **The knob slides up to turn the light on.** It rests at the bottom when off and rises to the
  top when on, and it always carries the label of the half it occupies — so the word under your
  thumb is the action you are about to take, and the exposed word is the current state.
- **The 18-dot column is the hold meter.** It fills bottom-to-top across the settled frames.
  That is the "subtle progress indicator" the spec asked for, so the gesture reuses it instead
  of adding a ring or a bar.

The one place the reference could not be reproduced: the dot column is bracketed by two short
captions that the video's framing crops to `ER` and `SÜ`, so the intended words are not
recoverable. They are implemented as `HOLD` and a live seconds countdown in the same
typographic slots.

## 2. Build

### Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android Studio | Ladybug or newer, or SDK platform 35 + build-tools 35.x |
| Gradle | 8.9 (wrapper is committed, so `./gradlew` works out of the box) |
| Kotlin | 2.0.21 |

`ANDROID_HOME` must point at an SDK with `platforms;android-35` and `build-tools;35.0.0`.

### Where the wrapper came from

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` were fetched over HTTPS from
the official `gradle/gradle` repository at the immutable tag `v8.9.0`, not copied from a third
party. Recorded so you can verify them independently before you let a build script run on your
machine:

```
gradlew                             8784 B  9cbbb4d68ff7fb5211c4d58f598ac9d8664c05fdcd1e5f59b7f2c3ac1ee00af0
gradlew.bat                         2894 B  0f3ed8f03b50934cb8c48b15a470d5c20a30a5385825e48b55bcc8ea3d8f8e18
gradle/wrapper/gradle-wrapper.jar  43504 B  498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17
```

If you would rather regenerate them yourself, delete the three files and run
`gradle wrapper --gradle-version 8.9` with any Gradle on `PATH`.

### Build and install

```bash
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleRelease        # minified; add your own signingConfig before shipping
./gradlew :app:testDebugUnitTest      # the 21 pure unit tests
./gradlew :app:lintDebug
```

## 3. Using it

1. Tap the switch, or give it a short press, to toggle the LED.
2. **Press and hold the switch for five seconds.** The dot column fills bottom-to-top and the
   caption counts down. Release early and nothing happens — not even a torch toggle.
3. Set a 4–12 digit PIN on first entry, then confirm it once.
4. Inside, add apps to the list and launch them. Back, or leaving the app, locks it again and
   returns you to the flashlight.

## 4. How the five-second gesture is built

`HoldGestureTracker` ([gesture/HoldGestureTracker.kt](app/src/main/java/com/qoder/stealthflash/gesture/HoldGestureTracker.kt))
is a pure state machine with no Android or Compose types, driven by an injected clock, which is
why it is unit-testable. The rules:

| Input | Result |
| --- | --- |
| release ≤ 250 ms, inside slop | toggle the torch |
| release 250 ms – 5 s | **nothing** — no toggle, no unlock |
| hold ≥ 5 s, still pressed | unlock, once per press |
| finger leaves the pill + slop | hold interrupted, release is swallowed |
| pointer stream stolen | hold interrupted |
| second finger during a hold | ignored, timer keeps its original start |

A stationary finger emits no further pointer events on Android, so the timer is advanced by a
frame clock (`withFrameMillis`) rather than by event arrival — see
[`runHoldGesture`](app/src/main/java/com/qoder/stealthflash/ui/flashlight/TorchSwitch.kt).

The dead band between the tap window and the threshold is deliberate: it is what stops a
failed unlock attempt from also flipping the light on.

**Accessibility.** TalkBack cannot reliably perform a five-second continuous drag, so the
switch exposes a real `toggleable` role plus an explicit "Open private area" custom action.
Neither path is reachable by accident.

## 5. What the private area actually does — and does not do

It is **Option A: a private launcher.** A PIN-gated list of apps you choose, which you can
search, sort and launch. This is the only approach that genuinely works on an unrooted phone
running an ordinary app.

**Removing an app from this list does not hide its icon anywhere else.** It only takes it off
this list. The app shows you this in-app under "How this works" rather than letting you believe
something false.

To actually hide launcher icons you would need one of:

- **Being the default launcher** (Option B) — implementable, but the app would then own the
  home screen and app drawer, and hiding only works while it is the default.
- **Device-owner / managed profile** (Option C) via `DevicePolicyAdmin.setPackagesSuspended` —
  requires provisioning the device with `adb shell dpm set-device-owner` or an MDM, so on a
  normal phone it reads as broken.

No root, no accessibility-service abuse, no deceptive permission prompts, no system
modification. Say the word and I will build out B or C as an opt-in mode.

## 6. Permissions and privacy

| | |
| --- | --- |
| Declared permissions | `USE_BIOMETRIC` only |
| Camera permission | **not requested** — `setTorchMode()` does not need it |
| `QUERY_ALL_PACKAGES` | **not used** — a `<queries>` element restricts visibility to apps with a launcher entry |
| Network | no `INTERNET` permission, no networking code |
| Backup | `allowBackup=false`, plus `data_extraction_rules` excluding prefs, DB and files |

- PINs are never stored. Only a salted PBKDF2-HMAC-SHA256 digest (120 000 iterations, 16-byte
  `SecureRandom` salt) goes into `EncryptedSharedPreferences`, under an AES-256-GCM master key
  in the Android Keystore. Comparison is constant-time.
- Wrong PINs back off exponentially after 5 attempts, and the counter survives a restart.
- Leaving the app locks the private area and returns to the flashlight screen, so no private
  content is left in a task snapshot.
- The installed-app list is read from the local `PackageManager` and rendered; only the package
  names *you* add are persisted.

## 7. Torch behaviour and edge cases

`TorchController` ([torch/TorchController.kt](app/src/main/java/com/qoder/stealthflash/torch/TorchController.kt))
picks the rear camera that reports `FLASH_INFO_AVAILABLE`, and registers a
`CameraManager.TorchCallback` while the screen is visible. The callback — not a remembered
boolean — is the authority on the LED state, because the OS silently drops torch ownership the
moment another client opens the camera. `onTorchModeUnavailable` therefore flips the switch off
and surfaces "another app is using the camera".

`ON_STOP` forces the LED off and unregisters the callback, so the switch is never left showing
a state the hardware is not in. Failures are classified into `CameraInUse`, `CameraDisabled`,
`PermissionDenied` and `Hardware`; `getReason()` is guarded behind an API 29 check because it
does not exist on 26–28.

A device with no flash gets a disabled, desaturated switch and an explanatory line — no crash,
no dead button that looks live.

## 8. Testing status — read this before trusting the build

**Verified by execution.** 21 unit tests, compiled with Kotlin 2.0.21 on JDK 17 and run on the
JVM, all green:

- [`HoldGestureTrackerTest`](app/src/test/java/com/qoder/stealthflash/gesture/HoldGestureTrackerTest.kt)
  — 14 cases: tap, full hold, early release, once-per-press completion, repeated taps,
  interrupted touch resetting elapsed time, second finger, monotonic clamped progress, meter
  fill order, out-of-order clock readings, release without press, reset.
- [`PinHasherTest`](app/src/test/java/com/qoder/stealthflash/vault/PinHasherTest.kt)
  — 7 cases: round trip, salt uniqueness, length bounds, non-digit rejection, corrupt seals,
  cross-PIN rejection.

Resource references were cross-checked: every `R.string`, `@style`, `@xml`, `@mipmap` and
`@drawable` resolves.

**Not verified.** This machine had no JDK and no Android SDK, and you chose the partial check,
so the Compose UI, the camera integration, `EncryptedSharedPreferences`, `BiometricPrompt` and
the `PackageManager` queries have **never been compiled or run**. Treat the first
`assembleDebug` in Android Studio as likely to surface some compile errors in those layers.
Everything that could be checked without an SDK has been.

**Needs a physical device.** Torch on/off, the five-second hold under a real finger, camera
contention with the system camera app, biometric enrollment, no-flash hardware, and behaviour
across screen sizes and Android versions. The checklist in section 8 of the brief maps onto:

| Requirement | Covered by |
| --- | --- |
| Five-second continuous long press | unit tests |
| Cancellation when released early | unit tests |
| Accidental double taps / interrupted touches | unit tests |
| Authentication and wrong-PIN handling | unit tests (hashing) + on-device (flow, lockout) |
| Flashlight on/off | on-device only |
| Opening/closing the private area | on-device only |
| App-list loading and search | on-device only |
| Behaviour after restart | on-device only (lockout counter and PIN are persisted) |
| Devices without flashlight hardware | on-device only |
| Permission denial and camera conflicts | on-device only |
| Screen sizes and Android versions | on-device only |

## 9. Layout

```
app/src/main/java/com/qoder/stealthflash/
  MainActivity.kt                  FragmentActivity; navigation, lifecycle, edge-to-edge
  StealthFlashApp.kt               app-scoped singletons
  gesture/HoldGestureTracker.kt    pure 5-second state machine
  torch/TorchController.kt         CameraManager torch mode + TorchCallback authority
  flashlight/FlashlightViewModel.kt
  vault/PinHasher.kt               PBKDF2, constant-time compare
  vault/VaultStore.kt              EncryptedSharedPreferences
  vault/InstalledAppsRepository.kt PackageManager launcher query, icon cache
  vault/BiometricAuthenticator.kt  BiometricPrompt wrapper
  vault/VaultViewModel.kt          auth state machine + list/search/sort
  ui/flashlight/TorchSwitch.kt     canvas switch, sampled geometry and palette
  ui/flashlight/FlashlightScreen.kt
  ui/vault/PinScreen.kt            PIN pad
  ui/vault/VaultScreen.kt          private launcher list
  ui/theme/                        sampled tokens; Material only for the private area
reference/                         source clip, extracted frames, DESIGN_SPEC.md
```

The torch screen is drawn from measured tokens rather than a Material palette; the private area
uses plain dark Material controls on purpose, so there is never any doubt which interface you
are standing in.
