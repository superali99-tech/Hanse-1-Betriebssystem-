# NovaOS – convenience targets. The ISO build needs root (sudo make iso).
PYTHON ?= python3
SCRATCH ?= build/test-output

.PHONY: help iso iso-fast debs artwork test ui-test qemu qemu-uefi

help:
	@echo "make iso        - build the NovaOS ISO (needs root, ~30-60 min first time)"
	@echo "make iso-fast   - ISO with zstd compression (faster build, bigger ISO)"
	@echo "make debs       - build the novaos-*.deb packages only"
	@echo "make artwork    - regenerate logo, icons, wallpaper, splash, slides"
	@echo "make test       - unit tests + static checks (pytest)"
	@echo "make ui-test    - start all Nova apps offscreen and take screenshots"
	@echo "make qemu       - boot build/out/*.iso in QEMU (BIOS) with screenshots"
	@echo "make qemu-uefi  - same with UEFI firmware"
	@echo "To start from scratch: sudo ./tools/build-iso.sh --purge"

iso:
	./tools/build-iso.sh

iso-fast:
	NOVAOS_SQUASHFS_COMPRESSION=zstd ./tools/build-iso.sh

debs:
	./tools/build-debs.sh

artwork:
	$(PYTHON) artwork/generate.py

test:
	$(PYTHON) -m pytest -q tests/unit

ui-test:
	QT_QPA_PLATFORM=offscreen $(PYTHON) tests/ui/smoke_apps.py $(SCRATCH)/ui

qemu:
	$(PYTHON) tests/qemu/novaqemu.py screens $$(ls -t build/out/*.iso | head -n1) --out $(SCRATCH)/qemu-bios --screens 10,60,150,300,420

qemu-uefi:
	$(PYTHON) tests/qemu/novaqemu.py screens $$(ls -t build/out/*.iso | head -n1) --uefi --out $(SCRATCH)/qemu-uefi --screens 10,60,150,300,420
