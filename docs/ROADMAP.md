# Roadmap

Ehrliche Liste dessen, was als Nächstes kommt bzw. noch fehlt.

## Kurzfristig (0.2)

* **Debian-Pakete im Nova Store:** zweites Backend für die AppStream-Daten von Debian
  (DEP-11), Installation über den vorhandenen `nova-apt-helper`. Heute werden
  Debian-Pakete nur über Nova Updates (Aktualisierung) und das Terminal (`apt`) verwaltet.
* **Eigenes NovaOS-APT-Repository** (signiert), damit die `novaos-*`-Pakete per Update
  verteilt werden können
* Firmware-Updates (fwupd/LVFS) in Nova Updates anzeigen
* Snapshot vor jedem System-Update (Timeshift-Integration in Nova Updates)
* Gastkonto per Klick (temporärer Benutzer, beim Abmelden gelöscht)
* Weitere Übersetzungen (Englisch ist Quellsprache; Deutsch vollständig)

## Mittelfristig

* KI-Assistent (optional, lokal laufendes Modell), nur mit echten Funktionen:
  Fragen zum System beantworten, Einstellungen öffnen, Dateien finden
* Reproduzierbare Builds, signierte ISO-Prüfsummen
* Automatisierter Installationstest (Calamares-Durchlauf in QEMU per Skript)
* ARM64-Variante
