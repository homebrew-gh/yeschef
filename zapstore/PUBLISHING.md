# Publishing YesChef to Zapstore

[Zapstore](https://zapstore.dev) is an Android app store built on Nostr. Its CLI, `zsp`, reads
[`../zapstore.yaml`](../zapstore.yaml), pulls the APK from this repo's GitHub Releases, and
publishes the listing as events signed with the developer's Nostr key. Users can then install and
update YesChef from the Zapstore app.

Your main Nostr key (`nsec`) and the Android keystore never go in this repo, in chat, or in CI.
CI only ever gets a separate, low-power "delegate" key that can publish releases of YesChef but
can't change who owns the listing.

## Before you start

- The release signing secrets are set (see "Release signing key" in the README) and at least one
  `build-N` release with a `yeschef-*.apk` exists on the Releases page.
- You have `yeschef.jks` and its password on your computer.
- You can sign with your Nostr key. A bunker (NIP-46, for example Amber on Android or nsec.app)
  is safest because the key never touches your computer.
- Install `zsp`: download the binary for your computer from
  [zsp releases](https://github.com/zapstore/zsp/releases), or `go install github.com/zapstore/zsp/v2/cmd/zsp@latest`.

## One-time setup (by hand, on your computer)

1. `cd` into your local clone of this repo, on an up-to-date `main`.
2. Run `zsp` and follow the wizard. It reads `zapstore.yaml`, asks how to sign (choose the
   bunker, or put `SIGN_WITH=nsec1…` in a `.env` file here, `chmod 600 .env`; `.env` is already
   git-ignored), and asks for `yeschef.jks` and its password. It publishes a **C1 proof** that the
   certificate the APKs are signed with belongs to your Nostr key, then the first release.
3. For automatic publishing, create a brand-new Nostr key just for CI (for example a new account in
   any Nostr app; it doesn't need a profile). Run `zsp` again, choose **Set up CI/CD publishing
   (advanced)**, and paste that key's **npub**. Your proof now names it as a delegate.
4. In GitHub: **Settings > Secrets and variables > Actions > New repository secret**, name
   `ZAPSTORE_SIGN_WITH`, value the CI key's **nsec**. Never put your main nsec here.

## Each release

With `ZAPSTORE_SIGN_WITH` set, every push to `main` builds and signs the APK, creates the GitHub
Release, and then the `zapstore` job in `.github/workflows/android.yml` runs `zsp publish`.
Without the secret, the job is skipped and you can publish by hand from your clone:

```sh
zsp publish
```

To rotate a leaked CI key, run `zsp` again, choose CI/CD with a new npub, and replace the secret.
To change the listing text, icon or screenshots, edit `zapstore.yaml` and the files it points to;
the next publish picks them up.
