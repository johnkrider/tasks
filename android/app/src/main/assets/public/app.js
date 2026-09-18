/*
    STUDY TIMETABLE APP

    All timetable information is stored here for now.

    Later we will move this into persistent storage so
    changes made by the user remain after closing the app.
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
// Edits
// =====================

function saveTimetable() {
    localStorage.setItem(
        "studyTimetable",
        JSON.stringify(timetable)
    );

    syncNativeAlarms();
}


// =====================
// ANDROID ALARMS
// =====================

const StudyAlarm =
    window.Capacitor && window.Capacitor.registerPlugin
        ? window.Capacitor.registerPlugin("StudyAlarm")
        : null;


function syncNativeAlarms() {

    if (!StudyAlarm) return;

    const now = new Date();
    const dayNumbers = {
        Sunday: 0,
        Monday: 1,
        Tuesday: 2,
        Wednesday: 3,
        Thursday: 4,
        Friday: 5,
        Saturday: 6
    };

    const alarms = [];
    let id = 1000;

    for (const day of dayOrder) {

        for (const task of timetable[day]) {

            if (!task.start) continue;

            const [hour, minute] = task.start.split(":").map(Number);
            const trigger = new Date(now);
            const currentDay = trigger.getDay();
            let daysAhead = (dayNumbers[day] - currentDay + 7) % 7;

            trigger.setDate(trigger.getDate() + daysAhead);
            trigger.setHours(hour, minute - 10, 0, 0);

            if (trigger.getTime() <= now.getTime()) {
                trigger.setDate(trigger.getDate() + 7);
            }

            alarms.push({
                id: id++,
                task: task.name,
                triggerAt: trigger.getTime()
            });
        }
    }

    StudyAlarm.schedule({ alarms })
        .catch(error => console.error("Could not schedule alarms:", error));
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


loadTimetable();
syncNativeAlarms();


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


const homeButton =
    document.getElementById("homeButton");

const timetableButton =
    document.getElementById("timetableButton");


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


const cancelButton =
    document.getElementById("cancelButton");


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


        row.innerHTML = `
            <div></div>

            <div class="task-time">
                ${formatTime(task.start)}
            </div>

            <div class="task-name">
                ${escapeHTML(task.name)}
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

            end

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
// NAVIGATION
// --------------------------------------------------

homeButton.addEventListener(
    "click",
    () => {

        homePage.classList.remove(
            "hidden"
        );


        timetablePage.classList.add(
            "hidden"
        );


        homeButton.classList.add(
            "active"
        );


        timetableButton.classList.remove(
            "active"
        );


        updateHome();

    }
);


timetableButton.addEventListener(
    "click",
    () => {

        homePage.classList.add(
            "hidden"
        );


        timetablePage.classList.remove(
            "hidden"
        );


        homeButton.classList.remove(
            "active"
        );


        timetableButton.classList.add(
            "active"
        );


        renderTimetable();

    }
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

renderTimetable();

updateHome();


setInterval(
    updateHome,
    1000
);