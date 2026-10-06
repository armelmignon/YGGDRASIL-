package com.yggdrasil.app;

import android.Manifest;
import android.os.Handler;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
private final Handler handler = new Handler();

private static final int AUDIO_PERMISSION = 1001;  

private WebView web;  
private TextToSpeech tts;  
private SpeechRecognizer recognizer;  

private boolean ttsReady = false;  
private boolean listening = false;  
private boolean pendingListening = false;  
private static final String API_URL = "https://script.google.com/macros/s/AKfycbzMsx9e7AadOJgJR8F8b4BqSluJ6jt-dcoqaZ4zseVsJhh7AeVZGjN1rfJGv_8VUdUaDQ/exec";  
  
@Override  
public void onCreate(Bundle state) {  
    super.onCreate(state);  

    web = new WebView(this);  

    WebSettings s = web.getSettings();  
    s.setJavaScriptEnabled(true);  
    s.setDomStorageEnabled(true);  
    s.setDatabaseEnabled(true);  
    s.setAllowFileAccess(true);  
    s.setMediaPlaybackRequiresUserGesture(false);  

    web.setWebViewClient(new WebViewClient());  

    web.setWebChromeClient(new WebChromeClient() {  
        @Override  
        public void onPermissionRequest(final PermissionRequest r) {  
            runOnUiThread(() -> r.grant(r.getResources()));  
        }  
    });  

    web.addJavascriptInterface(new AndroidVoice(), "AndroidVoice");  

    tts = new TextToSpeech(this, status -> {  
        if (status == TextToSpeech.SUCCESS) {  
            int result = tts.setLanguage(Locale.FRENCH);  

            if (result != TextToSpeech.LANG_MISSING_DATA &&  
                result != TextToSpeech.LANG_NOT_SUPPORTED) {  
                ttsReady = true;  
            }  
        }  
    });  

    if (SpeechRecognizer.isRecognitionAvailable(this)) {  
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);  
        recognizer.setRecognitionListener(new RecognitionListener() {  

            @Override  
            public void onReadyForSpeech(Bundle params) {  
                listening = true;  
                sendStatus("🎙️ YGGDRASIL vous écoute…");  
            }  

            @Override  
            public void onBeginningOfSpeech() {}  

            @Override  
            public void onRmsChanged(float rmsdB) {}  

            @Override  
            public void onBufferReceived(byte[] buffer) {}  

            @Override  
            public void onEndOfSpeech() {  
                listening = false;  
            }  

            @Override  
            public void onError(int error) {  
                listening = false;  

                String message = "Erreur de reconnaissance vocale.";  

                if (error == SpeechRecognizer.ERROR_NO_MATCH ||  
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {  
                    message = "Aucune parole détectée.";  
                } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {  
                    message = "Autorisation du microphone nécessaire.";  
                } else if (error == SpeechRecognizer.ERROR_NETWORK ||  
                           error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {  
                    message = "Problème de connexion pour la reconnaissance vocale.";  
                }  

                sendStatus(message);  
                sendToJavaScript("window.yggVoiceEnded && window.yggVoiceEnded();");  
            }  

            @Override  
            public void onResults(Bundle results) {  
                listening = false;  

                ArrayList<String> matches =  
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);  

                if (matches != null && !matches.isEmpty()) {  
                    String transcript = matches.get(0);  
                    askAI(transcript);  

                    String safeText = org.json.JSONObject.quote(transcript);  

                    sendToJavaScript(  
                        "window.yggVoiceResult && window.yggVoiceResult(" +  
                        safeText + ");"  
                    );  
                }  

                sendToJavaScript("window.yggVoiceEnded && window.yggVoiceEnded();");  
            }  

            @Override  
            public void onPartialResults(Bundle results) {}  

            @Override  
            public void onEvent(int eventType, Bundle params) {}  
        });  
    }  

    setContentView(web);  
    web.loadUrl("file:///android_asset/index.html");  
}  

private void sendStatus(String message) {  
    String safeMessage = org.json.JSONObject.quote(message);  

    sendToJavaScript(  
        "var s=document.getElementById('voiceStatus');" +  
        "if(s)s.textContent=" + safeMessage + ";"  
    );  
}  

private void sendToJavaScript(String script) {  
    runOnUiThread(() -> {  
        if (web != null) {  
            web.evaluateJavascript(script, null);  
        }  
    });  
}  

