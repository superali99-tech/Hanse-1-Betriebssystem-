"""Static checks of packaging, installer configuration and scripts."""
import glob
import os
import subprocess

import pytest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None


def shell_scripts():
    files = glob.glob(f"{ROOT}/tools/*.sh") + glob.glob(f"{ROOT}/live-build/auto/*")
    files += glob.glob(f"{ROOT}/live-build/config/hooks/live/*.hook.chroot")
    files += glob.glob(f"{ROOT}/packages/*/DEBIAN/p*") + glob.glob(f"{ROOT}/packages/*/build.sh")
    files += glob.glob(f"{ROOT}/packages/novaos-installer/root/usr/lib/novaos/calamares/novaos-*")
    files += [f"{ROOT}/packages/novaos-installer/root/usr/bin/novaos-install",
              f"{ROOT}/packages/novaos-installer/root/usr/lib/live/config/2000-novaos"]
    return sorted(files)


@pytest.mark.parametrize("path", shell_scripts(), ids=lambda p: os.path.relpath(p, ROOT))
def test_shell_syntax(path):
    first = open(path, encoding="utf-8").readline()
    shell = "bash" if "bash" in first else "sh"
    assert subprocess.run([shell, "-n", path]).returncode == 0


@pytest.mark.parametrize("control", glob.glob(f"{ROOT}/packages/*/DEBIAN/control"),
                         ids=lambda p: p.split("/")[-3])
def test_control_fields(control):
    text = open(control, encoding="utf-8").read()
    for field in ("Package:", "Version: @VERSION@", "Architecture:", "Maintainer:", "Description:", "Depends:"):
        assert field in text, field


class UniqueKeyLoader(yaml.SafeLoader if yaml else object):
    pass


def _construct_mapping(loader, node, deep=False):
    keys = set()
    for key_node, _ in node.value:
        key = loader.construct_object(key_node, deep=deep)
        assert key not in keys, f"duplicate key {key!r} line {key_node.start_mark.line + 1}"
        keys.add(key)
    return yaml.SafeLoader.construct_mapping(loader, node, deep)


if yaml:
    UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping)


@pytest.mark.skipif(yaml is None, reason="PyYAML not installed")
@pytest.mark.parametrize("path", glob.glob(f"{ROOT}/packages/novaos-installer/root/etc/calamares/**/*.conf", recursive=True)
                         + glob.glob(f"{ROOT}/packages/novaos-installer/root/etc/calamares/**/*.desc", recursive=True),
                         ids=lambda p: os.path.basename(p))
def test_calamares_yaml(path):
    with open(path, encoding="utf-8") as fh:
        data = yaml.load(fh, Loader=UniqueKeyLoader)
    assert isinstance(data, dict)


def test_calamares_sequence_modules_have_config():
    with open(f"{ROOT}/packages/novaos-installer/root/etc/calamares/settings.conf", encoding="utf-8") as fh:
        settings = yaml.safe_load(fh)
    for inst in settings["instances"]:
        assert os.path.exists(f"{ROOT}/packages/novaos-installer/root/etc/calamares/modules/{inst['config']}")
    exec_steps = [s for step in settings["sequence"] for k, v in step.items() if k == "exec" for s in v]
    assert exec_steps.index("partition") < exec_steps.index("unpackfs") < exec_steps.index("bootloader")
    assert settings["prompt-install"] is True  # explicit confirmation before writing to disk


def test_desktop_files_valid():
    tool = "/usr/bin/desktop-file-validate"
    if not os.path.exists(tool):
        pytest.skip("desktop-file-validate not installed")
    files = glob.glob(f"{ROOT}/src/data/applications/*.desktop") + glob.glob(f"{ROOT}/src/data/autostart/*.desktop")
    files += glob.glob(f"{ROOT}/packages/novaos-installer/root/usr/share/applications/*.desktop")
    res = subprocess.run([tool, *files], capture_output=True, text=True)
    assert res.returncode == 0, res.stdout + res.stderr


def test_package_lists_have_no_duplicates():
    seen = {}
    for path in glob.glob(f"{ROOT}/live-build/config/package-lists/*.list.chroot"):
        for line in open(path, encoding="utf-8"):
            pkg = line.split("#")[0].strip()
            if pkg:
                assert pkg not in seen, f"{pkg} in {path} and {seen.get(pkg)}"
                seen[pkg] = path
