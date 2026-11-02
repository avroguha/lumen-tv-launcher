# Development notes

Things that were found the hard way while building Lumen on a TCL Smart TV Pro (Android TV 12,
API 31, Amlogic T5M, 2 GB RAM). Most of them apply to any Google TV device.

## The Home button (why an accessibility service)

On this TCL build a sideloaded launcher **cannot be resolved for the Home key**, whatever the
preference or the HOME role says: the Google TV launcher's Home filter has priority 2, the
setup app's recovery home has priority 1, and a non-system app is capped at 0. Disabling the
Google TV launcher hands Home to that recovery screen (it draws nothing and respawns), and the
shell may not touch the setup app's components; disabling the setup app itself is the known
way to crash-loop the TV or lose its Wi-Fi. All of this was tried and rolled back; Google TV
is enabled and is still the resolved Home.

What works instead is `HomeRedirectService`, an accessibility service that watches for the
Google TV home window coming to the front and brings Lumen forward at once, so the Home
button lands on Lumen while Google TV stays installed and enabled. It must be switched on
once: Settings → Accessibility → **Lumen Home button** (the "Home button → Lumen" pill in
Lumen's Settings opens that page), or over ADB:

```
adb shell settings put secure enabled_accessibility_services org.idktech.lumen/org.idktech.lumen.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
```

Lumen also holds the HOME role (`cmd role add-role-holder android.app.role.HOME org.idktech.lumen`),
which is harmless and makes it the default home the moment the stock launcher is ever gone.

**It keeps itself on.** Android switched the service off after several installs and
reboots. Lumen holds `WRITE_SECURE_SETTINGS` (granted once over adb) and turns
its own service back on whenever it starts (`AppCache.ensureHomeService`); verified by
switching it off and starting Lumen. The service also requests key filtering (`flagRequestFilterKeyEvents`, set again in
`onServiceConnected`) for the trailer's Back. **Keys injected with `adb shell input` never
reach an accessibility key filter**: test that path with the real remote.

Measured: Lumen is in front about half a second after
Home from Settings, about a second and a half from Netflix (Google TV flashes first). A
`pm clear` of Lumen drops the binding; re-run the two `settings put` lines afterwards.

Move mode (hold OK on an app tile) handles its keys at the screen root rather than on the
tile, because a lazy-row item that has just been re-ordered loses focus; one press is one
step, key repeats are ignored, OK or Back drops. The saved order is read before the app list
is published, or the first tile of the unsorted list would take the initial focus.


## Platform traps

- Android 11+ hides other apps unless the manifest declares `<queries>`.
- The TV's Live TV app handles the HDMI pass-through URI only with the channel MIME type.
- JustWatch `deeplinkURL(platform: ANDROID_TV)` gives `intent://` URIs that open Netflix,
  Prime Video, JioHotstar and SonyLIV at the title. Its introspection is disabled; the filter
  has no language field, but `originalTitle` + `productionCountries` + `audioLanguages` do.
- Haze 1.5 disables blur on API 31 unless `blurEnabled = true`.
- `FocusRequester.saveFocusedChild/restoreFocusedChild` did not restore on this TV; every
  overlay opener passes its own FocusRequester and the close re-focuses it two frames later.
- A focused Compose text field pops the TV keyboard the moment the D-pad lands on it; the
  field is a plain pill until OK is pressed.
- `screencap` is empty while a protected surface (HDMI, Netflix, Prime) is on screen, and
  while the TV is asleep — it dozes off on its own at night.
- The screen is 960×540 dp (1920×1080 px at density 2): size overlays in dp.
- A Row that overflows the bar does not wrap or warn: its last children are simply clipped.
  The second clock showed as "11:" / "Ne" until the bar was re-budgeted; in 12-hour format
  it needed another 50 dp (margins 16, tabs 13 sp / 12 dp, Subscriptions moved to Settings).
- A rotated `Text` (`graphicsLayer { rotationZ = -90f }`) is still measured at its parent's
  width, so inside a 24 dp handle it wrapped to one glyph. `Modifier.requiredWidth()` on the
  text lets it lay out at its own width before the rotation.
- Google TV's home activity answers HOME, not LEANBACK_LAUNCHER: `getLeanbackLaunchIntentForPackage`
  returns null and the `<queries>` block needs a HOME entry. It is started by component; the
  Home-button service is told to let that one appearance through.
- `android.settings.DREAM_SETTINGS` resolves to nothing on this TV. SystemUI's `Somnambulator`
  (MAIN + DESK_DOCK, exported) starts the current screensaver at once.
- `ACTION_ASSIST` resolves to Google Assistant (`com.google.android.katniss`): that is the
  "AI conversation" on this TV, and it works from a launcher.
- Compose's 2-D focus search happily leaves a row sideways for a pill in the bar that is
  above and to the right. `focusProperties { exit = … Cancel }` on the row (an experimental
  API, opt-in) keeps it put. **Cancel only the four D-pad directions:** a programmatic
  `requestFocus()` is an exit in direction `Enter`, and cancelling that too trapped focus in
  the rail for good (OK and RIGHT could no longer jump into a row).
- `enabled_accessibility_services` was found empty again on 2026-11-02 morning after being
  verified the night before (nothing in Lumen clears it; a reboot or the TV's settings did).
  Settings → System now shows the Home-button state, and the two `settings put` lines bring
  it back.
- Reading other apps' media sessions (`MediaSessionManager.getActiveSessions`) needs a
  NotificationListenerService that the user enables; an empty one is enough.
- Overlay windows (`TYPE_APPLICATION_OVERLAY`) draw over Google TV and over the apps once
  `SYSTEM_ALERT_WINDOW` is allowed; the TV's settings app does have
  `android.settings.action.MANAGE_OVERLAY_PERMISSION`, so the user can grant it on screen.
