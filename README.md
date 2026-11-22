# VidMax

**VidMax - All-in-One Video Downloader for Android**

VidMax is an Arabic-first Android app project. The first milestone provides a native Android foundation, accepts links shared from other apps, and downloads publicly accessible direct HTTP(S) media-file URLs using Android DownloadManager.

## Build

GitHub Actions builds a debug APK configured for both ARM ABIs:
- `armeabi-v7a` (32-bit)
- `arm64-v8a` (64-bit)

Open the **Actions** tab and download the `VidMax-debug-32-64` artifact after a successful run.

## Current scope

This first milestone supports direct media-file URLs only. A page link from YouTube, TikTok, Instagram, Facebook, or X is not automatically a downloadable media URL. Platform-specific support must use permitted official mechanisms and respect each service's terms, authentication, and copyright restrictions.

## Project details

- Application ID: `com.vidmax.app`
- Minimum Android: 6.0 (API 23)
- Target / compile SDK: 35
- Java: 17
