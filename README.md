# MyAppHub+
Persönlicher Android-App-Store ausschließlich für die sieben eigenen GitHub-Repositories von NoMercy0805.

## Apps
CarService, Haushaltsbuch, Arbeitszeit, GalerieSync, Vertraege, Rauchfrei und IronLog.

## Funktionen
- GitHub-Releases abrufen und APKs herunterladen.
- Installationsstatus prüfen und Android-Paketinstallation öffnen.
- Für Repositories ohne Release auf erfolgreiche GitHub-Actions-Builds verweisen.
- Hell-/Dunkelmodus durch Android-Systemeinstellung.

## Einschränkungen
Eine zuverlässige Update-Erkennung benötigt von allen Apps inkrementierte Android-versionCodes und veröffentlichte APKs. Aktuelle versionCodes sind in einigen Apps unverändert; deshalb behauptet MyAppHub noch keine sichere Update-Verfügbarkeit. Android erfordert eine Benutzerbestätigung zur APK-Installation. Zum Aktualisieren muss die Signatur der APK zur bereits installierten App passen.

## Build
GitHub Actions > Build MyAppHub APK > Artifacts > MyAppHub-debug-apk.
