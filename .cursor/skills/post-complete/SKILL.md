---
name: post-complete
description: Build the Android debug APK, reinstall it on all available connected devices (real first, otherwise emulator), and launch the app. Use after a request is completed or when the user asks to verify changes on devices.
---

# Post-Complete

## When to use
Use this skill after a request was completed and the user wants to verify the result on real devices (if available) and emulators.

## Rules
- Do NOT use subagents in this skill.
- If Mobile MCP install fails because the APK file does not exist, investigate the build outputs directory structure, find the correct APK path, update the skill path, and retry.
- Don't ask questions unless you can't complete the task because my attention is needed. If something fails, try to investigate yourself.

## Instructions

1. In parallel:
   - Build a fresh Android debug APK:
      - Run: `./gradlew app:android:assembleDebug` from the Android project root.
      - Do not try to execute this command multiple times or add additional options like `--no-daemon`.
   - List available devices:
      - Tool: `mobile_list_available_devices`
      - Parameters: `{}` (no params)
2. Select the devices to install the app to:
   - If at least one device is a physical Android device, install+launch on those devices.
   - Otherwise, install+launch on emulators.
   - If working with calls, install+launch on at least 2 devices (pick more devices from the available list).
   - Do not ask the user to choose devices unless they explicitly say what device to use.
3. In parallel:
   - For each chosen device, run `mobile_install_app` with:
      - `device`: string (use `id` from `mobile_list_available_devices`)
      - `path`: string (`app/android/build/outputs/apk/debug/android-debug.apk` converted to absolute path)
      - `package`: string (`ru.fromchat.beta`)
   - For each chosen device, run `mobile_launch_app` with:
      - `device`: string (use `id` from `mobile_list_available_devices`)
      - `packageName`: string (`ru.fromchat.beta`)
   - Use exactly ONE parallel tool execution batch for everything:
     - include every `mobile_install_app` call for every chosen device
     - include every `mobile_launch_app` call for every chosen device
   - Do NOT split into multiple parallel batches and do NOT run installs/launches in separate tool batches.
4. Completion:
   - Only after every chosen device has successful results for ALL tool calls the agent should tell the user the task is completed.
   - If any device fails any tool call, investigate and try again until they succeed.