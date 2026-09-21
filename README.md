# Study Timetable

A personal study-timetable application built with HTML, CSS, JavaScript, Capacitor, and Android.

## Main Goals

The Android project has exactly three main goals:

1. Run the existing study timetable on Android.
2. Add a hard-to-stop study alarm.
3. Add an Android home-screen widget.

The existing timetable functionality should remain intact while native Android features are added.

---

## Current Features

### Weekly timetable

The timetable is organized in this order:

- Friday
- Saturday
- Sunday
- Monday
- Tuesday
- Wednesday
- Thursday

### Task management

The app supports:

- Add task
- Edit task
- Delete task
- Delete confirmation
- Today's Tasks
- Next Task
- Automatic task status
- Live countdown

### Local storage

The timetable is saved locally using `localStorage`.

Storage key:

```text
studyTimetable
```

The data is stored as JSON, so the timetable does not require an online database.

---

## Study Schedule Rules

### Sleep

```text
12:00 AM - 9:00 AM
```

### Rest

```text
1 hour every day
```

### Exercise

Exercises are scheduled:

```text
11:00 PM - 11:30 PM
```

There must be exactly one exercise per day and it must not overlap another item.

Exercise distribution:

```text
Math       2
Chemistry  2
Arabic     1
English    1
Physics    1
```

An exercise subject should not be the same subject as that day's corresponding lesson.

### Quran

Quran time:

```text
5:30 PM - 7:00 PM
```

Labels:

```text
Monday     مراجعة
Tuesday    مراجعة
Wednesday  درس
Thursday   مراجعة
Friday     مراجعة
Saturday   درس
Sunday     مراجعة
```

Quran should only occupy the relevant 05:00 PM and 06:00 PM rows.

There is no Quran homework and no Quran exercise.

### Preferred study period

The strongest productivity period is:

```text
9:00 AM - 12:00 PM
```

Homework should preferably be placed in this period.

---

## Subject Rules

### Arabic

Monday:

```text
Online Arabic - Explanation + test
```

Duration:

```text
3 consecutive hours
```

Monday also has:

```text
Arabic Homework - 2 hours
```

Thursday:

```text
Online Arabic - H.W submission
```

Duration:

```text
1 hour
```

There is one Arabic homework session per week and one weekly:

```text
حل تدريبات عربي
```

session.

### English

Friday:

```text
English - 3 hours
```

Sunday:

```text
English - 2 hours
```

Friday also has:

```text
English Homework - 2 hours
```

### Math

```text
Friday     10:00 AM - 11:30 AM
Saturday   10:00 AM - 11:30 AM
Tuesday    8:15 PM
```

### Physics

Physics 1:

```text
Monday
9:00 PM - 10:30 PM
```

Physics 2:

```text
Thursday
9:00 PM - 10:30 PM
```

### Chemistry

Chemistry includes:

```text
Tuesday from 4:30 PM
Friday 11:30 AM
```

---

# Android Version

The web application was converted to Android using Capacitor.

## Application ID

```text
io.johnkrider12.tasks
```

## Application name

```text
tasks
```

## Capacitor web directory

```text
www
```

## capacitor.config.json

```json
{
  "appId": "io.johnkrider12.tasks",
  "appName": "tasks",
  "webDir": "www"
}
```

---

# Project Structure

```text
project/
├── android/
│   └── Android project
├── www/
│   ├── index.html
│   ├── app.js
│   └── style.css
├── index.html
├── app.js
├── style.css
├── capacitor.config.json
├── package.json
├── package-lock.json
└── .github/
    └── workflows/
        └── build-android.yml
```

`node_modules/` is a local dependency directory and should not be uploaded to GitHub.

---

# Capacitor

The project uses:

```text
@capacitor/core
@capacitor/cli
@capacitor/android
```

After changing the web application, make sure the current files are present in `www/`, then run:

```bash
npx cap sync android
```

This copies the web application into the Android project.

---

# Important Web Files

The main web files are:

```text
index.html
app.js
style.css
```

There is also a packaged copy:

