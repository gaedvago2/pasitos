package co.pasitos.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private SpeechRecognizer sr;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
                int r = tts.setLanguage(new Locale("es", "CO"));
                if (r < 0) r = tts.setLanguage(new Locale("es", "MX"));
                if (r < 0) tts.setLanguage(new Locale("es"));
                tts.setSpeechRate(0.85f);
                tts.setPitch(1.15f);
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

    private void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    private void sendResult(JSONObject o) { js("window.__sr&&window.__sr(" + o.toString() + ")"); }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            sendError("permission");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { sendError("permission"); return; }
        if (sr != null) sr.destroy();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float v) {}
            public void onBufferReceived(byte[] buf) {}
            public void onEndOfSpeech() {}
            public void onPartialResults(Bundle p) {}
            public void onEvent(int t, Bundle p) {}
            public void onError(int e) {
                sendError(e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "permission" : "nomatch");
            }
            public void onResults(Bundle res) {
                ArrayList<String> list = res.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                try {
                    JSONObject o = new JSONObject();
                    o.put("alts", new JSONArray(list == null ? new ArrayList<String>() : list));
                    sendResult(o);
                } catch (Exception ex) { sendError("nomatch"); }
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        sr.startListening(i);
    }

    private void sendError(String code) {
        try { JSONObject o = new JSONObject(); o.put("err", code); o.put("alts", new JSONArray()); sendResult(o); }
        catch (Exception ignored) {}
    }

    class Bridge {
        @JavascriptInterface public void speak(String text) {
            if (!ttsReady) { js("window.__ttsDone&&window.__ttsDone()"); return; }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
        }
        @JavascriptInterface public void stop() { if (ttsReady) tts.stop(); }
        @JavascriptInterface public void listen() {
            runOnUiThread(() -> { if (ttsReady) tts.stop(); startListening(); });
        }
    }

    @Override
    protected void onDestroy() {
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
