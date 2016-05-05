package ru.galakart.majordroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.example.recognizer.DataFiles;
import com.example.recognizer.Grammar;
import com.example.recognizer.PhonMapper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class VoiceRecognizer implements RecognitionListener, SensorEventListener {
    private static final String TAG = "Recognizer";
    private static final String KWS_SEARCH = "hotword";
    private static final String COMMAND_SEARCH = "command";
    private static final int VOICE_INPUT_TIMIOUT_MILLIS = 10000;
    final boolean proximityEnable;
    final boolean voiceKeywordEnable;
    private final TextToSpeech textToSpeech;
    private final Activity activity;
    private final String voiceHotWord;
    private final Handler delayHandler;
    private final Handler handler;
    private final int requestCode;
    private final InitListener initListener;
    boolean voiceKeywordWorking = false;
    boolean voiceGoogleInProgress = false;
    SpeechRecognizer recognizer;
    private SensorManager mSensorManager;
    private float sensorMaximum;
    private float sensorValue;

    public VoiceRecognizer(Activity activity, final int requestCode, final InitListener initListener) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.initListener = initListener;
        textToSpeech = new TextToSpeech(activity, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "Sorry, tts is not supported");
                    }
                }
            }
        });
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        voiceKeywordEnable = (prefs.getString(activity.getString(R.string.voice_switch), "Выкл").equals("Вкл"));
        proximityEnable = (prefs.getString(activity.getString(R.string.voice_proximity), "Выкл").equals("Вкл"));
        voiceHotWord = prefs.getString(activity.getString(R.string.voice_phrase), "проснись");
        if (voiceKeywordEnable) {
            setupRecognizer();
        }
        delayHandler = new android.os.Handler();
        handler = new Handler();

        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (sensor != null) {
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            sensorMaximum = sensor.getMaximumRange();
        }
    }

    void setupRecognizer() {
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    /*
                    List<Device> devices = mController.getDevices();
                    final String[] names = new String[devices.size()];
                    for (int i = 0; i < names.length; i++) {
                        names[i] = devices.get(i).name;
                    }
                    */

                    final String[] names = new String[1];
                    names[0] = voiceHotWord;
                    PhonMapper phonMapper = new PhonMapper(activity.getAssets().open("dict/ru/hotwords"));
                    Grammar grammar = new Grammar(names, phonMapper);
                    grammar.addWords(voiceHotWord);

                    DataFiles dataFiles = new DataFiles(activity.getPackageName(), "ru");
                    File hmmDir = new File(dataFiles.getHmm());
                    File dict = new File(dataFiles.getDict());
                    File jsgf = new File(dataFiles.getJsgf());
                    copyAssets(hmmDir, activity);
                    saveFile(jsgf, grammar.getJsgf());
                    saveFile(dict, grammar.getDict());

                    Log.d(TAG, "Recognizer initiate");
                    recognizer = SpeechRecognizerSetup.defaultSetup()
                            .setAcousticModel(hmmDir)
                            .setDictionary(dict)
                            .setBoolean("-remove_noise", false)
                            .setKeywordThreshold(1e-7f)
                            .getRecognizer();

                    Log.d(TAG, "Add keyphrase search");
                    recognizer.addKeyphraseSearch(KWS_SEARCH, voiceHotWord);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            private void copyAssets(File baseDir, final Context context) throws IOException {
                String[] files = context.getAssets().list("hmm/ru");
                for (String fromFile : files) {
                    File toFile = new File(baseDir.getAbsolutePath() + "/" + fromFile);
                    InputStream in = context.getAssets().open("hmm/ru/" + fromFile);
                    FileUtils.copyInputStreamToFile(in, toFile);
                }
            }

            private void saveFile(File f, String content) throws IOException {
                File dir = f.getParentFile();
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("Cannot create directory: " + dir);
                }
                FileUtils.writeStringToFile(f, content, "UTF8");
            }

            @Override
            protected void onPostExecute(Exception ex) {
                if (ex != null) {
                    onRecognizerSetupError(ex);
                } else {
                    onRecognizerSetupComplete();
                }
            }
        }.execute();
    }

    void onRecognizerSetupComplete() {
        voiceKeywordWorking = true;
        Toast.makeText(activity, "Activation: \"" + voiceHotWord + "\"", Toast.LENGTH_SHORT).show();
        recognizer.addListener(this);
        recognizer.startListening(KWS_SEARCH);
    }

    void onRecognizerSetupError(Exception ex) {
        Toast.makeText(activity, ex.getMessage(), Toast.LENGTH_LONG).show();
        voiceKeywordWorking = false;
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
        if (recognizer.getSearchName().equals(COMMAND_SEARCH)) {
            recognizer.stop();
        }
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null) {
            return;
        }
        final String text = hypothesis.getHypstr();
        if (KWS_SEARCH.equals(recognizer.getSearchName())) {
            startRecognition();
        } else {
            Log.d(TAG, text);
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        String text = hypothesis != null ? hypothesis.getHypstr() : null;
        Log.d(TAG, "onResult " + text);
        if (text != null) {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
        if (COMMAND_SEARCH.equals(recognizer.getSearchName())) {
            recognizer.startListening(KWS_SEARCH);
        }
    }

    @Override
    public void onError(final Exception e) {
    }

    @Override
    public void onTimeout() {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!proximityEnable) {
            return;
        }
        sensorValue = event.values[0];
        if (sensorValue < sensorMaximum) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if ((sensorValue < sensorMaximum)) {
                        startGoogleVoiceRecognize();
                    }
                }
            }, (long) 500);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    synchronized void startRecognition() {
        if (recognizer == null || COMMAND_SEARCH.equals(recognizer.getSearchName())) return;
        startGoogleVoiceRecognize();
    }

    void startGoogleVoiceRecognize() {
        if (voiceGoogleInProgress) {
            initListener.initStarted();
        }
        final PackageManager pm = activity.getPackageManager();
        final List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0) {
            Toast toast = Toast.makeText(activity.getApplicationContext(), "Голосовой движок не установлен", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();
            initListener.initFailed();
        } else {
            initListener.initStarted();

            release();
            voiceGoogleInProgress = true;
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
            activity.startActivityForResult(intent, requestCode);

            delayHandler.postDelayed(new Runnable() {
                public void run() {
                    if (voiceGoogleInProgress) {
                        activity.finishActivity(requestCode);
                        voiceGoogleInProgress = false;
                        if (voiceKeywordEnable) {
                            recognizer.cancel();
                            recognizer.startListening(KWS_SEARCH);
                            voiceKeywordWorking = true;
                        }
                        initListener.initFailed();
                    }
                }
            }, VOICE_INPUT_TIMIOUT_MILLIS);
        }
    }

    protected void onGoogleVoiceRecognizeResult() {
        delayHandler.removeCallbacksAndMessages(null);
        if (voiceKeywordEnable && !voiceKeywordWorking) {
            voiceGoogleInProgress = false;
            recognizer.cancel();
            recognizer.startListening(KWS_SEARCH);
            voiceKeywordWorking = true;
        }
    }

    void destroy() {
        mSensorManager.unregisterListener(this);
        if (recognizer != null) recognizer.cancel();
        release();
    }

    void release() {
        if (voiceKeywordWorking) {
            quit();
            voiceKeywordWorking = false;
        }
    }

    void quit() {
        recognizer.cancel();
        recognizer.stop();
    }

    void speak(final String toSpeak) {
        textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
    }

    public interface InitListener {

        void initStarted();

        void initFailed();
    }
}
