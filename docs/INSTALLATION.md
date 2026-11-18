# NovaOS testen und installieren

> **Wichtig:** Sichern Sie Ihre Daten, bevor Sie NovaOS auf einem echten PC installieren.
> Der Installer verändert Datenträger erst nach Ihrer ausdrücklichen Bestätigung,
> aber Partitionierung ist immer mit Risiko verbunden.

## Systemanforderungen

| | Minimum | Empfohlen |
|---|---|---|
| Prozessor | 64-Bit x86 (amd64), 2 Kerne | 4 Kerne |
| Arbeitsspeicher | 2 GB (Live: 4 GB) | 8 GB |
| Speicherplatz | 20 GB | 60 GB SSD |
| Firmware | BIOS oder UEFI | UEFI (Secure Boot möglich) |

## 1. ISO herunterladen und prüfen

Das fertige ISO liegt auf der Seite **Releases** des Repositorys (aktuell Release `build-2`,
Datei `novaos-0.1.0-amd64.iso` unter *Assets*, ca. 2,1 GB). Solange das Repository privat
ist, funktioniert der Download nur, wenn man auf github.com angemeldet ist und Zugriff
auf das Repository hat.

```bash
sha256sum -c novaos-0.1.0-amd64.iso.sha256
# erwartet für build-2: a468107495b5e1485647d913ce2d7a20345139ee0490de6e21cd6db99b58fb1a
```

Unter Windows: `certutil -hashfile novaos-0.1.0-amd64.iso SHA256`.

**USB-Stick (für einen echten PC):** mit [balenaEtcher](https://etcher.balena.io/) oder
Rufus (im *DD-Modus*) bzw. unter Linux mit
`sudo dd if=novaos-0.1.0-amd64.iso of=/dev/sdX bs=4M status=progress conv=fsync`
schreiben. **Achtung:** Dabei wird der gesamte Stick überschrieben – vorher genau prüfen,
dass `/dev/sdX` wirklich der USB-Stick ist (`lsblk`).

## 2. In einer virtuellen Maschine testen

**QEMU/KVM (Linux):**

```bash
qemu-img create -f qcow2 novaos.qcow2 40G
qemu-system-x86_64 -enable-kvm -m 4096 -smp 4 -cpu host -machine q35 \
  -drive if=pflash,format=raw,readonly=on,file=/usr/share/OVMF/OVMF_CODE_4M.fd \
  -drive if=pflash,format=raw,file=OVMF_VARS_copy.fd \
  -drive file=novaos-0.1.0-amd64.iso,media=cdrom,readonly=on,if=none,id=cd0 \
  -device ide-cd,drive=cd0,bootindex=1 \
  -drive file=novaos.qcow2,if=none,id=hd0 -device virtio-blk-pci,drive=hd0,bootindex=2 \
  -vga virtio -display gtk -device usb-tablet -nic user,model=virtio-net-pci
```

(`OVMF_VARS_copy.fd` ist eine Kopie von `/usr/share/OVMF/OVMF_VARS_4M.fd`. Für BIOS-Boot die
beiden `-drive if=pflash` Zeilen weglassen.) Wichtig ist, dass **auch die Festplatte** einen
`bootindex` hat: Gibt man ihn nur dem CD-Laufwerk, verwirft OVMF beim Neustart den
Booteintrag „NovaOS“, und ganz ohne `bootindex` startet OVMF seine UEFI-Shell vor der
Festplatte. virt-manager und GNOME Boxes setzen das automatisch richtig.

> **Hinweis aus den NovaOS-Tests:** Ohne KVM (reine Software-Emulation, `-accel tcg`)
> stürzten Plasma und KWin in QEMU ab, solange die emulierte CPU AVX/AVX2 meldete. Mit
> `-cpu max,avx=off,avx2=off,fma=off` lief alles stabil. Mit KVM (`-enable-kvm -cpu host`)
> tritt das nicht auf. Ohne KVM ist NovaOS in QEMU zudem sehr langsam.

**VirtualBox:** Neue VM „Debian (64-bit)“, 4 GB RAM, 40 GB Festplatte, *EFI aktivieren*,
Grafik *VMSVGA* mit 128 MB, ISO als optisches Laufwerk einlegen.

**GNOME Boxes / virt-manager:** ISO auswählen, Betriebssystem „Debian 13“.

## 3. Live-System

Im Bootmenü **„NovaOS starten (Live-System)“** wählen. Nach dem Start erscheint der
Desktop automatisch (Benutzer `nova`, Passwort `live`). Bei Grafikproblemen
*„sicherer Grafikmodus (nomodeset)“* wählen.

## 4. Installation

1. Auf dem Desktop **„NovaOS installieren“** doppelklicken (oder im Willkommensassistenten).
2. Sprache und Region wählen, Tastaturlayout prüfen.
3. **Partitionierung:**
   * *Festplatte löschen* – gesamtes Laufwerk für NovaOS (alle Daten darauf gehen verloren)
   * *Daneben installieren* – neben Windows/anderem System (Größe per Schieberegler)
   * *Manuelle Partitionierung* – für Fortgeschrittene
   * optional *System verschlüsseln* (LUKS)
4. Benutzername, Computername und Passwort festlegen.
5. **Zusammenfassung prüfen** → *Installieren* → Sicherheitsabfrage bestätigen.
6. Nach Abschluss neu starten und das Installationsmedium entfernen.

Auf UEFI-Rechnern wird ein Secure-Boot-fähiger Bootloader installiert (signiertes shim
und GRUB von Debian), auf BIOS-Rechnern GRUB im MBR. Die Pakete dafür stammen vom
ISO selbst – eine Internetverbindung ist während der Installation nicht nötig.

## 5. Nach der Installation

* Der **Willkommensassistent** erscheint bei der ersten Anmeldung.
* **Nova Updates** öffnen und aktuelle Updates installieren.
* Apps über den **Nova Store** installieren.
* Für Dual-Boot erkennt GRUB Windows automatisch (os-prober).

## Wenn der PC nach der Installation nicht von der Festplatte startet (UEFI)

Der Installer legt einen Booteintrag „NovaOS“ an und zusätzlich den Standard-Startpfad
`EFI\BOOT\BOOTX64.EFI`, über den die Firmware die Festplatte auch ohne Eintrag startet und
der den Eintrag dabei automatisch neu anlegt. Landet ein Rechner trotzdem im
Firmware-Menü oder in einer **UEFI-Shell** (`Shell>`):

* **Firmware-Menü / Boot-Menü** (meist F12, F11, F8 oder Esc beim Einschalten): die Festplatte
  bzw. „NovaOS“ auswählen.
* **UEFI-Shell:** `fs0:` eingeben (Enter), dann `\EFI\BOOT\BOOTX64.EFI` (Enter). Der Start
  legt den Eintrag „NovaOS“ an; danach startet das System automatisch.
* Installationsmedium (USB-Stick/ISO) entfernen bzw. in der VM das CD-Laufwerk leeren.

## Bekannte Einschränkungen

Siehe [TESTBERICHT.md](TESTBERICHT.md) und [SECURE-BOOT.md](SECURE-BOOT.md).
