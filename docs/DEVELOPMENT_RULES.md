# Development rules

These rules are mandatory for all NexPad / Virtual Xbox Controller for Android (VXCA) development in this repository.

## 1. Do not duplicate existing functionality

The original upstream VXCA is the foundation of this project. Before adding or porting anything, inspect the corresponding implementation in the original upstream repository and compare it with this fork.

- Do not recreate a feature that VXCA already provides.
- Do not add a second service, manager, settings screen, backup system, mapping pipeline, or other parallel implementation when the existing one can be reused.
- Do not describe existing VXCA functionality as a new feature.
- Preserve the original implementation when it already meets the requirement.

## 2. Improve existing functionality only when there is a concrete benefit

Changes to existing functionality are allowed when they demonstrably improve one or more of the following:

- correctness and reliability;
- compatibility with physical controllers, Android versions, or target games;
- performance, resource use, or lifecycle stability;
- configurability or usability;
- diagnostics and recoverability.

An improvement must address a specific limitation or defect. "Another project has this feature" is not, by itself, a reason to copy it.

## 3. Required review before implementation

For every proposed change:

1. Identify the exact feature or subsystem that needs work.
2. Inspect how the original upstream VXCA implements it.
3. Inspect how the current fork implements it.
4. Compare any proposed reference implementation (for example, NexPad) against both.
5. State the concrete deficiency and explain why the proposed change is better.
6. Make the smallest focused change that solves that deficiency.
7. Check compatibility with existing settings, profiles, backups, UI, services, native code, and user workflows as applicable.
8. Build and test the exact resulting commit. Clearly label anything not verified on a physical device.

If no real deficiency or measurable improvement is established, do not change the code.

## 4. Preserve product scope and existing behavior

- Keep exactly two virtual gamepad profiles: Xbox Series X|S and PS5 DualSense.
- Gamepad and Desktop are Controller Modes. Desktop is the original VXCA mouse/keyboard mode, not a third gamepad profile.
- Do not restore Xbox 360, Xbox One, or DualShock 4 profiles.
- Preserve existing VXCA features and architecture unless a focused, justified change requires otherwise.
- Do not replace system-level virtual input with touchscreen mapping, touch injection, macros, or overlays.
- Do not promise universal compatibility. Verify each relevant controller, Android build, backend, and game separately.

## 5. No parallel implementation without necessity

A new subsystem is not justified merely because it exists in NexPad or another project. Prefer extending the existing VXCA implementation. A separate implementation is acceptable only when the existing architecture cannot reasonably support the requirement and the technical reason is documented.

## 6. Change acceptance standard

A change is acceptable only when its purpose, upstream comparison, scope, compatibility impact, and validation are documented. Fewer well-justified changes are better than a larger set of duplicated or unverified features.

**Default decision:** preserve what already works; improve it only when there is a proven reason; do not duplicate it.
