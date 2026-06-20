<div align="center">
<img src="assets/branding/inku-icon.png" width="160" alt="Inku app icon">
Inku
Your stories, your moment.
A local-first Android media hub for discovering, organizing, and enjoying anime, manga, TV shows, movies, audiobooks, and more.
<br>
![Status](https://img.shields.io/badge/status-in%20development-1ABC9C?style=for-the-badge)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
</div>
---
About Inku
Inku is being built as one clean place for different kinds of entertainment.
The goal is to combine personal libraries, local media, source browsing, extensions, progress tracking, downloads, metadata, and playback or reading tools without turning the app into a cluttered mess.
Inku is designed around three ideas:
Simple: clear navigation and an interface that stays easy to understand.
Flexible: support for different media types, sources, folders, and personal preferences.
Local-first: your library, settings, and personal organization should remain under your control.
> **Development status:** Inku is currently an early preview project. Some screens and controls are still being connected to their final systems.
---
Planned media support
Media type	Goal
Anime	Libraries, seasons, episodes, progress, sources, extensions, downloads, and playback
Manga	Libraries, chapters, metadata, sources, extensions, downloads, and reading
TV shows	Seasons, episodes, local files, progress, metadata, and playback
Movies	Local files, collections, metadata, downloads, and playback
Audiobooks	Books, chapters, progress, local storage, and audio playback
More	A flexible structure for additional media types later
---
Core project goals
Unified media libraries with clear categories and progress
Anime and manga source and extension support
Search and browsing by source
Covers, backgrounds, descriptions, genres, ratings, and status
Local folders and automatic folder creation
Downloads and offline organization
Reader, player, subtitle, gesture, and quality settings
Backup and restore tools
Extension repositories with install, update, refresh, and removal controls
A custom extension builder with source-specific settings
Privacy, security, storage, and diagnostic controls
A clean dark interface built around the Inku color palette
---
Screenshots
Real screenshots will be added as the app becomes stable enough to represent the final experience.
Place screenshots inside:
```text
assets/screenshots/
```
Recommended filenames:
```text
anime-library.png
manga-library.png
browse-sources.png
extension-repositories.png
media-details.png
settings.png
```
After the screenshots have been added, remove the `<!--` and `-->` lines around the gallery below.
<!--

<table>
  <tr>
    <td align="center">
      <img src="assets/screenshots/anime-library.png" width="230" alt="Anime library"><br>
      <strong>Anime Library</strong>
    </td>
    <td align="center">
      <img src="assets/screenshots/manga-library.png" width="230" alt="Manga library"><br>
      <strong>Manga Library</strong>
    </td>
    <td align="center">
      <img src="assets/screenshots/browse-sources.png" width="230" alt="Browse sources"><br>
      <strong>Browse Sources</strong>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="assets/screenshots/extension-repositories.png" width="230" alt="Extension repositories"><br>
      <strong>Extension Repositories</strong>
    </td>
    <td align="center">
      <img src="assets/screenshots/media-details.png" width="230" alt="Media details"><br>
      <strong>Media Details</strong>
    </td>
    <td align="center">
      <img src="assets/screenshots/settings.png" width="230" alt="Settings"><br>
      <strong>Settings</strong>
    </td>
  </tr>
</table>

---
Development roadmap
Foundation
[x] Inku identity, colors, and app icon
[x] Android and Jetpack Compose foundation
[x] Core navigation and preview interface
[x] Initial Anime, Manga, Updates, Browse, and Settings areas
Libraries and media
[ ] Production-ready anime library
[ ] Production-ready manga library
[ ] TV-show and movie libraries
[ ] Audiobook library and audio player
[ ] Reliable metadata, covers, backgrounds, and progress storage
[ ] Local-folder scanning and automatic folder setup
Sources and extensions
[ ] Full extension repository support
[ ] Install, update, uninstall, enable, disable, test, and refresh controls
[ ] Source-specific catalog browsing
[ ] Improved compatibility with supported extension APIs
[ ] Custom extension builder
[ ] Migration between compatible sources
Reading, playback, and downloads
[ ] Manga reader
[ ] Video player
[ ] Audiobook player
[ ] Subtitle support and styling
[ ] Download queue and offline media
[ ] Quality, language, gesture, and playback controls
Release preparation
[ ] Stable data storage
[ ] Backup and restore
[ ] Error reporting and diagnostics
[ ] Accessibility review
[ ] Performance testing
[ ] Signed Android release
[ ] First public APK
---
Technology
Inku is currently being developed with:
Kotlin
Android
Jetpack Compose
Material 3
Gradle
The project structure and dependencies may change while the core systems are being completed.
---
Running the project
The repository is still under active development, so setup steps may change.
General Android development flow:
Clone the repository.
Open the project in Android Studio.
Allow Gradle to synchronize.
Connect an Android device or start an emulator.
Run the `app` configuration.
```bash
git clone https://github.com/jeppejurgensen94-byte/Inku.git
cd Inku
```
Public APK downloads will be published through GitHub Releases when a stable build is ready.
---
Contributing
Inku is currently being shaped and stabilized.
Useful contributions may later include:
Reproducible bug reports
UI and accessibility feedback
Documentation improvements
Safe extension compatibility work
Tests and performance improvements
Translation help
Please use GitHub Issues for confirmed bugs and feature proposals once the issue templates are available.
---
Legal and content notice
Inku is a media-management and playback project.
The project does not intend to bundle or host copyrighted anime, manga, films, TV shows, audiobooks, or other protected media. Third-party sources and extensions are separate from the core project. Users are responsible for following the laws and service rules that apply to them.
---
License
A project license has not been selected yet.
---
<div align="center">
<img src="assets/branding/inku-icon.png" width="90" alt="Inku icon">
Inku
One home for the stories you enjoy.
</div>
