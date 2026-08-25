# Wheelie Bin Reminder (Newport City Council)

A tiny Android app that checks Newport City Council's bin collection system in the
background and sends you a notification when a bin is due for collection tomorrow.
No account, no server, no subscription — your UPRN is stored only on your phone.

## How it works

- Newport's "check your collection day" tool is powered by a system called
  iTouchVision. This app talks to that same system directly using your **UPRN**
  (Unique Property Reference Number) — a 12-digit code every UK address has.
- A background job (Android WorkManager) checks a few times a day. If a bin is due
  **tomorrow** and you haven't already been notified for that date, it shows a
  notification.
- Because it's a background check rather than an exact-time alarm, the notification
  can arrive up to a few hours after Android decides to run the check — usually
  it's much sooner. There's no server involved, so there's nothing to keep paying for
  or maintaining.

## Finding your UPRN

Open the app on each phone. You need your household's UPRN once:

1. Go to [findmyaddress.uk](https://www.findmyaddress.uk/search) (a free, official
   public lookup service) and search your postcode.
2. Select your address — it will show you the UPRN.
3. Paste the 12-digit number into the app and tap **Save and start reminders**.

Do this on both your phone and your partner's — same UPRN on both, since you share
a house.

Tap **Check now** any time to see your upcoming collection dates immediately and
confirm it's working.

## Getting the APK

Every push to `main` triggers a GitHub Actions workflow
(`.github/workflows/build-apk.yml`) that compiles the app for free on GitHub's own
build servers. Check the **Actions** tab for progress, then grab
`WheelieBinNotifier.apk` from the repo's **Releases** page — that link stays the
same and refreshes with every new build.

## Cost

Free, indefinitely, at this scale:

- **GitHub Actions**: unlimited free minutes on a public repo; 2,000 free
  minutes/month on a private repo (this build uses ~3–5 minutes per run).
- **GitHub Releases**: free file hosting for the APK, no bandwidth limit that you'd
  realistically hit for two people downloading a ~5MB file occasionally.
- **The app itself**: no backend server, so nothing to keep running or paying for.

## Notes on the data source

Newport's bin lookup is provided by a third party (iTouchVision) and identified via
Newport-specific client/council IDs. If Newport City Council changes this system,
the app would need `NewportBinApi.kt` updated to match — everything relevant lives
in that one file.
