package com.clubhub.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a believable semester of club activity into the demo schema: a live recruitment drive with
 * applicants at every pipeline stage, past events with check-ins and certificates, upcoming events
 * with registrations, and an audit trail.
 *
 * All dates are relative to "now", so the demo never looks stale. Plain JDBC with schema-qualified
 * names on purpose: the seed runs outside any request, so there is no tenant context to route JPA.
 */
@Component
class DemoSeeder {

    static final ZoneId CAMPUS = ZoneId.of("Asia/Kolkata");

    /** Everyone in the demo club's world. Keys are stable handles used below. */
    static final Map<String, String> CAST = Map.ofEntries(
            Map.entry("ananya", "Ananya Iyer"), Map.entry("rohan", "Rohan Mehta"),
            Map.entry("kavya", "Kavya Nair"), Map.entry("arjun", "Arjun Reddy"),
            Map.entry("priya", "Priya Sharma"), Map.entry("vikram", "Vikram Singh"),
            Map.entry("sneha", "Sneha Patel"), Map.entry("aditya", "Aditya Rao"),
            Map.entry("ishaan", "Ishaan Gupta"), Map.entry("meera", "Meera Krishnan"),
            Map.entry("karthik", "Karthik Subramanian"), Map.entry("diya", "Diya Menon"),
            Map.entry("rahul", "Rahul Verma"), Map.entry("nisha", "Nisha Joshi"),
            Map.entry("siddharth", "Siddharth Das"), Map.entry("tanvi", "Tanvi Kulkarni"));

    static final List<String> CORE = List.of("ananya", "rohan");
    static final List<String> MEMBERS = List.of("kavya", "arjun", "priya", "vikram", "sneha", "aditya");

    private final JdbcTemplate jdbc;

    DemoSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs inside the caller's transaction; the schema has just been (re)created and is empty. */
    void seed(String schema, UUID tenantId, UUID visitor, Map<String, UUID> people) {
        new Run(schema, tenantId, visitor, people).all();
    }

    private final class Run {
        private final String s;
        private final UUID tenantId;
        private final UUID visitor;
        private final Map<String, UUID> people;
        private final Instant now = Instant.now();

        Run(String schema, UUID tenantId, UUID visitor, Map<String, UUID> people) {
            this.s = schema + ".";
            this.tenantId = tenantId;
            this.visitor = visitor;
            this.people = people;
        }

        void all() {
            profile();
            memberships();
            openDrive();
            closedDrive();
            draftDrive();
            events();
            notifications();
        }

        private UUID id(String handle) {
            return people.get(handle);
        }

        private Timestamp daysAgo(double days) {
            return Timestamp.from(now.minus(Duration.ofMinutes(Math.round(days * 24 * 60))));
        }

        /** A wall-clock time on campus, {@code days} from today. */
        private Timestamp onCampus(int days, int hour, int minute) {
            return Timestamp.from(LocalDate.now(CAMPUS).plusDays(days).atTime(LocalTime.of(hour, minute))
                    .atZone(CAMPUS).toInstant());
        }

        private void profile() {
            jdbc.update("INSERT INTO " + s + "club_profile (display_name, description, contact_email, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    DemoAccounts.CLUB_NAME,
                    "We design, build and fight robots: line followers, autonomous rovers and a 15 kg combat bot for"
                            + " RoboWars. Weekly build nights, workshops for first-years, and a competition team that"
                            + " travels. (This is a sandbox: change anything, it resets itself every hour.)",
                    "robotics@" + DemoAccounts.DOMAIN, daysAgo(120), daysAgo(3));
        }

        private void memberships() {
            membership(visitor, "CLUB_ADMIN", 120);
            CORE.forEach(h -> membership(id(h), "CORE", 110));
            for (int i = 0; i < MEMBERS.size(); i++) {
                membership(id(MEMBERS.get(i)), "MEMBER", 90 - i * 8);
            }
        }

