import os

from novaos.disk import analysis, recovery


def test_categories_and_sizes():
    assert analysis.category("Urlaub.JPG") == "images" and analysis.category("film.mkv") == "videos"
    assert analysis.category("README") == "other"
    assert analysis.human_size(999) == "999 B" and analysis.human_size(1_500_000) == "1,5 MB"
    assert analysis.human_size(250_000_000_000, german=False) == "250 GB"


def test_scan_folder(tmp_path):
    (tmp_path / "Bilder").mkdir()
    (tmp_path / "Bilder" / "a.jpg").write_bytes(b"x" * 5000)
    (tmp_path / "Videos").mkdir()
    (tmp_path / "Videos" / "b.mp4").write_bytes(b"x" * 20000)
    (tmp_path / "notiz.txt").write_bytes(b"x" * 100)
    os.symlink("/usr", tmp_path / "link")     # symlinks are not followed
    r = analysis.scan(str(tmp_path))
    assert r.total == 25100 and r.files == 3
    assert r.top_children()[0] == ("Videos", 20000)
    assert r.by_category["videos"] == 20000 and r.by_category["images"] == 5000
    assert r.largest[0][1].endswith("b.mp4")


def test_lsblk_parsing():
    js = '''{"blockdevices":[
      {"name":"nvme0n1","path":"/dev/nvme0n1","size":512110190592,"type":"disk","mountpoints":[null],"model":"Samsung SSD 980 ","rm":false,"tran":"nvme",
       "children":[{"name":"nvme0n1p1","path":"/dev/nvme0n1p1","size":536870912,"type":"part","mountpoints":["/boot/efi"],"fstype":"vfat"},
                   {"name":"nvme0n1p2","path":"/dev/nvme0n1p2","size":511000000000,"type":"part","mountpoints":["/"],"fstype":"ext4"}]},
      {"name":"sdb","path":"/dev/sdb","size":32000000000,"type":"disk","mountpoints":[null],"model":"SanDisk","rm":true,"tran":"usb",
       "children":[{"name":"sdb1","path":"/dev/sdb1","size":32000000000,"type":"part","mountpoints":["/media/max/STICK"],"fstype":"vfat","label":"STICK"}]},
      {"name":"loop0","path":"/dev/loop0","size":1,"type":"loop"},
      {"name":"zram0","path":"/dev/zram0","size":1,"type":"disk"}]}'''
    ds = analysis.parse_lsblk(js)
    assert [d.name for d in ds] == ["nvme0n1", "sdb"]
    assert ds[0].kind == "nvme" and ds[0].model == "Samsung SSD 980" and ds[0].partitions[1]["mounts"] == ["/"]
    assert ds[1].kind == "usb" and ds[1].partitions[0]["label"] == "STICK"


def test_trash_list_and_restore(tmp_path):
    trash = tmp_path / "Trash"
    (trash / "files").mkdir(parents=True)
    (trash / "info").mkdir()
    home = tmp_path / "home"
    home.mkdir()
    (trash / "files" / "Brief.odt").write_bytes(b"x" * 10)
    (trash / "info" / "Brief.odt.trashinfo").write_text(
        f"[Trash Info]\nPath={home}/Dokumente/Brief%20Neu.odt\nDeletionDate=2026-11-27T10:00:00\n")
    items = recovery.trash_items(str(trash))
    assert len(items) == 1 and items[0].original.endswith("Dokumente/Brief Neu.odt") and items[0].size == 10
    target = recovery.restore(items[0], str(trash))
    assert os.path.exists(target) and target.endswith("Brief Neu.odt") and recovery.trash_items(str(trash)) == []


def test_free_name(tmp_path):
    p = tmp_path / "a.jpg"
    assert recovery.free_name(str(p)) == str(p)
    p.write_bytes(b"1")
    assert recovery.free_name(str(p)).endswith("a (2).jpg")


def test_photorec_command_and_device_check(tmp_path):
    assert recovery.valid_device("/dev/sdb") and recovery.valid_device("/dev/mmcblk0p1") and recovery.valid_device("/dev/nvme0n1p2")
    assert not recovery.valid_device("/dev/sdb;rm -rf /") and not recovery.valid_device("/etc/passwd")
    cmd = recovery.photorec_command("/dev/sdb1", "/media/x/Rettung", ["photos"])
    assert cmd[:5] == ["photorec", "/log", "/d", "/media/x/Rettung/recup_dir", "/cmd"] and cmd[5] == "/dev/sdb1"
    assert "jpg,enable" in cmd[6] and "mp4,enable" not in cmd[6] and cmd[6].endswith("wholespace,search")
    assert recovery.same_device(str(tmp_path), [str(tmp_path)])
    assert not recovery.same_device(str(tmp_path), [])


def test_to_trash_and_back(tmp_path):
    f = tmp_path / "home" / "gross.iso"
    f.parent.mkdir()
    f.write_bytes(b"x" * 1000)
    trash = str(tmp_path / "Trash")
    recovery.to_trash(str(f), trash, now="2026-11-27T12:00:00")
    assert not f.exists()
    items = recovery.trash_items(trash)
    assert items[0].original == str(f) and items[0].size == 1000
    recovery.restore(items[0], trash)
    assert f.exists()


def test_edition(tmp_path, monkeypatch):
    from novaos.common import sysinfo

    f = tmp_path / "edition"
    monkeypatch.setattr(sysinfo, "EDITION_FILE", str(f))
    assert sysinfo.edition() == 1           # NovaOS 1: no file
    f.write_text("2\n")
    assert sysinfo.edition() == 2
    f.write_text("nonsense")
    assert sysinfo.edition() == 1


def test_novaos2_package():
    root = os.path.join(os.path.dirname(__file__), "..", "..", "packages", "novaos-2")
    with open(os.path.join(root, "root", "usr", "share", "novaos", "edition")) as fh:
        assert fh.read().strip() == "2"
    with open(os.path.join(root, "DEBIAN", "control")) as fh:
        control = fh.read()
    assert "novaos-disk" in control and "Nova-News:" in control
    assert os.path.exists(os.path.join(root, "root", "usr", "share", "wallpapers", "NovaNebula", "metadata.json"))
