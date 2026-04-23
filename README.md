# AssistiveScroll

AssistiveScroll is a utility app for Android that makes screen scrolling easier by using Android's AccessibilityService.

It is designed primarily to help users scroll quickly from a floating on-screen button, especially during one-handed use or when reading long content.  
While inspired by iOS AssistiveTouch, it narrows the focus to **scroll assistance** in a way that fits Android's platform constraints.

## Features

- Floating button that stays on screen
- Draggable position adjustment
- Edge snapping to reduce obstruction
- Variable-speed scrolling by long-pressing and sliding up or down
- Visual indicator for scroll direction
- Semi-transparent / partially hidden idle state for better visibility without getting in the way

## What Problem This App Solves

This app is built for situations where smartphone scrolling feels inconvenient, such as:

- Reading long web pages, social feeds, or articles with one hand
- Repeated swipe gestures becoming tedious
- Large screen devices making scrolling less comfortable
- Wanting a persistent assistive button without having it block the screen

AssistiveScroll focuses on providing scroll assistance through a minimal UI.

## How to Use

1. Launch the app
2. When the accessibility settings screen opens, enable the AssistiveScroll accessibility service
3. Operate the floating button displayed on the screen

### Basic Interaction

- **Tap**  
  Starting action for opening the assistive menu

- **Drag**  
  Move the button to any position

- **Long press → slide up / down**  
  Enter scroll mode  
  Scrolling stops near the center of the button, and speed increases as the finger moves farther up or down

- **Move your finger back near the center**  
  Stop scrolling

- **Release your finger**  
  End scrolling

## Design Principles

This app emphasizes daily usability and tactile feel rather than adding too many features.

The main design priorities are:

- Staying focused on the core purpose of scroll assistance
- Making one-handed use easier
- Keeping the persistent UI from becoming intrusive
- Avoiding excessive animation or visual noise
- Not forcing features that conflict with Android platform limitations

## Limitations

Because Android AccessibilityService and screen structures have limitations, the following constraints apply:

- Behavior may not be identical across all apps
- The true top or bottom of content cannot always be detected precisely
- Scroll results may differ in WebView-based apps or apps with custom rendering
- Some system-level behaviors seen on iPhone cannot be reproduced as-is on Android

For that reason, AssistiveScroll prioritizes **practical scroll assistance for everyday use** rather than fully automated page navigation.

## Required Permissions

This app relies on the following mechanisms:

- **AccessibilityService**
  - Assists with scrolling actions
  - Supports on-screen overlay behavior

Open the project in Android Studio and run Gradle Sync if needed.
Build
Example for generating a debug APK:
./gradlew assembleDebug

You can then install the generated APK on a device and test it.
Directory Structure
AssistiveScroll/
├─ app/
├─ gradle/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradlew
└─ gradlew.bat

Roadmap
At this stage, the project is focused on the following directions:
Improving the quality of the scroll assistance experience
Prioritizing responsiveness and stability of existing interactions
Avoiding features that do not work well with Android platform constraints
Refining it as a minimal tool suitable for daily use
License
No license has been added yet.
Please add one if needed.
- **Overlay usage equivalent to SYSTEM_ALERT_WINDOW**
  - Required to display the floating button

Users must explicitly enable the necessary settings themselves before use.

## Development Environment

- Android Studio
- Kotlin
- Android AccessibilityService
- Gradle

## Setup

```bash
git clone https://github.com/tyosu131/AssistiveScroll.git
cd AssistiveScroll

