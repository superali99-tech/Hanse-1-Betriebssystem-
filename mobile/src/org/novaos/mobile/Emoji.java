package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Emojis of the Nova Chat emoji panel (sorted like on phones, only emojis Android 9 and
 * newer can show) and "message consists only of emojis" (shown bigger). Android-independent.
 */
public final class Emoji {
    private Emoji() {}

    /** Tab symbol of each category. */
    public static final String[] TABS = {"😀", "👋", "❤️", "🐶", "🍕", "⚽", "🚗", "💡", "🇩🇪"};

    public static final String[][] LIST = {
        // smileys
        split("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 "
                + "🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 "
                + "😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊"),
        // hands and people
        split("👋 🤚 🖐️ ✋ 🖖 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦵 🦶 👂 "
                + "👃 🧠 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙋 🙆 🙅 🤷 🤦 🙇 💁 👮 👷 💂 🕵️ 👨‍⚕️ 👩‍⚕️ 👨‍🍳 "
                + "👩‍🍳 👨‍🎓 👩‍🎓 👨‍💻 👩‍💻 👰 🤵 🤰 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 👼 💃 🕺 👫 👭 👬 💏 💑 👪 🗣️ 👤 👥"),
        // hearts and symbols
        split("❤️ 🧡 💛 💚 💙 💜 🖤 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💌 💯 💢 💥 💫 💦 💨 💬 💭 💤 ⭐ 🌟 ✨ ⚡ 🔥 🎉 🎊 "
                + "✅ ☑️ ✔️ ❌ ❎ ❗ ❓ ❕ ❔ ‼️ ⁉️ ⚠️ 🚫 ⛔ 📵 🔞 ♻️ 🆗 🆕 🆒 🆓 🔝 🔜 ➕ ➖ ✖️ ➗ 💲 ©️ ®️ ™️ 🔴 🔵 "
                + "⚫ ⚪ 🔶 🔷 🔺 🔻 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ 🔀 🔁 🎵 🎶 ☮️ ✝️ ☯️ ☪️ 🕉️ ✡️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓"),
        // animals and nature
        split("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🦗 "
                + "🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🦛 🐪 🐫 🦒 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 "
                + "🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦔 🐾 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 "
                + "🌻 🌞 🌝 🌛 🌙 🌎 🌍 🌏 ⭐ ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ ❄️ ☃️ ⛄ 🌬️ 🌈 ☔ 💧 🌊 🌫️"),
        // food and drink
        split("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🥔 🍠 🥐 🥯 🍞 🥖 🥨 🧀 🥚 "
                + "🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🥙 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🧁 "
                + "🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥜 🍯 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🥄 🍴 🍽️ 🥣 🥡 🥢 🧂"),
        // activities
        split("⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸️ 🥌 🛷 🎿 ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ "
                + "🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 "
                + "🎸 🎻 🎲 🧩 ♟️ 🎯 🎳 🎮 🎰 🧸 🎁 🎈 🎀 🎃 🎆 🎇 🧨"),
        // travel and places
        split("🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛴 🚲 🛵 🏍️ 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 "
                + "🚇 🚊 🚉 ✈️ 🛫 🛬 💺 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ "
                + "🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🕋 ⛩️ 🌅 🌄 "
                + "🌠 🎑 🏙️ 🌃 🌌 🌉 🌁"),
        // objects
        split("⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📞 ☎️ 📟 📠 📺 📻 🎙️ ⏱️ ⏲️ ⏰ 🕰️ ⌛ ⏳ 📡 🔋 "
                + "🔌 💡 🔦 🕯️ 🧯 🗑️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 💣 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🔮 "
                + "📿 💈 🔭 🔬 💊 💉 🌡️ 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🧽 🔑 🗝️ 🚪 🛋️ 🛏️ 🧳 ⌛ 🎁 🎈 ✉️ 📩 📨 📧 💌 📥 📤 📦 🏷️ 📪 📫 "
                + "📬 📭 📮 📜 📃 📄 📑 📊 📈 📉 🗒️ 🗓️ 📆 📅 📇 🗃️ 🗳️ 🗄️ 📋 📁 📂 🗂️ 🗞️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 "
                + "🔗 📎 🖇️ 📐 📏 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓 🔔 🔕"),
        // flags
        split("🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🇩🇪 🇦🇹 🇨🇭 🇱🇮 🇱🇺 🇧🇪 🇳🇱 🇫🇷 🇮🇹 🇪🇸 🇵🇹 🇬🇧 🇮🇪 🇩🇰 🇸🇪 🇳🇴 🇫🇮 🇮🇸 "
                + "🇵🇱 🇨🇿 🇸🇰 🇭🇺 🇸🇮 🇭🇷 🇧🇦 🇷🇸 🇲🇪 🇦🇱 🇽🇰 🇲🇰 🇬🇷 🇧🇬 🇷🇴 🇲🇩 🇺🇦 🇧🇾 🇱🇹 🇱🇻 🇪🇪 🇷🇺 🇹🇷 🇨🇾 🇬🇪 🇦🇲 "
                + "🇦🇿 🇮🇷 🇮🇶 🇸🇾 🇱🇧 🇮🇱 🇵🇸 🇯🇴 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇪🇬 🇲🇦 🇩🇿 🇹🇳 🇱🇾 🇳🇬 🇬🇭 🇰🇪 🇪🇹 🇿🇦 🇦🇫 🇵🇰 🇮🇳 🇧🇩 "
                + "🇱🇰 🇨🇳 🇯🇵 🇰🇷 🇻🇳 🇹🇭 🇵🇭 🇮🇩 🇲🇾 🇦🇺 🇳🇿 🇺🇸 🇨🇦 🇲🇽 🇧🇷 🇦🇷 🇨🇱 🇨🇴 🇵🇪 🇻🇪 🇨🇺 🇪🇺 🇺🇳"),
    };

    private static String[] split(String s) {
        return s.trim().split("\\s+");
    }

    /** True if the text has only 1 to 6 emojis (and spaces): Nova Chat shows them big. */
    public static boolean onlyEmoji(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 60) return false;
        int count = 0;
        boolean joined = false;  // previous was a zero width joiner: the next one belongs to the same emoji
        boolean regional = false;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                joined = false;
                regional = false;
                continue;
            }
            if (cp == 0x200D) {
                joined = true;
                continue;
            }
            if (cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || (cp >= 0x1F3FB && cp <= 0x1F3FF) || (cp >= 0xE0020 && cp <= 0xE007F)) {
                continue;  // variation selector, keycap, skin tone, tag
            }
            if (!isEmoji(cp)) return false;
            if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {  // flags are two regional letters
                if (regional) {
                    regional = false;
                    continue;
                }
                regional = true;
            } else {
                regional = false;
            }
            if (!joined) count++;
            joined = false;
        }
        return count >= 1 && count <= 6;
    }

    private static boolean isEmoji(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x2B00 && cp <= 0x2BFF)
                || (cp >= 0x2190 && cp <= 0x21FF) || (cp >= 0x2300 && cp <= 0x23FF) || (cp >= 0x25A0 && cp <= 0x25FF)
                || cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139
                || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 || cp == 0x2934 || cp == 0x2935;
    }

    /** Recently used emojis first, without doubles, at most max. */
    public static List<String> remember(List<String> recent, String emoji, int max) {
        List<String> out = new ArrayList<>();
        out.add(emoji);
        for (String e : recent) if (!e.equals(emoji) && out.size() < max) out.add(e);
        return out;
    }
}
