# DJACK Mail

A personal Android email app. Add as many IMAP or POP3 accounts as you like, read them in one
combined inbox, and send mail through each account's SMTP server. Built for sideloading
(no Play Store needed).

## What it does

- Multiple accounts, IMAP or POP3, with SSL, STARTTLS or no encryption
- One combined inbox, with a filter for each account
- **Unread mail stands out:** a blue dot, bold text and a tinted row. Read mail is lighter. The title shows how many are unread.
- **Folders (IMAP):** pick which folders to download (Sent, Drafts, Archive, and so on) from
  Accounts > the folder button next to an account. Inbox and Sent are on by default.
- **Background sync and notifications:** new mail is checked for automatically (every 15 minutes
  to 2 hours, your choice in Settings) and you get a notification. Tapping it opens the message.
- **Attachments:** received attachments are listed on the message; Open or Save them. You can
  attach files when writing (paperclip button in the top bar).
- **Reply, Reply all and Forward**, with the original quoted. Forward includes the original
  attachments. Replies are threaded correctly in other mail apps.
- Cc and Bcc
- Sent mail is also saved to your Sent folder on IMAP accounts (Gmail does this by itself)
- Passwords are stored encrypted on the phone (Android Keystore)
- **Delete:** press and hold a message to start selecting, tap more to add them, then tap the bin. You can also delete from inside a message.

## Getting the APK without installing anything (GitHub, free)

The repository already contains a build workflow. Every time the code is updated:

1. Open the **Actions** tab of the repository.
2. Open the newest **Build APK** run (about 5 to 8 minutes).
3. When it is green, download **DJACK-Mail-debug-apk** from the bottom of the page and unzip it to get
   `app-debug.apk`.

## Installing on your phone

1. Get `app-debug.apk` onto the phone (email it to yourself, Google Drive, or USB).
2. Tap it. Android will ask to allow installs from that app (Files, Chrome, Drive); allow it.
3. Open DJACK Mail. Updating over an existing install keeps your accounts and downloaded mail.
   (If an update ever fails to install, uninstall the app first, which means adding accounts again.)

When the app asks to send notifications, choose Allow.

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

## Things to know

- Background checks are scheduled by Android. They run at most as often as you choose, but battery
  saver and Doze mode can delay them, so mail may sometimes show up late. This is not push email.
- Each refresh downloads the newest 40 messages per enabled folder. With IMAP, attachments are only
  downloaded when you open or save them. With POP3, the whole message is downloaded.
- POP3 accounts only have an inbox (that is how POP3 works).
- HTML mail is shown as plain text. Pictures inside messages are not shown.
- Deleting also deletes on the mail server: IMAP mail goes to the Trash folder; mail already in Trash, POP3 mail, and accounts with no Trash folder are deleted permanently.
- You can't archive or move messages from the app yet, and drafts are not saved.
- This is a debug build, which is fine for personal use.

## Project layout

- `data/` database (Room), encrypted password storage, settings, repository
- `mail/MailEngine.kt` all IMAP, POP3 and SMTP code (JavaMail for Android)
- `sync/` background checks and notifications (WorkManager)
- `ui/` the screens (Jetpack Compose)
