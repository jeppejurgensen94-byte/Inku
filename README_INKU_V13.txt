INKU v13 — LOCAL LIBRARY, PLAYER, DOWNLOADS AND CLEAN BROWSE

MAIN FIXES
- Local Anime and Local Manga are Browse sources, not automatic library entries.
- Anime/Manga libraries only contain titles explicitly added by the user.
- Clean Anime/Manga toggle under Browse search.
- No preloaded fake anime, manga or remote extensions.
- Built-in Media3 video player for local episodes and movies.
- Play/Continue opens the actual local video, including completed titles.
- Subtitle selection moved into the video player.
- Episode rows show thumbnail, title/number and watched state only.
- Local episode thumbnails are extracted from a deterministic point inside the video.
- Download queue shows title, episode/selection, state and progress.
- Local episode downloads copy real files into the selected Inku folder.
- Light theme option updates app and system-bar appearance.
- Existing custom categories, profile edits, covers, backgrounds, seasons,
  settings, backups, WebView and Inku Extension Builder are preserved.

EXTENSIONS
- The Extension Builder creates Inku JSON source manifests from a public or
  user-owned website URL and leaves detected selectors editable.
- Installed Inku sources appear in Browse and can be opened independently.
- Inku does not bypass DRM, logins, paywalls, captcha or anti-bot protections.
- Aniyomi APK extensions and Yukimi/Tenka packages use different runtimes and
  cannot be loaded directly as Inku extensions. See EXTENSION_COMPATIBILITY_V13.txt.

KNOWN LIMITS
- Local Manga scanning/catalog metadata is present, but a complete page reader
  for every manga archive/folder layout is not guaranteed in this build.
- A remote website extension can only download media when the permitted source
  exposes a usable direct file URL through the detected/edited adapter.
