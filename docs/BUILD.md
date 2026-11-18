# NovaOS selbst bauen

## Voraussetzungen

* Build-Rechner mit **Debian 13** (empfohlen) oder Ubuntu 24.04, x86-64
* Root-Rechte (live-build arbeitet mit chroot, mount und Loop-Geräten)
* ≥ 25 GB freier Speicher, ≥ 4 GB RAM, Internetzugang zu `deb.debian.org`
* Pakete:

```bash
sudo apt install live-build debootstrap debian-archive-keyring dpkg-dev rsync \
                 xorriso squashfs-tools librsvg2-bin python3
```

Unter Ubuntu ist `live-build` veraltet. Verwenden Sie dort die Debian-Pakete:

```bash
wget http://deb.debian.org/debian/pool/main/l/live-build/live-build_20250505+deb13u1_all.deb \
     http://deb.debian.org/debian/pool/main/d/debootstrap/debootstrap_1.0.141_all.deb \
     http://deb.debian.org/debian/pool/main/d/debian-archive-keyring/debian-archive-keyring_2025.1_all.deb
sudo apt install distro-info && sudo dpkg -i ./*.deb
```

## Build

```bash
git clone https://github.com/superali99-tech/Hanse-1-Betriebssystem-.git novaos
cd novaos
./tools/check-host.sh          # prüft die Voraussetzungen
sudo make iso                  # = sudo ./tools/build-iso.sh
```

Ergebnis: `build/out/novaos-<version>-amd64.iso` plus `.sha256` und Paketliste.

| Variable | Bedeutung | Standard |
|---|---|---|
| `NOVAOS_SQUASHFS_COMPRESSION` | `xz` (kleiner) oder `zstd` (schneller) | `xz` |
| `NOVAOS_MIRROR` | Debian-Spiegel | `http://deb.debian.org/debian` |
| `NOVAOS_WORKDIR` / `NOVAOS_OUTDIR` | Arbeits-/Ausgabeverzeichnis | `build/live`, `build/out` |

Optionen: `--clean` (Neuaufbau, Paketcache bleibt), `--purge` (alles löschen),
`--skip-debs` (NovaOS-Pakete nicht neu bauen).

Der erste Build lädt ≈ 2 GB Pakete und dauert je nach Rechner/Leitung 30–60 Minuten;
Folge-Builds nutzen den Paketcache (gemessen: ≈ 11–15 Minuten mit zstd, 4 CPU-Kerne).

## Einzelne Schritte

```bash
make debs      # nur die novaos-*.deb bauen (build/debs/)
make artwork   # Icons, Wallpaper, Splash, Installer-Bilder neu erzeugen
make test      # Unit-Tests + statische Prüfungen (pytest)
make ui-test   # UI-Smoke-Test aller Nova-Apps (offscreen, Screenshots)
make qemu      # ISO in QEMU starten und Screenshots aufnehmen
```

## Projektstruktur

```
live-build/            live-build-Konfiguration (auto/, config/)
packages/novaos-*/     Quellen der NovaOS-Debian-Pakete (DEBIAN/, root/, build.sh)
src/novaos/            Python-Quellcode der Nova-Apps
src/data/              Desktop-Dateien, polkit, systemd-Units, Hilfe, APT-Helfer
artwork/               Generator für Logo, Icons, Wallpaper, Splash, Folien
tools/                 Build-Skripte
tests/unit/            pytest-Tests
tests/ui/              UI-Smoke-Tests/Screenshots
tests/qemu/            QEMU-Testtreiber (Boot, Screenshots, serielle Konsole)
docs/                  Dokumentation, Testbericht
```

## Versionierung

Die Version steht ausschließlich in `VERSION.sh` und wird von allen Skripten
(os-release, Paketversionen, Calamares-Branding, ISO-Name) übernommen.
