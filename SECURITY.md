# Security policy

## Supported versions
Only the latest release (**Latest build**) is supported.

## Reporting a vulnerability
Please do not open a public issue for security problems. Use GitHub's private reporting: open the **Security** tab of
this repository and choose **Report a vulnerability**. Include the app version, your Android version and steps to
reproduce.

## What the app does and does not do
- It works through Shizuku, which gives it shell-level access to the AppOps service. Changes affect only the device it
  runs on.
- It does not request the `INTERNET` permission. No data leaves the device.
- Saved rules and history are plain files in the app's private storage. A backup is a JSON file you choose where to save.
- It asks for `QUERY_ALL_PACKAGES` to list installed apps.

## Release integrity
Release APKs are built in GitHub Actions from the `main` branch and published to the **Latest build** release. Only
install builds from this repository's Releases page.
