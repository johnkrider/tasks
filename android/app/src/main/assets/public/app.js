/*
    STUDY TIMETABLE APP

    The timetable (and the alarm settings) are saved in localStorage.
    Every time either one changes, the whole thing is pushed to the native
    Android side, which owns the alarms and the home-screen widget.
*/


const timetable = {

    Friday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Math / الرياضيات", start: "10:00", end: "11:30" },
        { name: "Physics / الفيزياء HW", start: "15:00", end: "17:00" },
        { name: "مراجعة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Chemistry / الكيمياء", start: "20:00", end: "21:30" },
        { name: "Arabic Exercise / حل تمارين عربي", start: "23:00", end: "23:30" }
    ],

    Saturday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Math / الرياضيات", start: "10:00", end: "11:30" },
        { name: "Chemistry / الكيمياء HW", start: "14:00", end: "16:00" },
        { name: "حصة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "English / الإنجليزي", start: "20:00", end: "22:00" },
        { name: "Chemistry Exercise / حل تمارين كيمياء", start: "23:00", end: "23:30" }
    ],

    Sunday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Math / الرياضيات HW", start: "10:00", end: "12:00" },
        { name: "Catch UP", start: "14:00", end: "15:00" },
        { name: "مراجعة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Physics Exercise / حل تمارين فيزياء", start: "23:00", end: "23:30" }
    ],

    Monday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Physics / الفيزياء HW", start: "10:00", end: "12:00" },
        { name: "Arabic / العربي", start: "13:00", end: "16:00" },
        { name: "مراجعة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Physics / الفيزياء", start: "21:00", end: "22:30" },
        { name: "English Exercise / حل تمارين إنجليزي", start: "23:00", end: "23:30" }
    ],

    Tuesday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Math / الرياضيات HW", start: "10:00", end: "12:00" },
        { name: "English / الإنجليزي HW", start: "13:00", end: "15:00" },
        { name: "مراجعة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Math / الرياضيات", start: "20:00", end: "21:30" },
        { name: "Chemistry Exercise / حل تمارين كيمياء", start: "23:00", end: "23:30" }
    ],

    Wednesday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Math / الرياضيات HW", start: "10:00", end: "12:00" },
        { name: "Catch UP", start: "14:00", end: "15:00" },
        { name: "حصة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Chemistry / الكيمياء", start: "20:00", end: "22:00" },
        { name: "Math Exercise / حل تمارين رياضيات", start: "23:00", end: "23:30" }
    ],

    Thursday: [
        { name: "Wake UP", start: "09:00", end: "09:15" },
        { name: "Chemistry / الكيمياء HW", start: "10:00", end: "12:00" },
        { name: "Arabic / العربي HW", start: "15:00", end: "16:00" },
        { name: "مراجعة Quran / القرآن", start: "18:00", end: "19:00" },
        { name: "Physics / الفيزياء", start: "21:00", end: "22:30" },
        { name: "English Exercise / حل تمارين إنجليزي", start: "23:00", end: "23:30" }
    ]
};


const dayOrder = [
    "Friday",
    "Saturday",
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday"
];


// =====================
// Alarm settings
// =====================

const defaultSettings = {
    alarmsEnabled: true,
    leadMinutes: 10,
    holdSeconds: 5,
    mathCount: 0,
    maxVolume: true,
    vibrate: true
};

let settings = { ...defaultSettings };


function clampInt(value, min, max, fallback) {

    const number = parseInt(value, 10);

    if (Number.isNaN(number)) return fallback;

    return Math.max(min, Math.min(max, number));
}


function loadSettings() {

    try {

        const saved =
            JSON.parse(localStorage.getItem("studySettings"));

        if (saved && typeof saved === "object") {

            settings = { ...defaultSettings, ...saved };

        }

    } catch (error) {

        console.error("Could not load settings:", error);

    }
}


function saveSettings() {

    localStorage.setItem(
        "studySettings",
        JSON.stringify(settings)
    );

    syncNative();
}


// =====================
// Edits
// =====================

function saveTimetable() {

    localStorage.setItem(
        "studyTimetable",
        JSON.stringify(timetable)
    );

    syncNative();
}


function loadTimetable() {

    const saved =
        localStorage.getItem("studyTimetable");

    if (!saved) return;

    try {

        const parsed =
            JSON.parse(saved);

        for (const day of dayOrder) {

            if (Array.isArray(parsed[day])) {

                timetable[day] =
                    parsed[day];

            }
        }

    } catch (error) {

        console.error(
            "Could not load timetable:",
            error
        );

    }
}