```text
www/index.html
www/app.js
www/style.css
```

The `www/` files are the files Capacitor packages into Android.

When changing `app.js`, keep the source and `www/app.js` synchronized before running:

```bash
npx cap sync android
```

Otherwise an older `www/app.js` can overwrite the newer Android web copy.

---

# Existing JavaScript Details

The day order is:

```javascript
const dayOrder = [
    "Friday",
    "Saturday",
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday"
];
```

The timetable is saved with:

```javascript
function saveTimetable() {
    localStorage.setItem(
        "studyTimetable",
        JSON.stringify(timetable)
    );
}
```

It is loaded with:

```javascript
function loadTimetable() {
    const saved =
        localStorage.getItem("studyTimetable");

    if (!saved) return;

    try {
        const parsed = JSON.parse(saved);

        for (const day of dayOrder) {
            if (Array.isArray(parsed[day])) {
                timetable[day] = parsed[day];
            }
        }
    } catch (error) {
        console.error(
            "Could not load timetable:",
            error
        );
    }
}
```

The app calls:

```javascript
loadTimetable();
```

when starting.

---

# Important Bug Fixes

## Task row append

The incorrect code was:

```javascript
task.appendChild(row);
```

`task` is timetable data, not a DOM element.

The correct code is:

```javascript
taskListElement.appendChild(row);
```

## Edit button selector

The correct selector is:

```javascript
row.querySelector(".edit-button")
```

not:

```javascript
row.querySelector("edit-button")
```

## Delete

Deletion asks for confirmation, then removes the selected item and refreshes the interface:

```javascript
const confirmed =
    confirm(`Delete "${task.name}"?`);

if (!confirmed) return;

timetable[selectedDay].splice(index, 1);

saveTimetable();
renderTimetable();
updateHome();
```

---

# GitHub Actions Android Build

The Android APK is built remotely with GitHub Actions.

Android Studio and a local Android SDK are not required for the current workflow.

The workflow is:

```text
Checkout repository
        ↓
Setup Node.js 24
        ↓
Setup Java 21
        ↓
npm ci
        ↓
npx cap sync android
        ↓
bash ./gradlew assembleDebug
        ↓
Upload app-debug.apk
```

The build output is:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

The GitHub artifact name is:

```text
study-timetable-debug
```

---

# GitHub Actions Configuration

The build workflow is:

```text
.github/workflows/build-android.yml
```

The important parts are:

```yaml
- name: Setup Node.js
  uses: actions/setup-node@v4
  with:
    node-version: 24
    cache: npm

- name: Setup Java
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
```

The Gradle build uses:

```yaml
working-directory: android
run: bash ./gradlew assembleDebug
```

The `bash` form is used because the Gradle wrapper originally produced a Linux permission-denied error in GitHub Actions.

---

# Building Locally

Install dependencies:

```bash
npm ci
```

Synchronize Capacitor:

```bash
npx cap sync android
```

Build:

```bash
cd android
bash ./gradlew assembleDebug
```

APK:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

---

# Android APK Testing

The debug APK has already been successfully built and tested on a physical Android phone.

For manual installation, Android Platform-Tools / ADB can be used.

Enable:

```text
Developer options
USB debugging
```

Then:

```bash
adb devices
```

After accepting the USB debugging authorization on the phone:

```bash
adb install -r app-debug.apk
```

A successful installation returns:

```text
Success
```

---

# Native Study Alarm

The second major goal is a native Android study alarm.

A JavaScript timer alone is not enough because the application may be closed or in the background.

The alarm therefore uses native Android components.

## Intended behavior

The default alarm time is:

```text
10 minutes before the task
```

The alarm should:

- Work when the main app is closed.
- Play a loud alarm sound.
- Vibrate.
- Open a full-screen alarm screen.
- Require deliberate interaction to stop.
- Use a 5-second hold as the intended dismissal mechanism.

The alarm is intended to be difficult to dismiss accidentally rather than being an ordinary notification.

## Native components

The alarm implementation uses:

```text
MainActivity
StudyAlarmPlugin
AlarmReceiver
AlarmActivity
```

