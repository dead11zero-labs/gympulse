# Privacy Policy – GymPulse

Last updated: [DATE] · Version 0.x

> **Draft.** This policy describes the technical state of the app. It has not yet been reviewed by a lawyer and must be checked and completed by Jörg before it is published on the Play Store.

## 1. Responsible party

[PROVIDER – name and address to be added by Jörg]

Contact for all privacy questions: [KONTAKT-EMAIL]

## 2. Basic principle: everything stays on your device

GymPulse is an offline-first app. All data you enter is stored in a local database on your Android device. The app

- does not require a user account,
- does not transfer your training data, plans, exercises, body measurements or settings to a server operated by the provider – unless you turn on the optional cloud backup and start a backup yourself (section 7),
- works during your workouts even without an internet connection.

## 3. Data stored in the app

Depending on how you use it, GymPulse stores the following on your device:

- **Profile and settings:** name, optional height, body weight, weekly goal, week start, rest timer, theme and language,
- **Training:** workouts with date, duration, exercises, sets, weights, repetitions and notes,
- **Plans and calendar:** training plans, weekly schedule and exceptions (for example moved training days),
- **Library:** exercises and equipment, including the ones you create yourself and their settings,
- **Body measurements:** weight, body fat and circumferences, each with a date.

You decide which information you enter. Required fields are limited to what the app needs.

## 4. Internet connection and third parties

- **Exercise images:** when you open the detail view of an exercise, the app loads its demonstration image from a GitHub server (`raw.githubusercontent.com`). For this technical connection your device necessarily sends its IP address to GitHub. GitHub's privacy policy applies to this connection. GymPulse itself does not receive any data from these requests. Images may be cached locally.
- As of the current version, GymPulse contains no advertising, tracking or crash-reporting services and sends no usage statistics.

## 5. Permissions

- **Internet:** to load exercise images (see section 4) and, only if you turn it on, for sign-in and cloud backup (see section 7).
- **Notifications:** show a notification during an active workout so the session can be resumed easily.
- **Foreground service:** keeps the active workout and rest timer running while you train.
- **Health Connect (optional):** write exercise sessions and read body weight, heart rate, steps, active calories and – with a separate switch – sleep (see section 8).

## 6. Export and import

With "Export and import" you can save your data as a JSON file and read it back in later. You choose the storage location through the Android file picker. The file only leaves your device if you share it yourself or save it to a location others can access.

## 7. Cloud backup (optional, opt-in)

Cloud backup is turned **off** by default. It only becomes active when you turn it on yourself under "Cloud backup" in the settings and sign in with your Google account. Without these steps GymPulse does not send any data to a server.

- **Sign-in:** sign-in uses "Sign in with Google" (Android Credential Manager). Google passes a sign-in token with your Google account ID, your email address and, where applicable, your name and profile picture to our backend service Supabase. An account with this email address and a random user ID is created there.
- **What is backed up:** when you tap "Back up now", the app uploads the same JSON file that the export creates (section 6): profile and settings, workouts, plans, calendar, your own exercises and equipment, and body measurements. Only the latest backup is stored per account, together with the time of the upload. Progress photos are not uploaded.
- **No automatic upload:** a backup is only made when you start it. "Restore" loads the backup back onto your device after you confirm.
- **Access protection:** the transfer is encrypted with HTTPS. An access rule in the database (row level security) ensures that only your signed-in account can read, change or delete your backup.
- **Service providers:** the data is stored with Supabase (Supabase Inc.); sign-in is provided by Google (Google Ireland Limited). [To be checked by Jörg: region of the Supabase project, data processing agreement, legal basis (consent, Art. 6(1)(a) GDPR and Art. 9(2)(a) GDPR for health data such as body measurements) and information on transfers to third countries.]
- **Withdrawal and deletion:** you can remove the backup at any time in the settings with "Delete cloud backup", sign out and turn cloud backup off. Turning it off or signing out does not delete an existing backup automatically.

## 8. Health Connect (optional, opt-in)

The connection to Android Health Connect is **off** by default. It only becomes active when you turn it on in the settings under "Health data" and grant the permissions in the Health Connect dialog. Each permission can be granted separately and revoked in Health Connect at any time.

- **Writing:** when you finish a workout, GymPulse writes it to Health Connect as an exercise session (start and end time, title, notes, exercise type).
- **Reading – body weight:** GymPulse reads your latest body weight to show it in the settings. It is saved as a body measurement only when you tap "Import".
- **Reading – heart rate, steps, active calories (optional):** after you finish a workout, GymPulse reads the average and maximum heart rate, steps and active calories for exactly the time of that workout and shows them in the workout summary.
- **Reading – sleep (optional, separate switch):** only if you also turn on the "Sleep" switch, GymPulse reads the duration of your last sleep before the workout and shows it in the summary.
- **Origin of the data:** values that are read can come from any app that you have allowed to write to Health Connect (for example fitness, smartwatch or health apps). GymPulse does not connect to these apps directly.
- **Processing:** heart rate, steps, calories and sleep are only used for display on your device. They are not saved in the GymPulse database, not exported and not included in the cloud backup. Health data from Health Connect is not sent to servers, not used for advertising and not shared with third parties.
- **Withdrawal:** you can turn the connection off in the GymPulse settings at any time and revoke the permissions in Health Connect.

[To be reviewed by Jörg: legal basis for health data (consent, Art. 9(2)(a) GDPR) and details for the Health Connect review in the Play Store.]

## 9. Android system backup

GymPulse is configured to take part in the Android system backup. If you have enabled a backup of app data in your Android settings (for example via your Google account), Android may back up the app's data according to the rules of the operating system. This backup is performed by Android or Google, not by GymPulse. [To be checked by Jörg: define backup rules or deliberately allow the default.]

## 10. No sharing, no advertising

GymPulse does not sell data, does not share data with third parties and shows no advertising. The exceptions are the technically required loading of exercise images from GitHub (section 4) and – only with cloud backup turned on – Google as sign-in service and Supabase as hosting provider (section 7).

## 11. Planned features (not yet available)

The following features are planned but are **not** included in the current version. This policy will be updated when they are released. They are optional and will only be activated with your consent.

- **Automatic synchronisation:** continuous syncing of your data between several devices. Today's cloud backup (section 7) works manually only.

## 12. Retention and deletion

Your data stays on your device until you delete it in the app (individual workouts, exercises, plans or measurements). A cloud backup is kept until you remove it in the settings with "Delete cloud backup" or replace it with a new backup; to delete your account, contact [KONTAKT-EMAIL]. If you uninstall GymPulse, the data stored locally by the app is removed. Any backup made by Android (section 9) is not affected and is managed in your Android settings.

## 13. Your rights

Because the provider does not receive your training data, it remains entirely with you. If you have questions about privacy, or a request about data you have sent to us (for example by email), please contact [KONTAKT-EMAIL]. You have the statutory rights to access, rectification, erasure, restriction and objection, and the right to lodge a complaint with a data protection supervisory authority. [To be checked by Jörg: wording and scope.]

## 14. Children

[To be checked by Jörg: note on use by minors.]

## 15. Changes to this policy

If features or data processing change, this policy will be updated. The date above shows the current version.
