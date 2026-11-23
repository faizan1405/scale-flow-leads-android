# Scale Flow Leads — Android App Setup Guide

## 1. Connect Your Google Sheet (2 minutes)
1. Open your **Scale Flow Leads** Google Sheet on your computer.
2. In the top menu, click **Extensions → Apps Script**.
3. Delete any existing code in `Code.gs` and paste the entire contents of `google-apps-script/Code.gs`.
4. *(Optional)* Inside `Code.gs`, set a short password in `API_KEY: "your-pin"` so only your app can access the sheet.
5. Click **Deploy → New deployment**.
6. Click the gear icon next to *Select type* and choose **Web app**.
7. Set:
   - **Execute as:** `Me`
   - **Who has access:** `Anyone`
8. Click **Deploy**, click **Authorize access**, choose your Google account, and click **Allow**.
9. Copy the **Web app URL** (it looks like `https://script.google.com/macros/s/.../exec`).

## 2. Install the APK on Android (Android 13 & above)
1. Send `app-release.apk` to your wife's Android phone (via WhatsApp, Google Drive, or USB).
2. Tap the APK file on her phone.
3. If prompted, allow **Install unknown apps** for WhatsApp/Files, then tap **Install**.

## 3. Link the App to Your Sheet (30 seconds)
1. Open **Scale Flow Leads** on her phone.
2. Tap the **Settings (gear)** icon in the top-right corner (or tap **Connect** on the top banner).
3. Paste your **Apps Script Web App URL** (and API Key if you set one).
4. Tap **Save & Sync Now**.

## Features Included
- **Android 13+ Native Support** (`minSdk = 33`, `targetSdk = 35`).
- **Auto-Adapts to Your Sheet Columns:** Automatically detects Business/Name, Phone, Website, Status, Call Notes, and Follow-Up columns — plus lets you view/edit every custom column in your sheet.
- **One-Tap Call + Automatic Post-Call Notes:** Tap **Call** to open the phone dialer. As soon as she returns to the app after the call, a **Log Call Note** sheet pops up automatically with quick status chips and auto-timestamped notes.
- **One-Tap WhatsApp & Website Preview:** Jump straight into WhatsApp or open the lead's website while on a call.
- **Real-Time Sync:** Edits sync to your Google Sheet immediately; incoming changes from your computer auto-sync every 15 seconds (plus pull-to-refresh).
