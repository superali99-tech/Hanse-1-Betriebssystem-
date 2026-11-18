import os

from novaos.notes.main import NoteStore, safe_filename


def test_create_save_rename(tmp_path):
    store = NoteStore(str(tmp_path))
    path = store.create()
    assert os.path.exists(path)
    new = store.save(path, "# Einkaufsliste\n\n- Milch\n")
    assert os.path.basename(new) == "Einkaufsliste.md"
    assert not os.path.exists(path)
    assert store.read(new).startswith("# Einkaufsliste")
    # second note with the same title gets a unique file name
    p2 = store.save(store.create(), "# Einkaufsliste\nanders")
    assert os.path.basename(p2) == "Einkaufsliste (2).md"
    assert len(store.list()) == 2


def test_title_and_filename():
    assert NoteStore.title_of("\n\n## Hallo Welt\ntext", "x") == "Hallo Welt"
    assert NoteStore.title_of("", "fallback") == "fallback"
    assert safe_filename('a/b:c*?"<>|') == "a b c"