### MainActivity

The main Capacitor Android activity.

### StudyAlarmPlugin

Connects JavaScript to native Android alarm functionality.

### AlarmReceiver

Receives the scheduled Android alarm event.

### AlarmActivity

Displays the full-screen alarm interface, including the task information, alarm sound, vibration, and stop interaction.

---

# Capacitor PluginMethod Detail

The Capacitor annotation must be imported as:

```java
import com.getcapacitor.PluginMethod;
```

NOT:

```java
import com.getcapacitor.annotation.PluginMethod;
```

The second form caused the Android compilation error:

```text
cannot find symbol
com.getcapacitor.annotation.PluginMethod
```

---

# Alarm Permissions

Modern Android versions have restrictions around exact alarms, notifications, and full-screen alarm interfaces.

The final alarm implementation must use the appropriate Android permissions and user settings.

The application should request only the permissions needed for the alarm and should not disable Android security protections globally.

---

# Home-Screen Widget

The third major goal is an Android home-screen widget.

The widget should show timetable information without requiring the user to open the main application.

The intended information is:

```text
STUDY TIMETABLE

NEXT TASK

Task name

Countdown

Number of tasks today
```

Example:

```text
┌──────────────────────────┐
│      STUDY TIMETABLE     │
│                          │
│       NEXT TASK          │
│                          │
│      Arabic Exercise     │
│      حل تمارين عربي      │
│                          │
│        01:39:17          │
│                          │
│        6 tasks today     │
└──────────────────────────┘
```

The widget will be implemented as a native Android component.

It should not depend on a normal web-page `setInterval()` because Android widgets use their own update system.

---

# Development Order

The project should be developed in this order:

```text
1. Android app
       ↓
2. Hard-to-stop alarm
       ↓
3. Home-screen widget
```

The timetable itself remains the central application.

---

# Current Status

Completed:

```text
[x] Weekly timetable
[x] Add task
[x] Edit task
[x] Delete task
[x] Delete confirmation
[x] Today's Tasks
[x] Next Task
[x] Automatic task status
[x] Live countdown
[x] LocalStorage persistence
[x] Capacitor Android project
[x] GitHub repository
[x] GitHub Actions build
[x] Java 21 build environment
[x] Successful Android APK build
[x] APK tested on a physical Android phone
```

In progress:

```text
[ ] Finalize native hard-to-stop alarm
[ ] Test alarm while app is closed
[ ] Test full-screen alarm
[ ] Test alarm sound and vibration
[ ] Test dismissal behavior
[ ] Build home-screen widget
[ ] Test widget on physical phone
```

---

# Development Workflow

The normal development cycle is:

```text
Edit in VS Code
      ↓
Test the web application
      ↓
Update www/
      ↓
npx cap sync android
      ↓
GitHub Desktop
      ↓
Commit
      ↓
Push
      ↓
GitHub Actions
      ↓
Download APK artifact
      ↓
Install on phone
      ↓
Test
```

---

# Troubleshooting

## Capacitor does not copy the web files

Check:

```text
capacitor.config.json
```

and make sure:

```json
"webDir": "www"
```

Then:

```bash
npx cap sync android
```

## Gradle permission denied

Use:

```bash
bash ./gradlew assembleDebug
```

## Java source release 21 error

GitHub Actions must use Java 21:

```yaml
- name: Setup Java
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
```

## PluginMethod compilation error

Use:

```java
import com.getcapacitor.PluginMethod;
```

## APK installation warning

For a development APK, use ADB if needed:

```bash
adb devices
adb install -r app-debug.apk
```

Do not globally disable Android security features just to install the development APK.

---

# Project Philosophy

The application is intended to stay:

- Simple
- Fast
- Reliable
- Offline-capable for timetable data
- Easy to edit
- Focused on studying

Native Android functionality should complement the existing timetable instead of replacing it.

The final architecture is:

```text
Existing web application
        +
Native Android alarm
        +
Native Android home-screen widget
```

The timetable remains the source of the user's study schedule.