private void askAI(String question) {  
new Thread(() -> {  
    try {  
        URL url = new URL(API_URL);  
        HttpURLConnection connection =  
                (HttpURLConnection) url.openConnection();  

        connection.setRequestMethod("POST");  
        connection.setDoOutput(true);  
        connection.setRequestProperty("Content-Type", "application/json");  

        String json =  
                "{\"message\":" +  
                org.json.JSONObject.quote(question) +  
                "}";  

        try (OutputStream os = connection.getOutputStream()) {  
            os.write(json.getBytes("UTF-8"));  
        }  

        BufferedReader reader = new BufferedReader(  
                new InputStreamReader(connection.getInputStream())  
        );  

        StringBuilder response = new StringBuilder();  
        String line;  

        while ((line = reader.readLine()) != null) {  
            response.append(line);  
        }  

        reader.close();  
        connection.disconnect();  

        String answer = response.toString();
        answer = new org.json.JSONObject(answer).getString("answer");

        runOnUiThread(() -> {  
            if (tts != null && ttsReady) {  
                tts.speak(  
                        answer,  
                        TextToSpeech.QUEUE_FLUSH,  
                        null,  
                        "YGGDRASIL_AI"  
                );  
            }  

            sendStatus(answer);  
        });  

    } catch (Exception e) {  
        runOnUiThread(() ->  
                sendStatus("Impossible de contacter le cerveau en ligne.")  
        );  
    }  
}).start();  
            }  
private void startRecognition() {  
    if (recognizer == null) {  
        sendStatus("Reconnaissance vocale indisponible.");  
        return;  
    }  

    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)  
            != PackageManager.PERMISSION_GRANTED) {  
        pendingListening = true;  

        requestPermissions(  
            new String[]{Manifest.permission.RECORD_AUDIO},  
            AUDIO_PERMISSION  
        );  
        return;  
    }  

    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);  
    intent.putExtra(  
        RecognizerIntent.EXTRA_LANGUAGE_MODEL,  
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM  
    );  
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");  
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR");  
    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);  
    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);  

    try {  
        recognizer.startListening(intent);  
    } catch (Exception e) {  
        listening = false;  
        sendStatus("Impossible de démarrer la reconnaissance vocale.");  
    }  
}  

private void stopRecognition() {  
    listening = false;  

    if (recognizer != null) {  
        recognizer.cancel();  
    }  
}  

public class AndroidVoice {  

    @JavascriptInterface  
    public void speak(String text) {  
        runOnUiThread(() -> {  
            if (tts != null && ttsReady) {  
                tts.stop();  
                tts.speak(  
                    text,  
                    TextToSpeech.QUEUE_FLUSH,  
                    null,  
                    "YGGDRASIL_VOICE"  
                );  
            }  
        });  
    }  

    @JavascriptInterface  
    public boolean isAvailable() {  
        return ttsReady;  
    }  

    @JavascriptInterface  
    public void startListening() {  
        runOnUiThread(() -> startRecognition());  
    }  

    @JavascriptInterface  
    public void stopListening() {  
        runOnUiThread(() -> stopRecognition());  
    }  

    @JavascriptInterface  
    public void stop() {  
        runOnUiThread(() -> {  
            if (tts != null) {  
                tts.stop();  
            }  
        });  
    }  
}  

@Override  
public void onRequestPermissionsResult(  
    int requestCode,  
    String[] permissions,  
    int[] grantResults  
) {  
    super.onRequestPermissionsResult(  
        requestCode, permissions, grantResults  
    );  

    if (requestCode == AUDIO_PERMISSION) {  
        if (grantResults.length > 0 &&  
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {  

            if (pendingListening) {  
                pendingListening = false;  
                startRecognition();  
            }  

        } else {  
            pendingListening = false;  
            sendStatus("Autorisation du microphone refusée.");  
        }  
    }  
}  

@Override  
protected void onDestroy() {  
    if (recognizer != null) {  
        recognizer.destroy();  
        recognizer = null;  
    }  

    if (tts != null) {  
        tts.stop();  
        tts.shutdown();  
    }  

    if (web != null) {  
        web.destroy();  
    }  

    super.onDestroy();  
}  

@Override
public void onBackPressed() {
    if (web.canGoBack()) {
        web.goBack();
    } else {
        super.onBackPressed();
    }
}
