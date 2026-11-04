package co.pasitos.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final List<String> LATAM = Arrays.asList(
        "CO", "MX", "US", "AR", "CL", "PE", "VE", "EC", "BO", "UY", "PY", "CR", "PA", "DO", "GT", "HN", "NI", "SV", "PR", "CU", "419");
    private WebView web;
    private TextToSpeech tts;
    private SpeechRecognizer sr;
    private boolean ttsReady = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ArrayList<String> partials = new ArrayList<>();
    private boolean delivered = false;
    private int busyRetries = 0;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("pasitos", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#E3F3EE"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "PasitosNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.08f);
                applyVoice(prefs.getString("voice", null));
                ttsReady = true;
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onDone(String id) { js("window.__ttsDone&&window.__ttsDone()"); }
            public void onError(String id) { js("window.__ttsDone&&window.__ttsDone()"); }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private boolean isSpanish(Voice v) {
        Locale l = v.getLocale();
        return l != null && ("es".equals(l.getLanguage()) || "spa".equals(l.getISO3Language()));
    }

    private boolean isLatam(Voice v) {
        String c = v.getLocale().getCountry();
        return c != null && LATAM.contains(c.toUpperCase());
    }

    private int score(Voice v) {
        int sc = 0;
        String c = v.getLocale().getCountry().toUpperCase();
        if ("CO".equals(c)) sc += 60; else if ("MX".equals(c) || "US".equals(c) || "419".equals(c)) sc += 50; else if (isLatam(v)) sc += 40;
        if (!v.isNetworkConnectionRequired()) sc += 10;
        Set<String> f = v.getFeatures();
        if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) sc -= 30;
        sc += v.getQuality() / 100;
        return sc;
    }

    private void applyVoice(String wanted) {
        try {
            Set<Voice> all = tts.getVoices();
            Voice best = null;
            if (all != null) {
                for (Voice v : all) {
                    if (!isSpanish(v)) continue;
                    if (wanted != null && wanted.equals(v.getName())) { best = v; break; }
                    if (best == null || score(v) > score(best)) best = v;
                }
            }
            if (best != null) { tts.setVoice(best); return; }
        } catch (Exception ignored) {}
        int r = tts.setLanguage(new Locale("es", "US"));
        if (r < 0) r = tts.setLanguage(new Locale("es", "MX"));
        if (r < 0) tts.setLanguage(new Locale("es"));
    }

    private void js(String code) { ui.post(() -> web.evaluateJavascript(code, null)); }

    private void deliver(ArrayList<String> alts, String err) {
        if (delivered) return;
        delivered = true;
        try {
            JSONObject o = new JSONObject();
            o.put("alts", new JSONArray(alts == null ? new ArrayList<String>() : alts));
            if (err != null) o.put("err", err);
            js("window.__sr&&window.__sr(" + o.toString() + ")");
        } catch (Exception ignored) {}
    }

    private Intent recognizerIntent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CO");
        i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"es-419", "es-MX", "es-US"});
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        return i;
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            deliver(null, "permission");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { deliver(null, "permission"); return; }
        if (sr != null) { try { sr.destroy(); } catch (Exception ignored) {} }
        partials = new ArrayList<>();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { js("window.__srReady&&window.__srReady()"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float v) {}
            public void onBufferReceived(byte[] buf) {}
            public void onEndOfSpeech() {}
            public void onEvent(int t, Bundle p) {}
            public void onPartialResults(Bundle p) {
                ArrayList<String> l = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null) for (String x : l) if (x != null && !x.trim().isEmpty() && !partials.contains(x)) partials.add(x);
            }
            public void onError(int e) {
                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && busyRetries < 2) {
                    busyRetries++;
                    ui.postDelayed(MainActivity.this::startListening, 400);
                    return;
                }
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { deliver(null, "permission"); return; }
                deliver(partials, partials.isEmpty() ? "nomatch" : null);
            }
            public void onResults(Bundle res) {
                ArrayList<String> l = res.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                ArrayList<String> out = new ArrayList<>();
                if (l != null) out.addAll(l);
                for (String x : partials) if (!out.contains(x)) out.add(x);
                deliver(out, null);
            }
        });
        sr.startListening(recognizerIntent());
    }

    class Bridge {
        @JavascriptInterface public void speak(String text) {
            if (!ttsReady) { js("window.__ttsDone&&window.__ttsDone()"); return; }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
        }
        @JavascriptInterface public void stop() { if (ttsReady) tts.stop(); }
        @JavascriptInterface public void listen() {
            ui.post(() -> {
                if (ttsReady) tts.stop();
                delivered = false;
                busyRetries = 0;
                // pequeña pausa para que el altavoz se calle antes de abrir el micrófono
                ui.postDelayed(MainActivity.this::startListening, 250);
            });
        }
        @JavascriptInterface public String voices() {
            JSONArray a = new JSONArray();
            try {
                Set<Voice> all = tts.getVoices();
                if (all == null) return "[]";
                List<Voice> list = new ArrayList<>();
                for (Voice v : all) if (isSpanish(v)) list.add(v);
                list.sort((x, y) -> score(y) - score(x));
                for (Voice v : list) {
                    JSONObject o = new JSONObject();
                    o.put("name", v.getName());
                    o.put("locale", v.getLocale().toLanguageTag());
                    o.put("latam", isLatam(v));
                    o.put("net", v.isNetworkConnectionRequired());
                    a.put(o);
                }
            } catch (Exception ignored) {}
            return a.toString();
        }
        @JavascriptInterface public String currentVoice() {
            try { Voice v = tts.getVoice(); return v == null ? "" : v.getName(); } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void setVoice(String name) {
            prefs.edit().putString("voice", name).apply();
            ui.post(() -> { if (ttsReady) applyVoice(name); });
        }
    }

    @Override
    protected void onDestroy() {
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
