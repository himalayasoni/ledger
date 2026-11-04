# Ledger Android app

A small Android shell around the Ledger web app (a Trusted Web Activity), plus native extras:

- **Home-screen widget**: Quick add and Say it.
- **Daily reminder** notification at a time you pick in Ledger → Settings.
- **Quick Settings tile**: pull down the shade → Quick add.

The screens themselves load from https://himalayasoni.github.io/ledger/, so web updates reach the app on their own.
Native changes need a new APK: bump `vc` in `app/build.gradle` and push. GitHub Actions builds it, signs it with the
key in `ledger.p12.enc` (unlocked by the `LEDGER_KEY_PASS` secret) and publishes it as the latest release.

First release: widget, daily reminder, Quick Settings tile.
