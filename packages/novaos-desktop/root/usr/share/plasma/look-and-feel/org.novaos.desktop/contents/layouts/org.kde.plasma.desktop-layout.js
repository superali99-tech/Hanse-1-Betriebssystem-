// NovaOS default desktop layout (Plasma scripting API).
// Runs once when a user logs in for the first time.

// ---- Desktop: wallpaper + widgets (calendar, system monitor, weather) ----
var desktops = desktopsForActivity(currentActivity());
for (var i = 0; i < desktops.length; i++) {
    var d = desktops[i];
    d.wallpaperPlugin = "org.kde.image";
    d.currentConfigGroup = ["Wallpaper", "org.kde.image", "General"];
    d.writeConfig("Image", "file:///usr/share/wallpapers/NovaAurora/");
    d.writeConfig("FillMode", "2");

    if (i === 0) {
        // Two compact columns at the right edge. The whole block fits into
        // 1024x768, so Plasma does not have to move the widgets when the
        // resolution changes (e.g. in virtual machines during login).
        var geo = screenGeometry(d.screen);
        var g = gridUnit;
        var colA = 17 * g, colB = 11 * g, gap = g;
        var right = geo.width - 2 * g;
        var xA = right - colB - gap - colA;
        var xB = right - colB;
        var y = 2 * g;
        try {
            d.addWidget("org.kde.plasma.calendar", xA, y, colA, 16 * g);
            d.addWidget("org.kde.plasma.weather", xA, y + 17 * g, colA, 8 * g);
            d.addWidget("org.kde.plasma.systemmonitor.cpu", xB, y, colB, 12 * g);
            d.addWidget("org.kde.plasma.systemmonitor.memory", xB, y + 13 * g, colB, 12 * g);
        } catch (e) {
            print("NovaOS layout: could not add desktop widgets: " + e);
        }
    }
}

// ---- Floating bottom panel, centred start button + pinned apps ----
var panel = new Panel;
panel.location = "bottom";
panel.height = 2 * Math.floor(gridUnit * 2.6 / 2);
if ("floating" in panel) {
    panel.floating = true;
}
panel.hiding = "none";

panel.addWidget("org.kde.plasma.panelspacer");

var kickoff = panel.addWidget("org.kde.plasma.kickoff");
kickoff.currentConfigGroup = ["General"];
kickoff.writeConfig("icon", "start-here-novaos");
kickoff.currentConfigGroup = ["Shortcuts"];
kickoff.writeConfig("global", "Alt+F1");

var tasks = panel.addWidget("org.kde.plasma.icontasks");
tasks.currentConfigGroup = ["General"];
tasks.writeConfig("launchers", [
    "applications:firefox-esr.desktop",
    "applications:org.kde.dolphin.desktop",
    "applications:org.novaos.Store.desktop",
    "applications:org.kde.konsole.desktop",
    "applications:org.novaos.Notes.desktop",
    "applications:systemsettings.desktop"
]);

panel.addWidget("org.kde.plasma.panelspacer");
panel.addWidget("org.kde.plasma.pager");
panel.addWidget("org.kde.plasma.systemtray");
var clock = panel.addWidget("org.kde.plasma.digitalclock");
clock.currentConfigGroup = ["Appearance"];
clock.writeConfig("showDate", "true");
clock.writeConfig("dateFormat", "shortDate");
panel.addWidget("org.kde.plasma.showdesktop");