// =====================
// ANDROID (alarms + widget)
// =====================

const capacitor = window.Capacitor;

// Capacitor's native bridge builds a ready-made object for every plugin
// that MainActivity registered. Prefer that; fall back to registerPlugin.
const pluginFromBridge =
    capacitor && capacitor.Plugins && capacitor.Plugins.StudyAlarm;


// "Am I inside the Android app?" - checked in several ways because
// not every Capacitor build exposes every helper.
const isNative = !!(
    window.androidBridge ||
    pluginFromBridge ||
    (
        capacitor &&
        (
            (
                typeof capacitor.isNativePlatform === "function" &&
                capacitor.isNativePlatform()
            ) ||
            (
                typeof capacitor.getPlatform === "function" &&
                capacitor.getPlatform() !== "web"
            )
        )
    )
);


let StudyAlarm = null;

if (isNative) {

    if (pluginFromBridge) {

        StudyAlarm = pluginFromBridge;

    } else if (
        capacitor &&
        typeof capacitor.registerPlugin === "function"
    ) {

        StudyAlarm = capacitor.registerPlugin("StudyAlarm");

    }
}


function noPluginMessage() {

    return isNative
        ? "The Android app started, but the alarm plugin did not load."
        : "Alarms only work in the Android app.";
}


let lastSync = null;


// One alarm per task. Android works out the exact time itself
// (weekday + start time - lead minutes), so it keeps working
// every week, across daylight-saving changes, and after a reboot.
function buildAlarmList() {

    const alarms = [];
    let id = 1000;

    for (const day of dayOrder) {

        for (const task of timetable[day]) {

            if (!task.start) continue;

            const thisId = id++;

            if (task.alarm === false) continue;

            alarms.push({
                id: thisId,
                task: task.name,
                day: day,
                start: task.start
            });
        }
    }

    return alarms;
}


function syncNative() {

    if (!StudyAlarm) return Promise.resolve(null);

    return StudyAlarm.schedule({
        alarms: buildAlarmList(),
        config: {
            enabled: settings.alarmsEnabled,
            leadMinutes: settings.leadMinutes,
            holdSeconds: settings.holdSeconds,
            mathCount: settings.mathCount,
            maxVolume: settings.maxVolume,
            vibrate: settings.vibrate
        },
        timetable: timetable
    })
        .then(result => {

            lastSync = result;

            renderAlarmInfo();

            return result;
        })
        .catch(error => {

            console.error("Could not sync with Android:", error);

            return null;
        });
}


// =====================
// Variables
// =====================

let selectedDay = getTodayName();

let editingIndex = -1;


// --------------------------------------------------
// ELEMENTS
// --------------------------------------------------

const homePage =
    document.getElementById("homePage");

const timetablePage =
    document.getElementById("timetablePage");

const settingsPage =
    document.getElementById("settingsPage");


const homeButton =
    document.getElementById("homeButton");

const timetableButton =
    document.getElementById("timetableButton");

const settingsButton =
    document.getElementById("settingsButton");


const dateElement =
    document.getElementById("date");


const currentTaskElement =
    document.getElementById("currentTask");


const currentTimeElement =
    document.getElementById("currentTime");


const currentCountdownElement =
    document.getElementById("currentCountdown");


const nextTaskElement =
    document.getElementById("nextTask");


const nextTimeElement =
    document.getElementById("nextTime");


const nextCountdownElement =
    document.getElementById("nextCountdown");


const homeTaskList =
    document.getElementById("homeTaskList");


const daysElement =
    document.getElementById("days");


const dayTitleElement =
    document.getElementById("dayTitle");


const taskCountElement =
    document.getElementById("taskCount");


const taskListElement =
    document.getElementById("taskList");


const addTaskButton =
    document.getElementById("addTask");


const editor =
    document.getElementById("editor");


const taskForm =
    document.getElementById("taskForm");


const editorTitle =
    document.getElementById("editorTitle");


const taskName =
    document.getElementById("taskName");


const taskStart =
    document.getElementById("taskStart");


const taskEnd =
    document.getElementById("taskEnd");


const taskAlarm =
    document.getElementById("taskAlarm");


const cancelButton =
    document.getElementById("cancelButton");


const alarmInfo =
    document.getElementById("alarmInfo");

const setEnabled =
    document.getElementById("setEnabled");

const setLead =
    document.getElementById("setLead");

const setHold =
    document.getElementById("setHold");

const setMath =
    document.getElementById("setMath");

const setVolume =
    document.getElementById("setVolume");

const setVibrate =
    document.getElementById("setVibrate");