        private void membership(UUID userId, String role, int daysAgo) {
            jdbc.update("INSERT INTO public.memberships (id, user_id, tenant_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, tenantId, role, daysAgo(daysAgo));
            if (!userId.equals(visitor)) {
                audit(visitor, "MEMBER_ADDED", "USER", userId.toString(), "{\"role\":\"" + role + "\"}", daysAgo(daysAgo));
            }
        }

        // ---- recruitment

        private long drive(String title, String description, String status, Timestamp closesAt, double createdDaysAgo) {
            Long driveId = jdbc.queryForObject("INSERT INTO " + s + "recruitment_drives"
                            + " (title, description, status, closes_at, created_by, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                    title, description, status, closesAt, id("ananya"), daysAgo(createdDaysAgo), daysAgo(createdDaysAgo));
            audit(id("ananya"), "DRIVE_CREATED", "DRIVE", String.valueOf(driveId),
                    "{\"title\":\"" + title + "\"}", daysAgo(createdDaysAgo));
            return driveId;
        }

        private long question(long driveId, int order, String prompt, boolean required) {
            return jdbc.queryForObject("INSERT INTO " + s + "drive_questions (drive_id, sort_order, prompt, required)"
                    + " VALUES (?, ?, ?, ?) RETURNING id", Long.class, driveId, order, prompt, required);
        }

        private void openDrive() {
            long drive = drive("Core team 2026: build & software",
                    "We're taking 6 people across mechanical, electronics and software. No experience needed, just"
                            + " proof you like making things. Shortlisted people get a 20-minute chat with the leads.",
                    "OPEN", onCampus(9, 23, 59), 14);
            long q1 = question(drive, 1, "Which sub-team do you want to join: mechanical, electronics or software?", true);
            long q2 = question(drive, 2, "Tell us about something you built, however small.", true);
            long q3 = question(drive, 3, "GitHub, portfolio or anything else we should look at (optional)", false);
            List<Long> questions = List.of(q1, q2, q3);

            apply(drive, questions, "ishaan", 2.5, List.of("APPLIED"), null,
                    "Software", "A Telegram bot that tells my hostel which washing machines are free.", "github.com/ishaan-g");
            apply(drive, questions, "siddharth", 1.2, List.of("APPLIED"), null,
                    "Mechanical", "Rebuilt my cycle's gear system after it broke on the way to class.", "");
            apply(drive, questions, "meera", 10, List.of("APPLIED", "SHORTLISTED"), null,
                    "Electronics", "An ESP32 plant-watering setup with a soil moisture sensor.", "meera.dev");
            apply(drive, questions, "tanvi", 8, List.of("APPLIED", "SHORTLISTED"), null,
                    "Software", "Path-planning visualiser for A* and Dijkstra in the browser.", "github.com/tanvik");
            apply(drive, questions, "karthik", 12, List.of("APPLIED", "SHORTLISTED", "INTERVIEW"), "Interview Thu 5 pm, TP 401",
                    "Mechanical", "Designed and 3D-printed a gripper for a school project.", "");
            apply(drive, questions, "diya", 11, List.of("APPLIED", "SHORTLISTED", "INTERVIEW"), "Strong CAD skills",
                    "Mechanical", "Chassis design for a college go-kart team in first year.", "grabcad.com/diya.menon");
            apply(drive, questions, "rahul", 13, List.of("APPLIED", "SHORTLISTED", "INTERVIEW", "SELECTED"), "Great interview, welcome aboard",
                    "Electronics", "Motor driver board for a line follower, PCB designed in KiCad.", "github.com/rahulv");
            apply(drive, questions, "nisha", 12.5, List.of("APPLIED", "SHORTLISTED", "REJECTED"), "Encouraged to apply next semester",
                    "Software", "Learning Python, built a to-do app following a tutorial.", "");
        }

        /** One application that walked {@code path} through the pipeline, with its full history. */
        private void apply(long driveId, List<Long> questions, String applicant, double submittedDaysAgo,
                           List<String> path, String lastNote, String... answers) {
            UUID applicantId = id(applicant);
            String status = path.getLast();
            List<Timestamp> times = new ArrayList<>();
            for (int step = 0; step < path.size(); step++) {
                // each move a day or so after the previous one, never in the future
                times.add(daysAgo(Math.max(submittedDaysAgo - step * 1.3, 0.1)));
            }
            Long applicationId = jdbc.queryForObject("INSERT INTO " + s + "applications"
                            + " (drive_id, applicant_user_id, status, submitted_at, updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id",
                    Long.class, driveId, applicantId, status, times.getFirst(), times.getLast());
            for (int i = 0; i < questions.size() && i < answers.length; i++) {
                if (!answers[i].isBlank()) {
                    jdbc.update("INSERT INTO " + s + "application_answers (application_id, question_id, answer) VALUES (?, ?, ?)",
                            applicationId, questions.get(i), answers[i]);
                }
            }
            for (int step = 0; step < path.size(); step++) {
                boolean first = step == 0;
                UUID actor = first ? applicantId : id(step % 2 == 1 ? "ananya" : "rohan");
                String note = step == path.size() - 1 && !first ? lastNote : null;
                jdbc.update("INSERT INTO " + s + "application_status_changes"
                                + " (application_id, from_status, to_status, changed_by, note, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
                        applicationId, first ? null : path.get(step - 1), path.get(step), actor, note, times.get(step));
                if (!first) {
                    audit(actor, "APPLICATION_MOVED", "APPLICATION", String.valueOf(applicationId),
                            "{\"from\":\"" + path.get(step - 1) + "\",\"to\":\"" + path.get(step) + "\"}", times.get(step));
                }
            }
        }

        private void closedDrive() {
            long drive = drive("Tech fest volunteers",
                    "Help run the robotics arena at the tech fest: setup, timing and crowd control.",
                    "CLOSED", onCampus(-20, 23, 59), 35);
            List<Long> questions = List.of(question(drive, 1, "Which fest days can you make?", true));
            apply(drive, questions, "kavya", 30, List.of("APPLIED", "SHORTLISTED", "SELECTED"), null, "All three days");
            apply(drive, questions, "arjun", 29, List.of("APPLIED", "SHORTLISTED", "SELECTED"), null, "Day 1 and 2");
            apply(drive, questions, "aditya", 27, List.of("APPLIED", "REJECTED"), "Team was full", "Day 3 only");
        }

        private void draftDrive() {
            long drive = drive("RoboWars pit crew",
                    "Draft: a small crew to handle repairs between RoboWars rounds. Not published yet.",
                    "DRAFT", onCampus(25, 23, 59), 1);
            question(drive, 1, "Have you used power tools before? Which ones?", true);
            question(drive, 2, "Can you travel for the national round?", true);
        }

        // ---- events

        private long event(String title, String description, String venue, int day, int startHour, int endHour,
                           Integer capacity, String visibility, String status, int createdDaysAgo) {
            Long eventId = jdbc.queryForObject("INSERT INTO " + s + "events (title, description, venue, starts_at, ends_at,"
                            + " capacity, visibility, status, created_by, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                    title, description, venue, onCampus(day, startHour, 0), onCampus(day, endHour, 30), capacity,
                    visibility, status, id("rohan"), daysAgo(createdDaysAgo), daysAgo(createdDaysAgo));
            audit(id("rohan"), "EVENT_CREATED", "EVENT", String.valueOf(eventId), "{\"title\":\"" + title + "\"}",
                    daysAgo(createdDaysAgo));
            if (status.equals("PUBLISHED")) {
                audit(id("rohan"), "EVENT_PUBLISHED", "EVENT", String.valueOf(eventId), "{\"title\":\"" + title + "\"}",
                        daysAgo(createdDaysAgo - 0.5));
            }
            return eventId;
        }

        private void register(long eventId, int registeredDaysAgo, List<UUID> users) {
            for (int i = 0; i < users.size(); i++) {
                jdbc.update("INSERT INTO " + s + "event_registrations (event_id, user_id, registered_at) VALUES (?, ?, ?)",
                        eventId, users.get(i), daysAgo(registeredDaysAgo - i * 0.2));
            }
        }

        private List<UUID> ids(String... handles) {
            return java.util.Arrays.stream(handles).map(this::id).toList();
        }

        /** The first {@code byQr} attendees scanned their ticket at the door; the rest were ticked off by hand. */
        private void attend(long eventId, int day, int startHour, int byQr, List<UUID> attendees) {
            for (int i = 0; i < attendees.size(); i++) {
                jdbc.update("INSERT INTO " + s + "event_attendance (event_id, user_id, method, checked_in_by, checked_in_at)"
                                + " VALUES (?, ?, ?, ?, ?)", eventId, attendees.get(i), i < byQr ? "QR" : "MANUAL",
                        id("rohan"), Timestamp.from(onCampus(day, startHour, 0).toInstant().plus(Duration.ofMinutes(5L + i * 3))));
            }
        }

        private void certify(long eventId, String eventTitle, List<UUID> attendees, int issuedDaysAgo) {
            for (UUID attendee : attendees) {
                jdbc.update("INSERT INTO " + s + "certificates (id, user_id, event_id, title, recipient_name, description,"
                                + " issued_by, issued_at) VALUES (?, ?, ?, ?, (SELECT full_name FROM public.users WHERE id = ?), ?, ?, ?)",
                        UUID.randomUUID(), attendee, eventId, "Certificate of Participation", attendee,
                        "For taking part in " + eventTitle + ", organised by " + DemoAccounts.CLUB_NAME + ".",
                        id("ananya"), daysAgo(issuedDaysAgo));
            }
            audit(id("ananya"), "CERTIFICATES_ISSUED", "EVENT", String.valueOf(eventId),
                    "{\"issued\":" + attendees.size() + "}", daysAgo(issuedDaysAgo));
        }

        private void events() {
            // past, attended, no certificates yet: the visitor can issue them in one click
            long arduino = event("Arduino basics for first-years",
                    "Blink an LED, read a sensor, drive a servo. Kits provided, bring a laptop.",
                    "Tech Park, TP 302", -40, 16, 18, 80, "PUBLIC", "PUBLISHED", 50);
            register(arduino, 47, ids("kavya", "arjun", "priya", "vikram", "sneha", "aditya", "ishaan", "siddharth", "meera", "tanvi"));
            attend(arduino, -40, 16, 5, ids("kavya", "arjun", "priya", "ishaan", "meera", "vikram", "tanvi"));

            // past, attended, certificates issued
            String rosTitle = "Intro to ROS 2 workshop";
            long ros = event(rosTitle,
                    "Nodes, topics and a simulated TurtleBot in Gazebo. Ubuntu 24.04 VM image shared a day before.",
                    "Tech Park, TP 401", -12, 16, 18, 60, "PUBLIC", "PUBLISHED", 25);
            register(ros, 20, ids("ananya", "rohan", "kavya", "arjun", "priya", "vikram", "sneha", "aditya",
                    "karthik", "diya", "rahul", "nisha"));
            List<UUID> rosAttendees = ids("kavya", "arjun", "priya", "sneha", "aditya", "karthik", "diya", "rahul", "nisha");
            attend(ros, -12, 16, 7, rosAttendees);
            certify(ros, rosTitle, rosAttendees, 11);

            // upcoming: the visitor holds a ticket, so the QR flow works end to end
            long build = event("Line-follower build night",
                    "Build and tune a PID line follower in teams of three. Fastest lap wins a motor driver board.",
                    "Robotics Lab, Tech Park 1105", 3, 18, 21, 40, "PUBLIC", "PUBLISHED", 6);
            List<UUID> builders = new ArrayList<>(List.of(visitor));
            builders.addAll(ids("kavya", "arjun", "vikram", "ishaan", "meera", "tanvi", "siddharth"));
            register(build, 5, builders);

            long trials = event("RoboWars qualifier trials",
                    "Members only. Weapon spin-up tests and two practice bouts before we pick the travelling team.",
                    "Mechanical workshop, ground floor", 11, 10, 16, null, "MEMBERS", "PUBLISHED", 4);
            register(trials, 3, ids("ananya", "rohan", "kavya", "arjun", "sneha"));

            event("Alumni AMA: careers in robotics",
                    "Draft: three alumni from industry and grad school. Speakers still confirming.",
                    "Mini Hall 2, University Building", 20, 17, 18, 150, "PUBLIC", "DRAFT", 1);

            upcoming = List.of(build, trials);
        }

        private List<Long> upcoming = List.of();

        /** A couple of unread notifications, so the bell and the live feed have something to show. */
        private void notifications() {
            String[] titles = {"New event: Line-follower build night", "New event: RoboWars qualifier trials"};
            for (int i = 0; i < upcoming.size(); i++) {
                long eventId = upcoming.get(i);
                jdbc.update("INSERT INTO public.notifications (user_id, tenant_id, source_event_id, type, title, body, link,"
                                + " created_at) VALUES (?, ?, ?, 'EVENT_PUBLISHED', ?, ?, ?, ?)",
                        visitor, tenantId, UUID.randomUUID(), titles[i],
                        DemoAccounts.CLUB_NAME + " published an event. Register to get your QR ticket.",
                        "/clubs/" + DemoAccounts.CLUB_SLUG + "/events/" + eventId, daysAgo(5 - i * 1.5));
            }
        }

        private void audit(UUID actor, String action, String targetType, String targetId, String detailsJson, Timestamp at) {
            jdbc.update("INSERT INTO " + s + "audit_log (actor_id, action, target_type, target_id, details, occurred_at)"
                    + " VALUES (?, ?, ?, ?, ?::jsonb, ?)", actor, action, targetType, targetId, detailsJson, at);
        }
    }
}
