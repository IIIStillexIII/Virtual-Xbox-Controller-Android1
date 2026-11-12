# Virtual Xbox Controller for Android

Android controller bridge that reads supported physical game controllers and exposes a system-wide virtual gamepad through Android's Linux input stack.

This is **my development fork**, not the original repository. The original upstream project is [misakidebugged/Virtual-Xbox-Controller-Android](https://github.com/misakidebugged/Virtual-Xbox-Controller-Android); this repository is [IIIStillexIII/Virtual-Xbox-Controller-Android1](https://github.com/IIIStillexIII/Virtual-Xbox-Controller-Android1). The upstream project is kept as the reference/base, while changes are made in this fork.

## Project goal

The goal of this fork is to keep the original Virtual Xbox Controller app and its overall design as close to upstream as practical, while improving it into a reliable system-wide controller bridge for Android. A generic physical gamepad connected over USB or Bluetooth should be translated into exactly one of two virtual gamepad profiles: **Xbox Series X|S** or **PlayStation 5 DualSense**. Separately, the app retains the **original VXCA Desktop Mode** through the **Controller Mode** selector, alongside **Gamepad**. Desktop (mouse + keyboard) is inherited functionality from VXCA, not a new NexPad feature and not a virtual gamepad profile; preserve its existing behavior and improve it only through focused, compatible changes. The app uses the **root backend first** with **Shizuku as a fallback**, and provides practical mapping for buttons, sticks, triggers and D-pad.

### Original project remains the foundation

This fork must remain based on the original **Virtual Xbox Controller for Android** project by [misakidebugged](https://github.com/misakidebugged/Virtual-Xbox-Controller-Android). The original project's application structure, core architecture, established input and service flows, existing features, and visual design must remain the foundation of the project and be preserved wherever practical.

My changes are intended to improve and extend the original application, not replace it. Development should build on the upstream code and its working systems through focused, compatible improvements. The fork must not turn into a separately designed or unrelated implementation, rewrite functioning subsystems without a demonstrated technical need, or remove existing functionality just to add the new controller profiles. New mapping and compatibility capabilities should be integrated into the original project structure while retaining its behavior, design, and identity. Any necessary architectural changes should be limited to the smallest practical scope and preserve existing features, including Desktop mode.

### Development rule: improve the original, do not replace it

The original Virtual Xbox Controller is the project's foundation and must remain the application we are improving. Before implementing a feature or changing a subsystem, compare the upstream implementation with this fork and verify what already exists and how it currently works. Do not rebuild, duplicate, or replace functionality that the original project already provides unless a concrete defect or limitation has been identified.

Use other projects such as [GPad2Xbox](https://github.com/ZHStore19/GPad2Xbox) only as technical references for specific ideas where useful. They are not a replacement foundation, and their architecture or implementation must not be copied wholesale. Prefer small, targeted changes that integrate with the original Virtual Xbox Controller architecture and preserve its existing UI, Desktop mode, services, and working features.

Every change should have a clear reason, a narrowly defined scope, and a compatibility check against the intended fork behavior. This fork intentionally supports only two virtual gamepad profiles: Xbox Series X|S and PS5 DualSense. Do not restore Xbox 360, Xbox One, or DualShock 4 profiles. Keep Desktop as a separate Controller Mode, not a gamepad profile.

A key target for compatibility testing is **Call of Duty: Mobile (CODM)**, alongside other Android games with controller support. The objective is genuine system-level input-device recognition, **not** an on-screen overlay or touch-control/macro mapping. CODM compatibility is a target to verify on real devices; it is not claimed as guaranteed, and this project does not attempt to bypass a game's anti-cheat or controller restrictions.

This fork intentionally exposes exactly two virtual gamepad profiles:

- **Xbox Series X|S** — Xbox Series-style virtual controller profile.
- **PS5 DualSense** — PlayStation 5 DualSense-style virtual controller profile.

Separately, **Controller Mode** has two operating modes:

- **Gamepad** — use the selected Xbox Series X|S or PS5 DualSense virtual gamepad profile.
- **Desktop (mouse + keyboard)** — the original VXCA Desktop Mode, preserved as an existing Controller Mode for desktop-style mouse/keyboard input.

Desktop is not a third gamepad profile and must not appear in the virtual gamepad profile list. Do not restore Xbox 360, Xbox One, or DualShock 4 profiles.

### CODM compatibility objective: make different physical gamepads usable

Call of Duty: Mobile does not accept every physical gamepad as a supported controller; compatibility is limited to controller families the game recognizes, such as Xbox and PlayStation controllers. The central purpose of this fork is to bridge that gap: take input from as many different physical USB and Bluetooth gamepads as the Android device can read, translate their controls into the selected Xbox Series X|S or PS5 DualSense profile, and expose that profile as a system-level virtual input device for the game.

In practical terms, the intended result is that a player can use a supported generic or third-party physical gamepad in CODM through the virtual Xbox or DualSense profile, even when CODM would not recognize that physical gamepad directly. This requires correct input translation and compatible virtual-device behavior — not merely changing the displayed device name or identifiers. The goal is to achieve this without touch overlays, touch injection, macros, or bypassing anti-cheat or game restrictions.

This is a development and real-device validation target, **not a claim that every controller or Android device already works**. Success must be checked with different physical controllers on actual Android devices and CODM versions. The virtual `uinput` device may not reproduce every hardware/protocol characteristic of a genuine Xbox or PlayStation controller, which can limit recognition.

## How it works

```
Physical controller (USB or Bluetooth, where supported)
                 |
                 v
        Android/Linux input events
                 |
                 v
       Controller mapping pipeline
                 |
                 v
       Virtual device via uinput
                 |
                 v
       Android system input stack
```

The app reads supported physical input devices and maps their buttons, sticks, triggers and D-pad to the selected virtual profile. The virtual device is created through Linux `uinput`.

### Important technical limitation

A virtual `uinput` device is a Linux input-core device. Setting a device name or USB-style vendor/product identifiers does **not** make it a genuine Xbox Series X|S or DualSense USB/Bluetooth HID device, nor does it reproduce those controllers' original hardware protocols. Actual recognition depends on the Android build, kernel input stack and the target app.

In particular, virtual-controller operation does not guarantee that every game — including Call of Duty: Mobile — will accept the device. Compatibility must be verified on the target phone, Android build and game version.

## Input and injection

- **USB:** Steam Controller devices use the project's dedicated Steam USB path. Other supported USB controllers are read through evdev.
- **Bluetooth:** Supported paired Bluetooth gamepads can be selected through the app's Bluetooth transport.
- **Virtual device injection:** The app tries the root backend first. Shizuku is used as a fallback when the root path is unavailable and Shizuku is configured.
- **Hotplug and recovery:** The input pipeline monitors device changes and handles evdev synchronization loss.
- **Calibration:** Stick calibration and sensitivity controls are available in the app.
- **D-pad:** Standard buttons and supported evdev hat axes are mapped to the virtual D-pad.

USB device discovery and auto-launch behavior can vary by Android version and device. The manifest's USB attachment filter currently targets Valve's Steam Controller; generic USB HID devices may need the app to be open so its runtime USB discovery can handle them.

## Requirements

- Android device with USB host support for wired controllers, or Bluetooth support for wireless controllers.
- A supported physical controller.
- For root injection: a device with working root access and kernel support for the required input interfaces.
- For the fallback path: Shizuku installed, running and authorized.

The availability of `/dev/uinput`, evdev device permissions, and related kernel features varies across Android devices. Root access alone does not guarantee that every backend will work.

## Setup

1. Install the app.
2. Connect or pair the physical controller.
3. Open the app and choose USB or Bluetooth transport.
4. Select the desired virtual gamepad profile. Use Desktop mode separately when mouse/keyboard navigation is desired.
5. Start the controller service and check the app's connection/status log.

If injection cannot start, check that the root backend is available or that Shizuku is running and authorized.

## Development status

This project is under active development. CI builds verify that the Android project compiles, but CI does not test physical USB/Bluetooth controllers, root permissions, Shizuku injection on a real device, or compatibility with individual games.

Please include the Android version, device model, controller model, connection type and relevant app logs when reporting a hardware compatibility issue.

## Credits

This project is a fork of [misakidebugged/Virtual-Xbox-Controller-Android](https://github.com/misakidebugged/Virtual-Xbox-Controller-Android). See the source repository and in-project license files for the original project's licensing and attribution.


---

# Engineering handoff and continuity notes

> This section records implementation decisions and the verified repository state so future contributors and AI coding assistants can continue the work without accidentally undoing earlier decisions. When this section conflicts with an older sentence elsewhere in this README, follow this section and update the older sentence in the same documentation change.

## A. Product model: two gamepad profiles plus a separate Controller Mode

This fork intentionally supports **exactly two virtual gamepad profiles**:

1. **Xbox Series X|S**
2. **PS5 DualSense**

Do **not** restore the original Xbox 360, Xbox One, or DualShock 4 virtual profiles. The fork's goal is not backward compatibility with those controller emulations; they are intentionally out of scope.

Controller Mode is a separate application concept with two values:

- **Gamepad** — activates the selected virtual gamepad profile (Xbox Series X|S or PS5 DualSense).
- **Desktop (mouse + keyboard)** — uses the physical controller for desktop-style pointer/keyboard behavior.

Desktop is not a virtual controller profile, not a third gamepad target, and must not be shown in the virtual gamepad profile selector.

### Current implementation detail that must not be confused with the product model

At the last source inspection, `GamepadProfile.kt` contains:

- `XBOX` with ID 0.
- `DUALSENSE` with ID 3.
- `MOUSE` with ID 4 and `isMouseMode = true`.

The `MOUSE` enum entry is an internal legacy representation used by the current implementation to persist/toggle Desktop behavior. It is **not** a third virtual gamepad profile. The UI already presents a separate **Control mode** choice between Gamepad and Desktop, then displays the gamepad profile choices only in Gamepad mode.

When refactoring this area, preserve the user's intended behavior and saved settings for the two supported profiles, but do not reintroduce Xbox 360, Xbox One, or DualShock 4. Prefer making the separation explicit in the model (for example, a dedicated `ControllerMode` enum/state plus a `GamepadProfile` containing only Xbox Series and DualSense) if this can be done safely across preferences, service state, notifications, and native code. Do not perform a partial refactor that breaks Desktop mode or profile selection.

The current IDs 0 and 3 are implementation details already present in the fork. Do not renumber them casually; check every Kotlin/native/JNI/preference reference before changing IDs. The Desktop sentinel ID 4 must not be described as a gamepad profile ID.

## B. Verified fork/upstream comparison (12 November 2026)

A Git blob comparison was made between this fork's `main` and the original upstream `main`:

- 128 files in the fork tree.
- 114 common files were identical.
- 14 common files differed.
- 2 new files existed in the fork.
- No upstream files were deleted in that comparison.

The two added files are:

- `app/src/main/java/com/virtualcontroller/android/uinput/RootUInputBackend.kt`
- `app/src/main/java/com/virtualcontroller/android/uinput/RootUInputDaemon.kt`

The 14 modified files are:

- `.github/workflows/ci.yml`
- `README.md`
- `app/src/main/aidl/com/virtualcontroller/android/ipc/IUInputService.aidl`
- `app/src/main/cpp/uinput_jni.cpp`
- `app/src/main/java/com/virtualcontroller/android/MainActivity.kt`
- `app/src/main/java/com/virtualcontroller/android/Prefs.kt`
- `app/src/main/java/com/virtualcontroller/android/input/JoystickCalibration.kt`
- `app/src/main/java/com/virtualcontroller/android/service/ControllerService.kt`
- `app/src/main/java/com/virtualcontroller/android/ui/DebugScreen.kt`
- `app/src/main/java/com/virtualcontroller/android/ui/HomeScreen.kt`
- `app/src/main/java/com/virtualcontroller/android/uinput/GamepadProfile.kt`
- `app/src/main/java/com/virtualcontroller/android/uinput/UInputGamepad.kt`
- `app/src/main/java/com/virtualcontroller/android/uinput/UInputNative.kt`
- `app/src/main/java/com/virtualcontroller/android/uinput/UInputService.kt`

Important original files confirmed identical at that audit:

- `BluetoothHidManager.kt`
- `SteamReportParser.kt`
- `SteamControllerState.kt`
- `SteamCommands.kt`
- `SteamHidProtocol.kt`
- `UsbConnectionManager.kt`
- `HidReportReader.kt`
- `GamepadMapper.kt`
- `ButtonMapping.kt`
- `XboxDescriptor.kt`

This means the original Steam transports/protocol/parser and core mapping files were not changed in that comparison. It does **not** mean the fork is regression-free: the 14 changed files include critical native, service, profile, preference, and UI code.

### Clarification of the previous profile audit

The original project had Xbox 360, Xbox One, and DualShock 4 profiles. Their absence in this fork is **intentional**, not a regression, because the product scope has been explicitly narrowed to Xbox Series X|S and PS5 DualSense only.

The important audit point is instead to ensure the two supported profiles remain correctly represented and selectable, and that Desktop stays a separate Controller Mode. Do not spend development effort restoring the three intentionally excluded profiles.

## C. Implementation notes

### Root backend

The fork added `RootUInputBackend.kt` and `RootUInputDaemon.kt`. The intended injection order is root first, Shizuku fallback. Static review found the flow coherent, but it has not been proven on all target devices. Test startup, shutdown, restart, process cleanup, descriptor/socket cleanup, permission failures, and fallback behavior on a real rooted Android device.

A stale comment in `ControllerService.kt` still describes trying uinput via Shizuku even though the binding path is root-first with Shizuku fallback. This is a documentation/comment cleanup item, not proof of a functional defect.

### Generic evdev input

The generic evdev path in `uinput_jni.cpp` monitors input devices/hotplug, handles `SYN_DROPPED`, normalizes axes, and uses a physical-device identity helper. It currently adapts generic events into an internal Steam-like report ID `0x45` which is parsed through `SteamReportParser` / `SteamControllerState`. Treat this as a bridge in the current implementation; avoid a sweeping redesign until a concrete requirement justifies it.

Risks to keep in mind:

- `ABS_Z` and `ABS_RZ` can be triggers or stick axes depending on hardware. Inspect capabilities and ranges; do not assume.
- A D-pad hat pair must not be mistaken for a second analog stick.
- If `phys` and `uniq` are absent, identity matching by bus + VID/PID + name can merge identical controllers. A stable sysfs parent path may be a better fallback, but must be validated.
- Vendor-specific USB interface discovery can create false-positive candidates. Candidate selection, capability validation, and reader startup must be reviewed together.
- Keep the original dedicated Steam USB/Bluetooth path intact.

### Native output limitation

Linux `uinput` creates an input-core device, not a genuine USB/Bluetooth HID peripheral. A name, VID/PID, or profile label alone cannot guarantee that Android or CODM will treat it as a physical Xbox Series or DualSense controller. Device capabilities, bus information, Android InputReader behavior, and target-game checks all matter.

## D. Open PRs at the last recorded handoff

These are historical handoff details. Always check the live GitHub PR and latest CI run before acting; do not assume the state below is still current.

### PR #1 — Android input-device verification

[PR #1](https://github.com/IIIStillexIII/Virtual-Xbox-Controller-Android1/pull/1)  
Branch: `codex/android-input-device-verification`

Adds diagnostic verification through Android InputManager/InputReader: locate the virtual device by name and VID/PID and log input sources, gamepad/joystick classification, and axes. An earlier CI run passed, but the latest commit's result was not confirmed at the handoff.

### PR #2 — USB XInput discovery and evdev capability detection

[PR #2](https://github.com/IIIStillexIII/Virtual-Xbox-Controller-Android1/pull/2)  
Branch: `codex/fix-usb-xinput-evdev-discovery`  
Last recorded branch head: `3905f8ef269443bc9d27ec41308bd42dc3684620`

Contains changes to consider vendor-specific USB interfaces as XInput candidates, distinguish D-pad hats from a second stick, recognize legacy Linux joystick button codes (for example `BTN_TRIGGER`, `BTN_THUMB`, `BTN_TOP`), and match Bluetooth devices through `uniq`, `phys`, or name where appropriate.

This work was not part of `main` at the last audit. The latest CI status for the recorded head was not confirmed. Review candidate ordering and false-positive handling before merge.

## E. Ordered roadmap — continue in this order

### 1. Verify and cleanly separate Controller Mode from Gamepad Profile

- Keep only Xbox Series X|S and PS5 DualSense as virtual gamepad profiles.
- Keep Gamepad and Desktop (mouse + keyboard) as Controller Mode choices.
- Do not restore Xbox 360, Xbox One, or DualShock 4.
- Audit the current `MOUSE` sentinel in `GamepadProfile.kt`: it is currently used internally for Desktop mode, but should not be presented or conceptualized as a third gamepad profile.
- Consider a dedicated `ControllerMode` model/state and a `GamepadProfile` model containing only the two supported virtual gamepad profiles. Refactor only if the full preferences, UI, service, notification, and native lifecycle can be updated coherently.
- Preserve the existing Xbox ID 0 and DualSense ID 3 unless a complete migration is designed and tested.
- Verify profile selection, Desktop switching back to the last selected gamepad profile, app restart persistence, notification actions, and native profile selection.

### 2. Review and validate PR #2

- Rebase/compare against the current `main`; inspect the full candidate-selection and reader-start lifecycle.
- Keep capability validation as the final guard against false positives.
- Test standard USB HID and vendor-specific/XInput USB controllers.
- Test D-pad hats, legacy button codes, and single-stick layouts.

### 3. Review and validate PR #1

- Check CI for the exact current PR head.
- Confirm Android InputReader sees the virtual device and reports expected sources, classification, buttons, and axes.
- Keep diagnostics useful without excessive normal-operation logging.

### 4. Test root and Shizuku lifecycle

- Test root backend initialization and normal stop/restart.
- Test root failure and Shizuku fallback.
- Test daemon/process cleanup, file descriptors, sockets, and repeated connect/disconnect.
- Record device-specific SELinux and permission behavior.

### 5. Test real controllers and games

Test USB HID, USB XInput-style, generic Bluetooth, and Steam Controller paths separately. Test multiple physical controllers if multi-device support is intended. Validate both output profiles independently: Xbox Series X|S and PS5 DualSense. Also test both Controller Modes: Gamepad and Desktop.

For CODM and every other target game, record the phone model, Android build, game version, physical controller, connection, selected gamepad profile, Controller Mode, backend, app commit, and logs. Distinguish these checkpoints:

1. Physical controller is discovered.
2. Physical evdev events are read.
3. Mapping produces the expected normalized state.
4. Virtual uinput device is created.
5. Android InputReader recognizes it as the expected gamepad.
6. The target game accepts it.

Passing one checkpoint does not prove the next.

## F. Debugging checklist

When a game does not react, inspect each layer rather than assuming the physical controller reader is at fault:

1. Is the physical controller present in Android input devices?
2. Was the correct `/dev/input/eventN` selected?
3. Do its button/axis events arrive?
4. Are events normalized and mapped correctly?
5. Did the selected root or Shizuku backend create the virtual uinput device?
6. Does the virtual device expose the expected capabilities and selected profile?
7. Does Android InputReader classify it as a gamepad/joystick?
8. Does the target game accept it?

Useful diagnostics on a device you own or are authorized to test:

- `getevent -pl` — list input devices and capabilities.
- `getevent -lt /dev/input/eventN` — inspect a selected event device.
- `dumpsys input` — inspect Android's input-device view.
- `ls -l /dev/uinput` — inspect the uinput node and permissions.

An event visible in `getevent` proves only that the physical input path works. It does not prove virtual-device creation or game recognition.

## G. Test/build and issue-reporting rules

- Run the configured Gradle/CI build for the exact commit being reviewed.
- Never report an older passing workflow as proof that a newer commit passed.
- CI compilation is not physical-device validation.
- Changes to USB, Bluetooth, evdev, uinput, root, Shizuku, Controller Mode, or gamepad profiles require appropriate real-device tests before being called verified.
- If hardware testing was not performed, label the change **not device-verified**.

Bug reports should include app commit/version, phone model, Android build, controller model, USB/Bluetooth connection, selected gamepad profile, Controller Mode, backend, logs, exact reproduction steps, and (for game-specific issues) game version. Never include passwords, access tokens, private keys, or other secrets.

## H. Scope guardrails

- Exactly two virtual gamepad profiles: Xbox Series X|S and PS5 DualSense.
- Desktop (mouse + keyboard) is a separate Controller Mode, not a gamepad profile.
- Do not restore Xbox 360, Xbox One, or DualShock 4 virtual profiles.
- CODM is an important test target, not the only supported use case.
- No touchscreen mapping, touch injection, macro engine, or overlay is a substitute for the system virtual-device output.
- Do not promise universal compatibility across all Android devices, kernels, games, or controllers.
- Preserve Desktop behavior, original Steam handling, and upstream attribution; all development work belongs in this fork.