const testAlarmButton =
    document.getElementById("testAlarm");

const permissionList =
    document.getElementById("permissionList");


// --------------------------------------------------
// DATE / TIME
// --------------------------------------------------

function getTodayName() {

    return new Date().toLocaleDateString(
        "en-US",
        {
            weekday: "long"
        }
    );
}


function timeToMinutes(time) {

    const [hours, minutes] =
        time.split(":").map(Number);

    return hours * 60 + minutes;
}


function makeTodayTime(time) {

    const [hours, minutes] =
        time.split(":").map(Number);

    const date = new Date();

    date.setHours(hours);
    date.setMinutes(minutes);
    date.setSeconds(0);
    date.setMilliseconds(0);

    return date;
}


function formatTime(time) {

    const date =
        makeTodayTime(time);

    return date.toLocaleTimeString(
        "en-US",
        {
            hour: "numeric",
            minute: "2-digit"
        }
    );
}


function formatCountdown(milliseconds) {

    if (milliseconds < 0) {

        milliseconds = 0;

    }

    const seconds =
        Math.floor(milliseconds / 1000);

    const hours =
        Math.floor(seconds / 3600);

    const minutes =
        Math.floor((seconds % 3600) / 60);

    const remainingSeconds =
        seconds % 60;

    return (
        String(hours).padStart(2, "0") +
        ":" +
        String(minutes).padStart(2, "0") +
        ":" +
        String(remainingSeconds).padStart(2, "0")
    );
}


// --------------------------------------------------
// HOME
// --------------------------------------------------

function updateHome() {

    const now = new Date();

    const today =
        getTodayName();

    const tasks =
        timetable[today] || [];

    const currentMinutes =
        now.getHours() * 60 +
        now.getMinutes() +
        now.getSeconds() / 60;


    let currentTask = null;

    let nextTask = null;


    for (const task of tasks) {

        const start =
            timeToMinutes(task.start);

        const end =
            timeToMinutes(task.end);


        if (
            currentMinutes >= start &&
            currentMinutes < end
        ) {

            currentTask = task;

        }


        if (
            start > currentMinutes &&
            nextTask === null
        ) {

            nextTask = task;

        }

    }


    dateElement.textContent =
        now.toLocaleDateString(
            "en-US",
            {
                weekday: "long",
                month: "long",
                day: "numeric"
            }
        );


    // CURRENT TASK

    if (currentTask) {

        currentTaskElement.textContent =
            currentTask.name;


        currentTimeElement.textContent =
            `${formatTime(currentTask.start)} → ${formatTime(currentTask.end)}`;


        const endTime =
            makeTodayTime(currentTask.end);


        currentCountdownElement.textContent =
            formatCountdown(
                endTime - now
            );

    } else {

        currentTaskElement.textContent =
            "No task right now";


        currentTimeElement.textContent =
            "";


        currentCountdownElement.textContent =
            "--:--:--";

    }


    // NEXT TASK

    if (nextTask) {

        nextTaskElement.textContent =
            nextTask.name;


        nextTimeElement.textContent =
            `${formatTime(nextTask.start)} → ${formatTime(nextTask.end)}`;


        const startTime =
            makeTodayTime(nextTask.start);


        nextCountdownElement.textContent =
            formatCountdown(
                startTime - now
            );

    } else {

        nextTaskElement.textContent =
            "No more tasks today";


        nextTimeElement.textContent =
            "";


        nextCountdownElement.textContent =
            "--:--:--";

    }


    renderHomeTasks(
        tasks,
        currentMinutes
    );
}


function renderHomeTasks(
    tasks,
    currentMinutes
) {

    homeTaskList.innerHTML = "";


    tasks.forEach(task => {

        const start =
            timeToMinutes(task.start);

        const end =
            timeToMinutes(task.end);


        let status = "";


        if (currentMinutes >= end) {

            status = "done";

        }

        else if (
            currentMinutes >= start &&
            currentMinutes < end
        ) {

            status = "current";

        }


        const row =
            document.createElement("div");


        row.className =
            `task ${status}`;


        row.innerHTML = `
            <div class="task-dot"></div>

            <div class="task-time">
                ${formatTime(task.start)}
            </div>

            <div class="task-name">
                ${escapeHTML(task.name)}
            </div>
        `;


        homeTaskList.appendChild(row);

    });
}


// --------------------------------------------------
// TIMETABLE EDITOR
// --------------------------------------------------

