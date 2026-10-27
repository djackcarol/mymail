# MyMail

A personal Android email app. Add as many IMAP or POP3 accounts as you like, read them in one
combined inbox, and send mail through each account's SMTP server. Built for sideloading
(no Play Store needed).

## What it does

- Multiple accounts, IMAP or POP3, with SSL, STARTTLS or no encryption
- One combined inbox with a filter chip for each account
- Downloads the newest 40 messages per account each time you refresh (also on app start)
- Reads plain-text and HTML mail (HTML is shown as plain text; attachments are ignored)
- Write and reply, sending through the account you pick
- Passwords are stored encrypted on the phone (Android Keystore)
- Nothing is ever deleted on your mail server

## Getting the APK without installing anything (GitHub, free)

1. Make a free account at github.com and create a new **private** repository, for example `mymail`.
2. Click **Add file > Upload files** and drag in everything from this folder (keep the folder
   structure, including the hidden `.github` folder). Commit.
3. Open the **Actions** tab. The **Build APK** workflow starts by itself (or click it, then
   **Run workflow**). It takes about 5 to 8 minutes.
4. When it finishes, open the run and download **MyMail-debug-apk** at the bottom. Unzip it to get
   `app-debug.apk`.

If the build fails, open the failed step, copy the red error text, and send it to me. First builds
of a new project often need a small fix.

## Installing on your phone

1. Get `app-debug.apk` onto the phone (email it to yourself, Google Drive, or USB).
2. Tap it. Android will ask to allow installs from that app (Files, Chrome, Drive); allow it.
3. Open MyMail, tap **Add account**.

## Adding accounts

- Pick Gmail, Yahoo or iCloud for automatic server settings, or "Other" and type them in.
- Gmail, Yahoo and iCloud will not accept your normal password. Create an **app password** in
  that account's security settings and use it here (this needs 2-step verification turned on).
- Outlook.com / Hotmail personal accounts no longer allow this kind of login, so they will not work.
- Use **Test connection** before saving to check the server and login.

## Typical settings

| Provider | IMAP server | POP3 server | SMTP server |
|---|---|---|---|
| Gmail | imap.gmail.com, 993 SSL | pop.gmail.com, 995 SSL | smtp.gmail.com, 465 SSL |
| Yahoo | imap.mail.yahoo.com, 993 SSL | pop.mail.yahoo.com, 995 SSL | smtp.mail.yahoo.com, 465 SSL |
| iCloud | imap.mail.me.com, 993 SSL | n/a | smtp.mail.me.com, 587 STARTTLS |

## Known limits of this first version

- No background sync or notifications; mail is fetched when you open the app or tap refresh
- Only the inbox folder; no sent, drafts or folders
- No attachments (can't view or send them)
- Messages are read-only copies; you can't delete or archive from the app yet
- This is a debug build, which is fine for personal use. It is signed with a debug key, so
  installing a build from a different computer or repository later may require uninstalling first.

## Project layout

- `data/` database (Room), encrypted password storage, repository
- `mail/MailEngine.kt` all IMAP, POP3 and SMTP code (Jakarta/JavaMail for Android)
- `ui/` the screens (Jetpack Compose)
