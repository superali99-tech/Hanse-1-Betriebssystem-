# Secure Boot und NovaOS

## Wie es funktioniert

NovaOS baut keinen eigenen Kernel und keinen eigenen Bootloader, sondern verwendet die
von Debian signierten Komponenten:

```
UEFI-Firmware (Microsoft-UEFI-CA)
   └─► shimx64.efi       (shim-signed, von Microsoft signiert)
         └─► grubx64.efi (grub-efi-amd64-signed, von Debian signiert)
               └─► vmlinuz (linux-image-amd64, von Debian signiert)
```

* **Live-Medium:** live-build wird mit `--uefi-secure-boot enable` aufgerufen und legt
  die signierte Kette unter `EFI/boot/` des ISO ab.
* **Installiertes System:** `novaos-bootloader-config` installiert auf UEFI-Rechnern
  `grub-efi-amd64-signed` und `shim-signed` und setzt
  `grub2/force_efi_extra_removable=true`, damit shim auch unter
  `EFI/BOOT/BOOTX64.EFI` liegt (Fallback für Firmware, die NVRAM-Einträge verliert).
  Calamares' eigene Fallback-Kopie ist deaktiviert (`installEFIFallback: false`), damit
  dort nicht ein unsignierter GRUB landet.

## Einschränkungen (ehrlich)

1. **Eigene/zusätzliche Kernelmodule** (z. B. NVIDIA-Treiber über DKMS, VirtualBox-Module)
   sind nicht von Debian signiert. Sie müssen mit einem eigenen *Machine Owner Key* (MOK)
   signiert werden (`mokutil --import`), sonst lädt der Kernel sie bei aktivem Secure Boot nicht.
2. **Firmware nur mit Microsoft-Windows-CA:** Einige neuere PCs (u. a. „Secured-core“-Geräte)
   vertrauen standardmäßig nur der *Windows Production CA*, nicht der *Microsoft UEFI CA 2011/2023*,
   mit der shim signiert ist. Dort muss im Firmware-Setup „Microsoft-UEFI-CA von Drittanbietern
   zulassen“ aktiviert oder Secure Boot deaktiviert werden.
3. **SBAT-Revocations:** Wenn Microsoft/Debian alte shim- oder GRUB-Versionen per SBAT
   widerrufen, startet ein **altes** NovaOS-ISO auf Rechnern mit aktualisierter
   Revocation-Liste nicht mehr mit Secure Boot. Abhilfe: aktuelles ISO bauen (es enthält
   automatisch die aktuellen signierten Pakete aus Debian).
4. **NovaOS-eigene Pakete** enthalten keinen Boot-Code und müssen daher nicht signiert werden.

## Testergebnis

Siehe [TESTBERICHT.md](TESTBERICHT.md): Dort ist dokumentiert, ob der Boot mit
OVMF + Secure Boot (Microsoft-Schlüssel) in QEMU tatsächlich getestet wurde.