function renderDays() {

    daysElement.innerHTML = "";


    dayOrder.forEach(day => {

        const button =
            document.createElement("button");


        button.type = "button";


        button.className =
            "day-button" +
            (
                day === selectedDay
                    ? " active"
                    : ""
            );


        button.textContent = day;


        button.addEventListener(
            "click",
            () => {

                selectedDay = day;

                renderTimetable();

            }
        );


        daysElement.appendChild(button);

    });
}


function renderTimetable() {

    renderDays();


    dayTitleElement.textContent =
        selectedDay;


    const tasks =
        timetable[selectedDay];


    taskCountElement.textContent =
        `${tasks.length} task${tasks.length === 1 ? "" : "s"}`;


    taskListElement.innerHTML = "";


    tasks.forEach((task, index) => {

        const row =
            document.createElement("div");


        row.className = "task";


        // 🔕 marks a task whose alarm is switched off
        const mute =
            task.alarm === false ? "🔕 " : "";


        row.innerHTML = `
            <div></div>

            <div class="task-time">
                ${formatTime(task.start)}
            </div>

            <div class="task-name">
                ${mute}${escapeHTML(task.name)}
            </div>
            <div class="buttons">
                <button
                    type="button"
                    class="edit-button"
                >
                    Edit
                </button>
                
                <button
                    type="button"
                    class="delete-button"
                >
                    Delete
                </button>
            </div>
        `;


        // EDIT

        row.querySelector(".buttons").querySelector(".edit-button")
            .addEventListener(
                "click",
                () => openEditor(index)
            );


        // DELETE
        row.querySelector(".buttons").querySelector(".delete-button").addEventListener(
            "click",
            () => {

                const confirmed =
                    confirm(
                        `Delete "${task.name}"?`
                    );


                if (!confirmed) return;


                timetable[selectedDay]
                    .splice(index, 1);


                // Save deletion permanently

                saveTimetable();


                // Refresh screen

                renderTimetable();

                updateHome();

            }
        );
        taskListElement.appendChild(row);
    });
}


// --------------------------------------------------
// EDIT TASK
// --------------------------------------------------

function openEditor(index) {

    editingIndex = index;


    if (index === -1) {

        editorTitle.textContent =
            "Add task";


        taskName.value = "";

        taskStart.value =
            "09:00";

        taskEnd.value =
            "10:00";

        taskAlarm.checked = true;

    } else {

        const task =
            timetable[selectedDay][index];


        editorTitle.textContent =
            "Edit task";


        taskName.value =
            task.name;


        taskStart.value =
            task.start;


        taskEnd.value =
            task.end;


        taskAlarm.checked =
            task.alarm !== false;

    }


    editor.showModal();
}


addTaskButton.addEventListener(
    "click",
    () => openEditor(-1)
);


cancelButton.addEventListener(
    "click",
    () => editor.close()
);


taskForm.addEventListener(
    "submit",
    event => {

        event.preventDefault();


        const name =
            taskName.value.trim();


        const start =
            taskStart.value;


        const end =
            taskEnd.value;


        if (!name || !start || !end) {

            alert(
                "Please fill in all fields."
            );

            return;

        }


        if (end <= start) {

            alert(
                "End time must be after start time."
            );

            return;

        }


        const task = {

            name,

            start,

            end,

            alarm: taskAlarm.checked

        };


        if (editingIndex === -1) {

            timetable[selectedDay]
                .push(task);

        } else {

            timetable[selectedDay]
                [editingIndex] = task;

        }


        timetable[selectedDay].sort(
            (a, b) =>
                a.start.localeCompare(b.start)
        );


        saveTimetable();


        editor.close();


        renderTimetable();

        updateHome();

    }
);


// --------------------------------------------------
// SETTINGS PAGE
// --------------------------------------------------

function renderSettings() {

    setEnabled.checked = settings.alarmsEnabled;

    setLead.value = settings.leadMinutes;

    setHold.value = settings.holdSeconds;

    setMath.value = settings.mathCount;

    setVolume.checked = settings.maxVolume;

    setVibrate.checked = settings.vibrate;

    renderAlarmInfo();

    refreshPermissions();
}


function renderAlarmInfo() {

    if (!alarmInfo) return;


    if (!StudyAlarm) {

        alarmInfo.textContent = noPluginMessage();

        return;

    }


    if (!settings.alarmsEnabled) {

        alarmInfo.textContent =
            "Alarms are off.";

        return;

    }


    if (lastSync && lastSync.nextAlarmAt > 0) {

        const when =
            new Date(lastSync.nextAlarmAt).toLocaleString(
                "en-US",
                {
                    weekday: "long",
                    hour: "numeric",
                    minute: "2-digit"
                }
            );

        alarmInfo.textContent =
            `Next alarm: ${when}`;

    } else {

        alarmInfo.textContent =
            "No alarms scheduled.";

    }
}


