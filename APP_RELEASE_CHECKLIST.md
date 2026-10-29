# App-Release-Checkliste (verbindlicher gemeinsamer Ablauf)

Diese Datei ist die zentrale Arbeitsanweisung für Änderungen an den Android-Apps von NoMercy0805. Bei jeder App-Änderung den Ablauf prüfen und diese Datei aktualisieren, wenn sich Abläufe, Repository-Namen, Paketnamen oder Release-Formate ändern.

## Apps
- CarService
- Haushaltsbuch
- Arbeitszeit
- GalerieSync
- Vertraege
- Rauchfrei
- IronLog
- MyAppHub (App-Store; auch selbst aktualisieren)

## Verbindlicher Ablauf bei jeder App-Änderung
1. Aktuellen Stand des jeweiligen GitHub-Repos auf `main` lesen und gewünschte Änderung umsetzen.
2. Versionierung kontrollieren: `versionCode` des tatsächlich gebauten APK muss strikt größer als beim installierten vorherigen Release sein. `versionName` für Nutzer nachvollziehbar weiterführen. Falls Workflow versionCode beim Build ersetzt, **diesen** Wert maßgeblich nehmen.
3. Prüfen, dass ein Commit automatisch den passenden GitHub-Actions-Workflow auslöst; falls ein Trigger erforderlich ist, diesen ausdrücklich auslösen. Nicht nur Code committen und dann behaupten, ein APK sei bereit.
4. Workflow erfolgreich abschließen lassen: Build und Tests, falls konfiguriert, außerdem konsistente Signatur/Keystore prüfen, damit Android ein Update über die bestehende Installation zulässt.
5. Ein GitHub-Release mit einer herunterladbaren `.apk` erstellen und in der Release-Beschreibung exakt `Android-versionCode: <tatsächlicher APK-versionCode>` hinterlegen. Das ist entscheidend für MyAppHub.
6. Prüfen, ob das Release und die APK tatsächlich existieren, und ob MyAppHub das Repository in seinen festgelegten Apps führt. Installation nur als Update anbieten, wenn Paketname und Signatur identisch sind und versionCode höher ist.
7. Dem Nutzer den **verifizierten** Download oder das Release verlinken. Wenn Build, Release oder MyAppHub-Anzeige nicht überprüft wurden, diesen Status ausdrücklich nennen.
8. Nach Besonderheiten oder Fehlern dieses Dokument und gegebenenfalls die Repo-eigene Release-Dokumentation nachpflegen.

## GalerieSync
- Repository: `NoMercy0805/GalerieSync`
- Android-Paket: `com.schiwy.galeriesync`
- Gradle: `app/build.gradle.kts`
- Workflow: `.github/workflows/build-apk.yml`
- Build: stabile Debug-Signatur aus `GALERIESYNC_DEBUG_KEYSTORE`, mit Fingerprint-Prüfung.
- Workflow startet bei Pushes auf `main`/ `master`, sofern `app/**`, die Build-Datei oder die Auto-Sync-Patchdatei betroffen sind; auch manuell möglich.
- Build überschreibt `versionCode` mit `100000 + github.run_number` und veröffentlicht Release-Tag `galeriesync-build-<run_number>`, APK `GalerieSync-Build-<run_number>.apk`.
- Die Release-Notiz muss den berechneten Wert `Android-versionCode: 100000+run_number` enthalten. Nicht mit der Version aus dem unveränderten Gradle-Quelltext verwechseln.
- **Stand 2026-10-29:** Automatischen Trigger und konsistenten Code implementiert. Erfolgreichen Build, Release und Update-Erkennung noch gesondert prüfen.

## MyAppHub
- Repository: `NoMercy0805/MyAppHub`
- MyAppHub ermittelt den Update-Status aus GitHub-Releases mit APK-Asset und der Release-Body-Zeile `Android-versionCode: N`.
- Es vergleicht diesen mit Androids installiertem `versionCode` und zeigt nur bei höherem Code ein Update. Alle Apps müssen daher ein *passendes Release* liefern, nicht nur ein aktualisiertes Repository.

## Sicherheits- und Qualitätsregeln
- Nie eine neue Signatur für bestehende App-Updates verwenden.
- Kein Update vortäuschen, wenn Release, Signatur oder versionCode unbekannt sind.
- Bestehende Nutzerdaten und Einstellungen beim Update erhalten.
- Fehlerhafte oder noch laufende CI-Builds nicht als fertige APK bezeichnen.
