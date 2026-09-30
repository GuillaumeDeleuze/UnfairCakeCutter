# Unfair Cake Cutter

Point your phone's rear camera at a cake and it draws cutting guides over it, live. The cake is split into N slices, and an adjustable "unfairness" level decides how uneven they are.

Kotlin · Jetpack Compose · Material 3 · CameraX · single activity · min SDK 26.

The app only asks for the camera permission. It has no network access and no analytics.

## Requirements

- JDK 17 or newer. The JDK bundled with Android Studio works.
- Android SDK with **Platform 35** and Build-Tools. Android Studio Ladybug (2024.2) or newer installs them for you.
- Tell Gradle where the SDK is. Either set `ANDROID_HOME`, or create `local.properties` at the project root:

  ```properties
  sdk.dir=/path/to/Android/Sdk
  ```

  Android Studio writes this file for you when you open the project.

## Build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To run the unit tests (share algorithm, geometry and gesture maths):

```bash
./gradlew testDebugUnitTest
```

## Run it on a device

1. On the phone, turn on **Developer options**: go to *Settings › About phone* and tap *Build number* seven times.
2. In *Settings › System › Developer options*, turn on **USB debugging**. On Android 11 and newer you can use **Wireless debugging** instead.
3. Connect the phone and accept the "Allow USB debugging?" prompt. Then check that the phone is listed:

   ```bash
   adb devices
   ```

4. Install and start the app:

   ```bash
   ./gradlew installDebug
   adb shell am start -n com.unfaircake.cutter/.MainActivity
   ```

   If you already built the APK, `adb install -r app/build/outputs/apk/debug/app-debug.apk` does the same job.

   In Android Studio, pick the device in the toolbar and press **Run**.

An emulator also works. Its virtual rear camera shows a 3D room, so the Photo button is the easier way to test there.

## Using it

- **People**: choose 1 to 12 people (default 6).
- **Unfairness**: set it from 0 (equal slices) to 100. A verdict label sums up the level, and a line below says how many times bigger the biggest slice is than the smallest.
- **Reroll**: deals a new random distribution at the same unfairness level.
- **Shape**:
  - **Round**: a circle or oval, cut into wedges.
  - **Rectangle**, with three layouts:
    - **Strips**: parallel cuts.
    - **Grid**: near-square pieces.
    - **Log**: slices across the length only. Labels move outside and alternate above and below when slices are thin.
- **Gestures**:
  - Drag a **corner handle** to stretch the width or height. The opposite corner stays put.
  - Drag with **one finger** inside the cake to move it.
  - **Pinch** to resize and **twist** to rotate.
  - The Size and Rotation sliders stay in sync with your gestures.
- **Freeze**: pauses the frame so you can check the cuts. Tap **Resume** to go live again.
- **Photo**: opens the system Photo Picker and uses the chosen picture instead of the camera. The overlay works the same way on it. Tap **Camera** to go back.
- Tap a person's name in the list to edit it.

Rotation and process death keep these settings: people count, unfairness, names, random seeds and the cake outline. A frozen frame and a picked photo last only for the current session.

## How the shares are computed

Each person gets a random seed `zᵢ ∈ [-1, 1]`. With unfairness `u`:

```
k       = 1.8 × (u / 100)^1.2
weightᵢ = exp(k × zᵢ)
shareᵢ  = weightᵢ / Σ weights
```

- At `u = 0`, every slice is the same size. At `u = 100`, the biggest slice can be up to e^3.6 ≈ 36× the smallest.
- Adding people adds new seeds. Removing people keeps the existing seeds, so adding them back restores the same slices.
- **Reroll** regenerates every seed.
- Percentages under 10 % show one decimal. From 10 % up they are rounded to a whole number.

Geometry:

- **Round/oval**: wedge angles are computed on a unit circle, then scaled to the ellipse. Scaling multiplies every area by the same factor, so each slice's area stays proportional to its share.
- **Grid**: uses a squarified treemap (Bruls et al.).
- **Strips and logs**: each slice's thickness along the long side is proportional to its share.

## Project layout

```
app/src/main/java/com/unfaircake/cutter/
├── MainActivity.kt              edge-to-edge, sets the Compose content
├── domain/                      pure Kotlin, no Android imports
│   ├── Shares.kt                share algorithm, verdicts, formatting
│   ├── Geometry.kt              wedges, strips, squarified treemap
│   └── ShapeTransform.kt        outline position and gesture maths
└── ui/
    ├── CakeViewModel.kt         one immutable CakeUiState via StateFlow, SavedStateHandle
    ├── CakeApp.kt               screen, permission flow, photo picker, preview buttons
    ├── camera/                  CameraX preview + freeze, photo decoding
    ├── overlay/CakeOverlay.kt   Canvas drawing and gestures
    ├── controls/ControlPanel.kt people, unfairness, shape, list
    └── theme/                   light/dark colour schemes, slice colours
app/src/test/…/domain/           JUnit tests
```

The app is portrait only. It draws edge to edge: the preview runs under the status bar, and the control panel stops above the navigation bar and the keyboard.