async function refreshPermissions() {

    if (!StudyAlarm) {

        permissionList.textContent =
            "Only available in the Android app.";

        return;

    }


    try {

        const status =
            await StudyAlarm.getStatus();


        permissionList.innerHTML = "";


        addPermissionRow(
            "Notifications",
            status.notifications,
            "notifications"
        );

        addPermissionRow(
            "Full-screen alarm over the lock screen",
            status.fullScreen,
            "fullScreen"
        );

        addPermissionRow(
            "Unrestricted battery use (most reliable)",
            status.batteryUnrestricted,
            "battery"
        );

    } catch (error) {

        console.error("Could not read status:", error);

    }
}


function addPermissionRow(label, ok, type) {

    const row =
        document.createElement("div");

    row.className = "perm";


    const text =
        document.createElement("span");

    text.textContent =
        (ok ? "✅ " : "⚠️ ") + label;

    row.appendChild(text);


    if (!ok) {

        const button =
            document.createElement("button");

        button.type = "button";

        button.className = "edit-button";

        button.textContent = "Fix";

        button.addEventListener(
            "click",
            () => StudyAlarm.openSettings({ type })
        );

        row.appendChild(button);

    }


    permissionList.appendChild(row);
}


function bindSettings() {

    setEnabled.addEventListener(
        "change",
        () => {

            settings.alarmsEnabled = setEnabled.checked;

            saveSettings();

        }
    );


    setLead.addEventListener(
        "change",
        () => {

            settings.leadMinutes =
                clampInt(setLead.value, 0, 240, 10);

            setLead.value = settings.leadMinutes;

            saveSettings();

        }
    );


    setHold.addEventListener(
        "change",
        () => {

            settings.holdSeconds =
                clampInt(setHold.value, 1, 60, 5);

            setHold.value = settings.holdSeconds;

            saveSettings();

        }
    );


    setMath.addEventListener(
        "change",
        () => {

            settings.mathCount =
                clampInt(setMath.value, 0, 10, 0);

            setMath.value = settings.mathCount;

            saveSettings();

        }
    );


    setVolume.addEventListener(
        "change",
        () => {

            settings.maxVolume = setVolume.checked;

            saveSettings();

        }
    );


    setVibrate.addEventListener(
        "change",
        () => {

            settings.vibrate = setVibrate.checked;

            saveSettings();

        }
    );


    testAlarmButton.addEventListener(
        "click",
        async () => {

            if (!StudyAlarm) {

                alert(noPluginMessage());

                return;

            }


            try {

                // make sure the newest settings are on the native side first
                await syncNative();

                await StudyAlarm.testAlarm({ seconds: 10 });

                alarmInfo.textContent =
                    "Test alarm in 10 seconds. Lock the phone or close the app now.";

            } catch (error) {

                alert("Could not start the test alarm: " + (error && error.message ? error.message : error));

            }

        }
    );


    // coming back from Android's settings screens
    document.addEventListener(
        "visibilitychange",
        () => {

            if (
                document.visibilityState === "visible" &&
                !settingsPage.classList.contains("hidden")
            ) {

                refreshPermissions();

            }

        }
    );
}


// --------------------------------------------------
// NAVIGATION
// --------------------------------------------------

function showPage(name) {

    const pages = {
        home: homePage,
        timetable: timetablePage,
        settings: settingsPage
    };

    const buttons = {
        home: homeButton,
        timetable: timetableButton,
        settings: settingsButton
    };


    for (const key of Object.keys(pages)) {

        pages[key].classList.toggle("hidden", key !== name);

        buttons[key].classList.toggle("active", key === name);

    }


    if (name === "home") updateHome();

    if (name === "timetable") renderTimetable();

    if (name === "settings") renderSettings();
}


homeButton.addEventListener(
    "click",
    () => showPage("home")
);


timetableButton.addEventListener(
    "click",
    () => showPage("timetable")
);


settingsButton.addEventListener(
    "click",
    () => showPage("settings")
);


// --------------------------------------------------
// SECURITY
// --------------------------------------------------

function escapeHTML(text) {

    const div =
        document.createElement("div");


    div.textContent = text;


    return div.innerHTML;
}


// --------------------------------------------------
// START
// --------------------------------------------------

loadTimetable();

loadSettings();

bindSettings();

syncNative();

renderTimetable();

updateHome();


setInterval(
    updateHome,
    1000
);
