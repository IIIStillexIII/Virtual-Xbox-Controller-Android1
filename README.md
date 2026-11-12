# Virtual Xbox Controller for Android

Android controller bridge that reads supported physical game controllers and exposes a system-wide virtual gamepad through Android's Linux input stack.

This is **my development fork**, not the original repository. The original upstream project is [misakidebugged/Virtual-Xbox-Controller-Android](https://github.com/misakidebugged/Virtual-Xbox-Controller-Android); this repository is [IIIStillexIII/Virtual-Xbox-Controller-Android1](https://github.com/IIIStillexIII/Virtual-Xbox-Controller-Android1). The upstream project is kept as the reference/base, while changes are made in this fork.

## Project goal

The goal of this fork is to keep the original Virtual Xbox Controller app and its overall design as close to upstream as practical, while improving it into a reliable system-wide controller bridge for Android. A generic physical gamepad connected over USB or Bluetooth should be mapped to one of exactly two virtual controller profiles — **Xbox Series X|S** or **PlayStation 5 DualSense** — so Android and games that support those controller input paths can recognize and use it as a gamepad. The app must also retain **Desktop mode**, use the **root backend first** with **Shizuku as a fallback**, and provide complete practical mapping for buttons, sticks, triggers and D-pad.

A key target for compatibility testing is **Call of Duty: Mobile (CODM)**, alongside other Android games with controller support. The objective is genuine system-level input-device recognition, **not** an on-screen overlay or touch-control/macro mapping. CODM compatibility is a target to verify on real devices; it is not claimed as guaranteed, and this project does not attempt to bypass a game's anti-cheat or controller restrictions.

This is a specialized fork of the original Virtual Xbox Controller project. The current fork focuses on two virtual gamepad profiles:

- **Xbox** — Xbox-style virtual controller profile.
- **DualSense** — PlayStation 5 DualSense-style virtual controller profile.

**Desktop mode** is a separate input mode for mouse/keyboard-style navigation. It is not a third virtual gamepad profile.

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
4. Select the desired virtual profile: **Xbox** or **DualSense**. Use Desktop mode separately when mouse/keyboard navigation is desired.
5. Start the controller service and check the app's connection/status log.

If injection cannot start, check that the root backend is available or that Shizuku is running and authorized.

## Development status

This project is under active development. CI builds verify that the Android project compiles, but CI does not test physical USB/Bluetooth controllers, root permissions, Shizuku injection on a real device, or compatibility with individual games.

Please include the Android version, device model, controller model, connection type and relevant app logs when reporting a hardware compatibility issue.

## Credits

This project is a fork of [misakidebugged/Virtual-Xbox-Controller-Android](https://github.com/misakidebugged/Virtual-Xbox-Controller-Android). See the source repository and in-project license files for the original project's licensing and attribution.
