import gzip

from novaos.store.appstream import Catalog, parse_component, preferred_languages

SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<components version="0.8" origin="flatpak">
  <component type="desktop-application">
    <id>org.example.Paint</id>
    <name xml:lang="ar">رسم</name>
    <name>Example Paint</name>
    <name xml:lang="de">Beispiel-Malprogramm</name>
    <summary>Draw pictures</summary>
    <summary xml:lang="de">Bilder malen</summary>
    <developer id="example.org"><name>Example Team</name></developer>
    <description><p>Paint <em>anything</em>.</p><ul><li>Layers</li><li>Brushes</li></ul></description>
    <description xml:lang="de"><p>Male <em>alles</em>.</p></description>
    <project_license>GPL-3.0-or-later</project_license>
    <categories><category>Graphics</category><category>2DGraphics</category></categories>
    <keywords><keyword>draw</keyword><keyword xml:lang="de">zeichnen</keyword></keywords>
    <url type="homepage">https://example.org/paint</url>
    <screenshots>
      <screenshot>
        <image type="thumbnail" width="224" height="126">https://img/224.png</image>
        <image type="thumbnail" width="624" height="351">https://img/624.png</image>
        <image type="thumbnail" width="1248" height="702">https://img/1248.png</image>
        <image type="source">https://img/source.png</image>
      </screenshot>
      <screenshot type="default">
        <caption>Main window</caption>
        <image type="thumbnail" width="624" height="351">https://img/default.png</image>
      </screenshot>
    </screenshots>
    <releases>
      <release version="1.0" timestamp="1000"/>
      <release version="2.1" timestamp="3000"><description><p>New brushes</p></description></release>
      <release version="2.0" timestamp="2000"/>
    </releases>
    <custom><value key="flathub::verification::verified">true</value></custom>
    <bundle type="flatpak" runtime="org.kde.Platform/x86_64/6.8">app/org.example.Paint/x86_64/stable</bundle>
  </component>
  <component type="runtime">
    <id>org.example.Runtime</id><name>Runtime</name>
    <bundle type="flatpak">runtime/org.example.Runtime/x86_64/1</bundle>
  </component>
  <component type="desktop-application">
    <id>org.example.Game</id><name>Space Game</name><summary>Shoot asteroids</summary>
    <categories><category>Game</category></categories>
    <bundle type="flatpak">app/org.example.Game/x86_64/stable</bundle>
  </component>
</components>
"""


def write(tmp_path, gz=False):
    path = tmp_path / ("appstream.xml.gz" if gz else "appstream.xml")
    data = SAMPLE.encode()
    path.write_bytes(gzip.compress(data) if gz else data)
    return str(path)


def test_untranslated_fallback_and_runtime_skipped(tmp_path):
    cat = Catalog()
    assert cat.load_file(write(tmp_path), langs=["en_US", "en"]) == 2
    app = cat.get("org.example.Paint")
    # regression: childless Elements are falsy, the untranslated name must win
    assert app.name == "Example Paint"
    assert app.summary == "Draw pictures"
    assert "<em>anything</em>" in app.description and "<li>Layers</li>" in app.description
    assert cat.get("org.example.Runtime") is None


def test_german(tmp_path):
    cat = Catalog()
    cat.load_file(write(tmp_path, gz=True), langs=["de_DE", "de"])
    app = cat.get("org.example.Paint")
    assert app.name == "Beispiel-Malprogramm"
    assert app.summary == "Bilder malen"
    assert app.description == "<p>Male <em>alles</em>.</p>"
    assert "zeichnen" in app.keywords


def test_metadata(tmp_path):
    cat = Catalog()
    cat.load_file(write(tmp_path), langs=["en"])
    app = cat.get("org.example.Paint")
    assert app.developer == "Example Team"
    assert app.version == "2.1" and app.release_timestamp == 3000
    assert "New brushes" in app.release_notes
    assert app.verified is True
    assert app.flatpak_name == "org.example.Paint" and app.branch == "stable"
    assert app.runtime.startswith("org.kde.Platform")
    assert app.homepage == "https://example.org/paint"
    # default screenshot first, thumbnail >= 600px wide, largest as full size
    assert app.screenshots[0].thumbnail == "https://img/default.png"
    assert app.screenshots[0].caption == "Main window"
    assert app.screenshots[1].thumbnail == "https://img/624.png"
    assert app.screenshots[1].full == "https://img/1248.png"


def test_search_and_categories(tmp_path):
    cat = Catalog()
    cat.load_file(write(tmp_path), langs=["en"])
    assert [a.id for a in cat.search("paint")] == ["org.example.Paint"]
    assert [a.id for a in cat.search("asteroids")] == ["org.example.Game"]
    assert cat.search("") == []
    assert [a.id for a in cat.search("spiele")] == ["org.example.Game"]  # German category word
    assert [a.id for a in cat.by_category("graphics")] == ["org.example.Paint"]
    assert [a.id for a in cat.by_category("games")] == ["org.example.Game"]


def test_preferred_languages():
    assert preferred_languages("de_DE.UTF-8") == ["de_DE", "de"]
    assert preferred_languages("C") == []
