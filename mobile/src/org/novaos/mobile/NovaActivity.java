package org.novaos.mobile;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.novaos.tv.AppFilter;
import org.novaos.tv.Channel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Nova, the NovaOS assistant: speak ("Hey Nova" on the home screen, or the microphone
 * button) or type; Nova answers aloud and carries out the command. No cloud AI: speech
 * recognition and voice are Android's own, the commands are understood by NovaBrain.
 */
public class NovaActivity extends Activity {
    static final String EXTRA_QUERY = "query";    // already spoken text ("Hey Nova, wie spät ist es")
    static final String EXTRA_LISTEN = "listen";  // start listening right away
    private static final int MIC = 21, CONTACTS = 22, LOCATION = 23;

    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private TextView heard, answer;
    private View orb;
    private ValueAnimator pulse;
    private EditText input;
    private ImageButton mic;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private NovaBrain.Command waiting;  // needs a permission first
    private int seed = (int) (System.currentTimeMillis() / 1000);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.app_nova, getString(R.string.nova_sub));
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        orb = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF8E6CFF, 0xFF3D7BFF, 0xFF22C1E8});
        g.setShape(GradientDrawable.OVAL);
        orb.setBackground(g);
        orb.setElevation(Ui.dp(this, 16));
        orb.setOnClickListener(v -> listen());
        orb.setContentDescription(getString(R.string.nova_listen));
        // landscape: a smaller orb, so the typing field stays on the screen
        boolean wide = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int orbSize = Ui.dp(this, wide ? 72 : 132);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(orbSize, orbSize);
        ol.topMargin = Ui.dp(this, wide ? 6 : 28);
        ol.bottomMargin = Ui.dp(this, wide ? 8 : 22);
        content.addView(orb, ol);

        heard = Ui.text(this, "", 15, getColor(R.color.text_muted), false);
        heard.setGravity(Gravity.CENTER);
        content.addView(heard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        answer = Ui.text(this, getString(R.string.nova_hello), 22, Color.WHITE, true);
        answer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(this, 8);
        content.addView(answer, al);

        // suggestions
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (int res : new int[] {R.string.nova_try_time, R.string.nova_try_weather, R.string.nova_try_timer,
                R.string.nova_try_torch, R.string.nova_try_camera, R.string.nova_try_note, R.string.nova_try_read, R.string.nova_try_mail, R.string.nova_try_tv,
                R.string.nova_try_calc, R.string.nova_try_joke}) {
            TextView chip = Ui.text(this, getString(res), 14, Color.WHITE, false);
            chip.setBackgroundResource(R.drawable.pill_bg);
            chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
            chip.setOnClickListener(v -> handle(chip.getText().toString()));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.rightMargin = Ui.dp(this, 8);
            chips.addView(chip, cl);
        }
        hs.addView(chips);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(this, wide ? 12 : 26);
        content.addView(hs, hl);

        // typing + microphone
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint(R.string.nova_type);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(getColor(R.color.text_muted));
        input.setBackgroundResource(R.drawable.pill_bg);
        input.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String t = input.getText().toString().trim();
                if (!t.isEmpty()) {
                    input.setText("");
                    handle(t);
                }
                return true;
            }
            return false;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        mic = new ImageButton(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setBackgroundResource(R.drawable.accent_round_bg);
        mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 14);
        mic.setPadding(p, p, p, p);
        mic.setContentDescription(getString(R.string.nova_listen));
        mic.setOnClickListener(v -> listen());
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        ml.leftMargin = Ui.dp(this, 10);
        bar.addView(mic, ml);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, wide ? 10 : 18);
        content.addView(bar, bl);

        // "Hey Nova" on the home screen
        Switch hey = new Switch(this);
        hey.setText(R.string.nova_hey_switch);
        hey.setTextColor(Color.WHITE);
        hey.setChecked(HeyNova.enabled(this));
        hey.setOnCheckedChangeListener((b, on) -> {
            HeyNova.setEnabled(this, on);
            if (on && !micAllowed()) requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MIC);
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 22);
        content.addView(hey, sl);
        TextView heyHint = Ui.text(this, getString(R.string.nova_hey_hint), 12, getColor(R.color.text_muted), false);
        content.addView(heyHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            mic.setVisibility(View.GONE);
            heard.setText(R.string.nova_no_speech);
        }
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.getDefault());
            ttsReady = true;
            if (pendingSpeech != null) speak(pendingSpeech);
        });

        String query = getIntent().getStringExtra(EXTRA_QUERY);
        if (query != null && !NovaBrain.clean(query).isEmpty()) {
            handle(query);
        } else if (getIntent().getBooleanExtra(EXTRA_LISTEN, false) || query != null) {
            orb.post(this::listen);
        }
    }

    @Override
    protected void onPause() {
        if (recognizer != null) recognizer.cancel();
        setListening(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private boolean micAllowed() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == MIC && ok) listen();
        if ((code == CONTACTS || code == LOCATION) && waiting != null) {
            NovaBrain.Command c = waiting;
            waiting = null;
            execute(c);
        }
    }

    // ------------------------------------------------------------ listening

    private void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            input.requestFocus();
            return;
        }
        if (!micAllowed()) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        if (tts != null) tts.stop();
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle b) {
                    setListening(true);
                    heard.setText(R.string.nova_listening);
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rms) {
                    float s = 1f + Math.max(0f, Math.min(rms, 10f)) / 40f;
                    orb.setScaleX(s);
                    orb.setScaleY(s);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    setListening(false);
                }

                @Override
                public void onError(int error) {
                    setListening(false);
                    heard.setText(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? R.string.nova_heard_nothing : R.string.nova_speech_error);
                }

                @Override
                public void onResults(Bundle b) {
                    setListening(false);
                    List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty()) handle(r.get(0));
                }

                @Override
                public void onPartialResults(Bundle b) {
                    List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && !r.get(0).isEmpty()) heard.setText("„" + r.get(0) + "“");
                }

                @Override
                public void onEvent(int type, Bundle b) {}
            });
        }
        recognizer.startListening(recognizerIntent(this));
    }

    static Intent recognizerIntent(Context c) {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
    }

    private void setListening(boolean on) {
        if (on && pulse == null) {
            pulse = ValueAnimator.ofFloat(0.6f, 1f);
            pulse.setDuration(700);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> orb.setAlpha((float) a.getAnimatedValue()));
            pulse.start();
        } else if (!on && pulse != null) {
            pulse.cancel();
            pulse = null;
            orb.setAlpha(1f);
            orb.setScaleX(1f);
            orb.setScaleY(1f);
        }
    }

    private void speak(String text) {
        answer.setText(text);
        if (!ttsReady) {
            pendingSpeech = text;
            return;
        }
        pendingSpeech = null;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova");
    }

    // ------------------------------------------------------------ understanding + doing

    private void handle(String spoken) {
        heard.setText("„" + spoken + "“");
        List<String> names = new ArrayList<>();
        for (Channel ch : Channels.get(this)) names.add(ch.name);
        execute(NovaBrain.understand(spoken, german, names, seed++));
    }

    private void execute(NovaBrain.Command c) {
        switch (c.kind) {
            case TIME:
                speak(getString(R.string.nova_time, DateFormat.getTimeFormat(this).format(new java.util.Date())));
                break;
            case DATE:
                speak(getString(R.string.nova_date, DateFormat.format(german ? "EEEE, d. MMMM yyyy" : "EEEE, MMMM d, yyyy",
                        Calendar.getInstance())));
                break;
            case TIMER:
                if (c.seconds <= 0) {
                    speak(c.answer);
                } else if (start(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, c.seconds)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "Nova").putExtra(AlarmClock.EXTRA_SKIP_UI, true))) {
                    speak(getString(R.string.nova_timer, duration(c.seconds)));
                }
                break;
            case ALARM:
                if (c.hour < 0) {
                    if (start(new Intent(AlarmClock.ACTION_SHOW_ALARMS))) speak(getString(R.string.nova_alarms));
                } else if (start(new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, c.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, c.minute).putExtra(AlarmClock.EXTRA_MESSAGE, "Nova")
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true))) {
                    speak(getString(R.string.nova_alarm, String.format(Locale.ROOT, "%d:%02d", c.hour, c.minute)));
                }
                break;
            case TORCH:
                speak(torch(c.level == 1) ? getString(c.level == 1 ? R.string.nova_torch_on : R.string.nova_torch_off)
                        : getString(R.string.no_torch));
                break;
            case OPEN_APP:
                openApp(c.text);
                break;
            case SETTINGS:
                openSettings(c.text);
                break;
            case CALL:
                contact(c);
                break;
            case MESSAGE:
                if (!chatMessage(c)) contact(c);
                break;
            case READ_MESSAGES:
                readMessages();
                break;
            case READ_MAIL:
                readMail();
                break;
            case MAIL:
                writeMail(c);
                break;
            case NOTE:
                NotesDb.Note n = new NotesDb.Note();
                n.title = c.text.length() > 40 ? c.text.substring(0, 40) + " …" : c.text;
                n.text = c.text;
                new NotesDb(this).save(n);
                speak(getString(R.string.nova_noted, c.text));
                break;
            case WEATHER:
                weather(c);
                break;
            case TV:
                tv(c.text);
                break;
            case MUSIC:
                music(c);
                break;
            case WIKI:
                wiki(c);
                break;
            case NAVIGATE:
                if (c.text.isEmpty()) {
                    startActivity(new Intent(this, MapActivity.class));
                    speak(getString(R.string.nova_map_open));
                } else {
                    startActivity(MapActivity.navigate(this, c.text));
                    speak(getString(R.string.nova_navigate, c.text));
                }
                break;
            case SEARCH:
                Intent web = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, c.text);
                if (!quietStart(web)) {
                    start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(c.text))));
                }
                speak(getString(R.string.nova_search, c.text));
                break;
            case VOLUME:
                AudioManager am = getSystemService(AudioManager.class);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, c.level > 0 ? AudioManager.ADJUST_RAISE
                            : c.level < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                }
                speak(getString(c.level > 0 ? R.string.nova_louder : c.level < 0 ? R.string.nova_quieter : R.string.nova_mute));
                break;
            case PHOTO:
                startActivity(new Intent(this, CameraActivity.class));
                speak(getString(R.string.nova_camera));
                break;
            default:
                speak(c.answer);
                break;
        }
    }

    private String duration(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (h > 0) parts.add(getResources().getQuantityString(R.plurals.nova_hours, h, h));
        if (m > 0) parts.add(getResources().getQuantityString(R.plurals.nova_minutes, m, m));
        if (s > 0) parts.add(getResources().getQuantityString(R.plurals.nova_seconds, s, s));
        return android.text.TextUtils.join(german ? " und " : " and ", parts);
    }

    private boolean start(Intent i) {
        if (quietStart(i)) return true;
        speak(getString(R.string.nova_no_app));
        return false;
    }

    private boolean quietStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private boolean torch(boolean on) {
        CameraManager cm = getSystemService(CameraManager.class);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    cm.setTorchMode(id, on);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // no torch
        }
        return false;
    }

    private void openApp(String name) {
        String n = AppFilter.normalize(name);
        Class<?> own = null;
        if (n.matches("(nova )?(kamera|camera)")) own = CameraActivity.class;
        else if (n.matches("(nova )?(galerie|gallery|fotos|photos|bilder|pictures)")) own = GalleryActivity.class;
        else if (n.matches("(nova )?(dateien|files|eigene dateien|my files)")) own = FilesActivity.class;
        else if (n.matches("(nova )?(notizen|notes|notiz)")) own = NotesActivity.class;
        else if (n.matches("(nova )?(store|app store|play store nova)")) own = StoreActivity.class;
        else if (n.matches("(nova )?(chat|nachrichten chat)")) own = ChatActivity.class;
        else if (n.matches("(nova )?(mail|e ?mail|email|e-mail|mails|e-mails|post)")) own = MailActivity.class;
        else if (n.matches("(tv|fernsehen|fernseher|sender)")) own = TvActivity.class;
        else if (n.matches("(nova )?(filme|films|movies)")) own = FilmeActivity.class;
        else if (n.matches("(software update|update|updates)")) own = UpdateActivity.class;
        if (own != null) {
            startActivity(new Intent(this, own));
            speak(getString(R.string.nova_opening, name));
            return;
        }
        List<App> apps = Apps.load(this);
        App best = null;
        int bestScore = 0;
        for (App a : apps) {
            int s = AppFilter.score(a.label, n);
            if (s > bestScore) {
                bestScore = s;
                best = a;
            }
        }
        if (best == null) {
            speak(getString(R.string.nova_no_such_app, name));
            return;
        }
        Launch.app(this, best, null);
        speak(getString(R.string.nova_opening, best.label));
    }

    private void openSettings(String what) {
        switch (what) {
            case "wifi": Launch.settings(this, Settings.ACTION_WIFI_SETTINGS); break;
            case "bluetooth": Launch.settings(this, Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case "display": Launch.settings(this, Settings.ACTION_DISPLAY_SETTINGS); break;
            case "sound": Launch.settings(this, Settings.ACTION_SOUND_SETTINGS); break;
            case "battery": Launch.settings(this, Intent.ACTION_POWER_USAGE_SUMMARY); break;
            case "nova": startActivity(new Intent(this, SettingsActivity.class)); break;
            default: Launch.settings(this, Settings.ACTION_SETTINGS); break;
        }
        speak(getString(R.string.nova_settings));
    }

    /**
     * "Schreib Lisa (im Chat): ..." - over Nova Chat if Lisa is a Nova Chat contact (or a direct
     * chat has her name); with text it is sent at once, without the chat opens with Nova listening.
     */
    private boolean chatMessage(NovaBrain.Command c) {
        String[] via = NovaBrain.viaChat(c.text);
        boolean wanted = !via[1].isEmpty();
        if (!ChatDb.loggedIn(this)) {
            if (wanted) speak(getString(R.string.nova_chat_login));
            return wanted;
        }
        String q = AppFilter.normalize(via[0]);
        String roomId = null, name = null, user = null;
        int best = 0;
        for (MatrixSync.Contact ct : ChatDb.get(this).contacts()) {
            int s = AppFilter.score(ct.name, q);
            if (s > best) {
                best = s;
                user = ct.id;
                name = ct.name;
            }
        }
        for (ChatDb.RoomRow r : ChatDb.get(this).rooms()) {
            if (r.invite) continue;
            int s = AppFilter.score(r.name, q);
            if (s > best) {
                best = s;
                roomId = r.id;
                name = r.name;
                user = null;
            }
        }
        if (best == 0) {
            if (wanted) speak(getString(R.string.nova_chat_unknown, via[0]));
            return wanted;
        }
        if (user != null) {
            ChatDb.RoomRow direct = ChatDb.get(this).directRoom(user);
            if (direct != null) roomId = direct.id;
        }
        if (roomId == null) {  // contact without a chat yet: start it in Nova Chat
            ChatActivity.startDirect(this, user, null);
            speak(getString(R.string.nova_chat_opening, name));
            return true;
        }
        if (c.body.isEmpty()) {
            startActivity(new Intent(this, RoomActivity.class).putExtra(RoomActivity.EXTRA_ROOM, roomId)
                    .putExtra(RoomActivity.EXTRA_NOVA, true));
            speak(getString(R.string.nova_chat_opening, name));
            return true;
        }
        String room = roomId, who = name, text = Dictation.clean(c.body, german);
        MatrixApi api = ChatDb.api(this);
        new Thread(() -> {
            boolean ok;
            try {
                api.sendText(room, text);
                ok = true;
            } catch (java.io.IOException e) {
                ok = false;
            }
            boolean sent = ok;
            runOnUiThread(() -> {
                if (!isDestroyed()) speak(getString(sent ? R.string.nova_chat_sent : R.string.nova_chat_failed, who));
            });
        }).start();
        return true;
    }

    /** "Lies meine E-Mails vor": the unread mails of the inbox (fetched first). */
    private void readMail() {
        MailStore.Account a = MailStore.current(this);
        if (a == null) {
            speak(getString(R.string.nova_mail_setup));
            startActivity(new Intent(this, MailActivity.class));
            return;
        }
        answer.setText(R.string.mail_loading);
        new Thread(() -> {
            try {
                MailSync.refresh(getApplicationContext(), a, "INBOX");
            } catch (java.io.IOException ignored) {
                // read what is on the phone
            }
            List<MailStore.Mail> unread = MailStore.get(this).unread(a.id, "INBOX", 10);
            StringBuilder b = new StringBuilder();
            if (unread.isEmpty()) {
                b.append(getString(R.string.mail_spoken_none));
            } else {
                b.append(getResources().getQuantityString(R.plurals.mail_spoken_count, unread.size(), unread.size()));
                for (MailStore.Mail m : unread) {
                    b.append(' ').append(getString(R.string.mail_spoken_one, m.sender(),
                            m.subject.isEmpty() ? getString(R.string.mail_no_subject) : m.subject));
                }
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) speak(b.toString());
            });
        }).start();
    }

    /** "Schreib eine E-Mail an Lisa: ..." - opens a new mail (to the address Nova finds for the name). */
    private void writeMail(NovaBrain.Command c) {
        if (MailStore.current(this) == null) {
            speak(getString(R.string.nova_mail_setup));
            startActivity(new Intent(this, MailActivity.class));
            return;
        }
        String to = NovaBrain.spokenEmail(c.text);
        String who = to;
        if (to == null && !c.text.isEmpty()) {
            String q = AppFilter.normalize(c.text);
            int best = 0;
            for (Mime.Address a : MailStore.get(this).addresses()) {
                int sc = Math.max(AppFilter.score(a.name, q), AppFilter.score(a.email, q));
                if (sc > best) {
                    best = sc;
                    to = a.toString();
                    who = a.display();
                }
            }
            if (to == null && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try (Cursor cur = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        new String[] {ContactsContract.CommonDataKinds.Email.DISPLAY_NAME, ContactsContract.CommonDataKinds.Email.ADDRESS},
                        null, null, null)) {
                    while (cur != null && cur.moveToNext()) {
                        String name = cur.getString(0) == null ? "" : cur.getString(0);
                        int sc = AppFilter.score(name, q);
                        if (sc > best && cur.getString(1) != null) {
                            best = sc;
                            to = new Mime.Address(name, cur.getString(1)).toString();
                            who = name;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // no contacts
                }
            }
        }
        Intent i = new Intent(this, MailComposeActivity.class).putExtra(MailComposeActivity.EXTRA_TO, to == null ? "" : to);
        if (!c.body.isEmpty()) i.putExtra(MailComposeActivity.EXTRA_BODY, Dictation.clean(c.body, german));
        else i.putExtra(MailComposeActivity.EXTRA_NOVA, true);
        startActivity(i);
        speak(who != null ? getString(R.string.nova_mail_writing, who) : getString(R.string.nova_mail_writing_nobody));
    }

    /** "Lies meine Nachrichten vor": new Nova Chat messages, chat by chat. */
    private void readMessages() {
        if (!ChatDb.loggedIn(this)) {
            speak(getString(R.string.nova_chat_login));
            return;
        }
        String me = ChatDb.me(this);
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (ChatDb.RoomRow r : ChatDb.get(this).rooms()) {
            if (r.invite) {
                b.append(getString(R.string.nova_chat_invite, r.name)).append(' ');
                continue;
            }
            if (r.unread <= 0) continue;
            for (MatrixSync.Message m : ChatDb.get(this).unreadOf(r.id, Math.min(r.unread, 5), me)) {
                String who = r.directUser.isEmpty() ? m.senderName + " (" + r.name + ")" : r.name;
                switch (m.kind) {
                    case "image": b.append(getString(R.string.chat_spoken_photo, who)); break;
                    case "audio": b.append(getString(R.string.chat_spoken_voice, who)); break;
                    case "text": case "notice": b.append(getString(R.string.chat_spoken_text, who, m.body)); break;
                    default: b.append(getString(R.string.chat_spoken_file, who, m.body)); break;
                }
                b.append(' ');
                count++;
            }
        }
        if (b.length() == 0) {
            speak(getString(R.string.nova_chat_none));
            return;
        }
        speak((count > 0 ? getResources().getQuantityString(R.plurals.nova_chat_count, count, count) + " " : "") + b.toString().trim());
    }

    /** Call or message someone from the phone's contacts (or a spoken number). */
    private void contact(NovaBrain.Command c) {
        String number = c.text.replaceAll("[^0-9+]", "");
        String name = c.text;
        if (number.length() < 3) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                waiting = c;
                speak(getString(R.string.nova_need_contacts));
                requestPermissions(new String[] {Manifest.permission.READ_CONTACTS}, CONTACTS);
                return;
            }
            String[] found = findContact(c.text);
            if (found == null) {
                speak(getString(R.string.nova_no_contact, c.text));
                return;
            }
            name = found[0];
            number = found[1];
        }
        if (c.kind == NovaBrain.Kind.CALL) {
            if (start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))) {
                speak(getString(R.string.nova_calling, name));
            }
        } else if (start(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", c.body))) {
            speak(getString(R.string.nova_message, name));
        }
    }

    /** {display name, number} of the best matching contact, or null. */
    private String[] findContact(String spoken) {
        String q = AppFilter.normalize(spoken);
        String[] best = null;
        int bestScore = 0;
        try (Cursor cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(0);
                int s = AppFilter.score(name == null ? "" : name, q);
                if (s > bestScore) {
                    bestScore = s;
                    best = new String[] {name, cur.getString(1)};
                }
            }
        } catch (RuntimeException ignored) {
            // no contacts
        }
        return best;
    }

    private void tv(String channel) {
        if (channel == null || channel.isEmpty()) {
            startActivity(new Intent(this, TvActivity.class));
            speak(getString(R.string.nova_tv));
            return;
        }
        List<Channel> list = Channels.get(this);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(channel)) {
                startActivity(new Intent(this, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_INDEX, i));
                speak(getString(R.string.nova_channel, channel));
                return;
            }
        }
        startActivity(new Intent(this, TvActivity.class));
    }

    private void weather(NovaBrain.Command c) {
        answer.setText(R.string.loading);
        String city = c.text;
        Location here = null;
        if (city.isEmpty()) {
            city = getSharedPreferences("nova", MODE_PRIVATE).getString("city", "");
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                here = lastLocation();
            } else if (city.isEmpty() && !getSharedPreferences("nova", MODE_PRIVATE).getBoolean("asked_location", false)) {
                getSharedPreferences("nova", MODE_PRIVATE).edit().putBoolean("asked_location", true).apply();
                waiting = c;
                requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION);
                return;
            }
            if (here == null && city.isEmpty()) {
                speak(getString(R.string.nova_which_city));
                return;
            }
        } else {
            getSharedPreferences("nova", MODE_PRIVATE).edit().putString("city", city).apply();
        }
        String wantedCity = city;
        Location loc = here;
        new Thread(() -> {
            String text;
            try {
                String name = "";
                double lat, lon;
                if (loc != null) {
                    lat = loc.getLatitude();
                    lon = loc.getLongitude();
                } else {
                    Weather.Place p = Weather.place(Download.text(Weather.geocodeUrl(wantedCity, german)));
                    if (p == null) throw new IllegalArgumentException("place");
                    name = p.name;
                    lat = p.lat;
                    lon = p.lon;
                }
                Weather.Now now = Weather.now(Download.text(Weather.forecastUrl(lat, lon)));
                text = now == null ? getString(R.string.nova_weather_failed) : Weather.sentence(name, now, german);
            } catch (IllegalArgumentException e) {
                text = getString(R.string.nova_no_city, wantedCity);
            } catch (Exception e) {
                text = getString(R.string.nova_weather_failed);
            }
            String said = text;
            runOnUiThread(() -> {
                if (!isDestroyed()) speak(said);
            });
        }).start();
    }

    @SuppressWarnings("MissingPermission")
    private Location lastLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        Location best = null;
        if (lm == null) return null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
            // no permission
        }
        return best;
    }

    /** Nova Musik: radio, songs, my music, and next/previous/pause/resume. */
    private void music(NovaBrain.Command c) {
        Music m = Music.get(this);
        switch (c.level) {
            case NovaBrain.MUSIC_RADIO:
                startActivity(new Intent(this, MusicActivity.class).putExtra(MusicActivity.EXTRA_RADIO, c.text));
                speak(c.text.isEmpty() ? getString(R.string.nova_music_radio_top) : getString(R.string.nova_music_radio, c.text));
                break;
            case NovaBrain.MUSIC_SONG:
                startActivity(new Intent(this, MusicActivity.class).putExtra(MusicActivity.EXTRA_SONG, c.text));
                speak(getString(R.string.nova_music_song, c.text));
                break;
            case NovaBrain.MUSIC_LOCAL:
                if (m.current() != null && !m.playing()) {
                    m.play();
                    speak(getString(R.string.nova_music_resumed));
                } else {
                    startActivity(new Intent(this, MusicActivity.class).putExtra(MusicActivity.EXTRA_LOCAL, true));
                    speak(getString(R.string.nova_music_local));
                }
                break;
            default:
                if (m.current() == null) {
                    speak(getString(R.string.nova_music_nothing));
                    break;
                }
                if (c.level == NovaBrain.MUSIC_NEXT) m.next();
                else if (c.level == NovaBrain.MUSIC_PREVIOUS) m.previous();
                else if (c.level == NovaBrain.MUSIC_PAUSE) m.pause();
                else m.play();
                speak(getString(c.level == NovaBrain.MUSIC_NEXT ? R.string.nova_music_next : c.level == NovaBrain.MUSIC_PREVIOUS
                        ? R.string.nova_music_previous : c.level == NovaBrain.MUSIC_PAUSE ? R.string.nova_music_paused : R.string.nova_music_resumed));
                break;
        }
    }


    /** Knowledge question: the first two sentences of the Wikipedia article; otherwise a web search. */
    private void wiki(NovaBrain.Command c) {
        answer.setText(R.string.loading);
        boolean de = java.util.Locale.getDefault().getLanguage().equals("de");
        new Thread(() -> {
            Wiki.Summary found = null;
            for (String title : Wiki.titles(c.text)) {
                try {
                    Wiki.Summary s = Wiki.parse(Download.text(Wiki.summaryUrl(title, de)));
                    if (s != null && !s.ambiguous) {
                        found = s;
                        break;
                    }
                } catch (java.io.IOException ignored) {
                    // not found (404) or offline: try the next spelling
                }
            }
            Wiki.Summary s = found;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (s == null) {
                    Intent web = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, c.text);
                    if (!quietStart(web)) start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(c.text))));
                    speak(getString(R.string.nova_search, c.text));
                } else {
                    speak(Wiki.sentences(s.text, 2));
                    if (!s.url.isEmpty()) {
                        android.widget.Toast.makeText(this, getString(R.string.nova_wiki_source, s.title), android.widget.Toast.LENGTH_LONG).show();
                    }
                }
            });
        }).start();
    }

}
