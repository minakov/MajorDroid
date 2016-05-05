package ru.galakart.majordroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

//import java.util.LinkedList;
//import java.util.Queue;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_VOICE = 1234;
    private static final int REQUEST_CODE_MESSAGE = 1236;
    private static final int REQUEST_CODE_QR = 1237;
    private final int TCP_SERVER_PORT = 7999; //Define the server port
    ProgressDialog dialog;
    @Nullable
    private VoiceRecognizer voiceRecognizer;
    private ReloadWebView reloadWebViewTimer;
    private WebView mWebView;
    private WebView webPost;
    private ProgressBar Pbar;
    private int update_period = 0;
    private String localURL = "", globalURL = "", serverURL = "", login = "",
            passw = "", wifiHomeNet = "", pathHomepage = "", pathVoice = "", pathQR = "", pathGps = "";
    private String tmpDostupAccess = "";
    private String tmpAdressAccess = "";
    private String uploadURL = "";
    private String faceURL = "";
    private boolean outAccess = false;
    private boolean firstLoad = false;
    private String gpsTimeOut;
    private boolean reloadTimerOn = false;
    private Timer timer;
    private TimerTask doAsynchronousTask;
    private boolean timerOn = false;
    private boolean faceDetectionEnable = false;
    private boolean faceDetectionWorking = false;
    private boolean disableFacePost = false;
    private MediaPlayer mediaPlayer;
    private String qrCameraSet;
    private Camera mCamera;
    private SurfaceView cameraSurface;
    private int NumberOfFacesDetected = 0;
    private int serverResponseCode = 0;
    private String activityResultString = "";


    private FaceDetectionListener faceDetectionListener = new FaceDetectionListener() {
        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            if (faces.length != NumberOfFacesDetected) {
                Log.d("onFaceDetection", "Number of Faces:" + faces.length);
                NumberOfFacesDetected = faces.length;
                String resURL = faceURL + "&faces=" + Integer.toString(NumberOfFacesDetected);
                //Log.d("onFaceDetection", "Loading face URL:" + resURL);
                if (!disableFacePost) {
                    webPost.loadUrl(resURL);
                }

                if (NumberOfFacesDetected > 0) {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Faces detected: " + Integer.toString(NumberOfFacesDetected), Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.BOTTOM, 0, 0);
                    toast.show();
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Pbar = (ProgressBar) findViewById(R.id.pB1);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setWebViewClient(new MajorDroidWebViewer());
        webPost = (WebView) findViewById(R.id.webPost);

        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100
                        && Pbar.getVisibility() == ProgressBar.INVISIBLE) {
                    Pbar.setVisibility(ProgressBar.VISIBLE);
                }
                Pbar.setProgress(progress);
                if (progress == 100) {
                    Pbar.setVisibility(ProgressBar.INVISIBLE);
                }
            }
        });

        final Handler handler = new Handler();
        timer = new Timer();
        doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            gpsSend();
                        } catch (Exception e) {
                        }
                    }
                });
            }
        };

        voiceRecognizer = new VoiceRecognizer(this, REQUEST_CODE_VOICE, new VoiceRecognizer.InitListener() {
            @Override
            public void initStarted() {
                turnOffFaceDetectionCamera();
            }

            @Override
            public void initFailed() {
                initiateFaceDetectionCamera();
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        gpsTimeOut = prefs.getString(getString(R.string.gps_period), "5");
        if ((prefs.getString(getString(R.string.gps_switch), "Выкл").equals("Вкл")) && (!timerOn)) {
            timer.schedule(doAsynchronousTask, 0, Long.parseLong(gpsTimeOut) * 60 * 1000);
            timerOn = true;
        }

        //New thread to listen to incoming connections
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //Create a server socket object and bind it to a port
                    ServerSocket socServer = new ServerSocket(TCP_SERVER_PORT);
                    //Create server side client socket reference
                    Socket socClient = null;
                    //Infinite loop will listen for client requests to connect
                    while (true) {
                        //Accept the client connection and hand over communication to server side client socket
                        socClient = socServer.accept();
                        //For each client new instance of AsyncTask will be created
                        ServerAsyncTask serverAsyncTask = new ServerAsyncTask();
                        //Start the AsyncTask execution
                        //Accepted client socket object will pass as the parameter
                        serverAsyncTask.execute(socClient);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "Activity pause");
        super.onPause();
        if (voiceRecognizer != null) {
            voiceRecognizer.release();
        }
        turnOffFaceDetectionCamera();
    }

    @Override
    protected void onDestroy() {
        if (voiceRecognizer != null) {
            voiceRecognizer.destroy();
        }
        turnOffFaceDetectionCamera();
        if (mCamera != null) mCamera = null;

        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                Intent ab = new Intent(this, AboutActivity.class);
                startActivity(ab);
                return true;

            case R.id.action_videorecord:
                extCommand("videomessage");
                return true;

            case R.id.action_quit:
                if (timerOn) {
                    timer.cancel();
                }
                if (voiceRecognizer != null) {
                    voiceRecognizer.quit();
                }
                finish();
                return true;

            case R.id.action_settings:
                Intent st = new Intent(this, Prefs.class);
                startActivity(st);
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        int goToHome = 0;

        if (requestCode == REQUEST_CODE_VOICE) {
            if (voiceRecognizer != null) {
                voiceRecognizer.onGoogleVoiceRecognizeResult();
            }
            if (resultCode == RESULT_OK) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                voiceCommand(matches.get(0));
            }
        }

        if (requestCode == REQUEST_CODE_MESSAGE && resultCode == RESULT_OK) {
            Log.d(TAG, "Got REQUEST_CODE_MESSAGE");

            activityResultString = data.getStringExtra("filename");

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getString(getString(R.string.homeaftervideo), "off").equals("on")) {
                goToHome = 1;
            }

            dialog = ProgressDialog.show(MainActivity.this, "", "Uploading file...", true);

            new Thread(new Runnable() {
                public void run() {
                    uploadFile(activityResultString);
                }
            }).start();
        }

        if (requestCode == REQUEST_CODE_QR) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

            if (scanResult != null) {
                // handle scan result
                String contantsString = scanResult.getContents() == null ? "0" : scanResult.getContents();
                if (contantsString.equalsIgnoreCase("0")) {
                    Toast.makeText(this, "No code scanned", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, contantsString, Toast.LENGTH_LONG).show();
                    if (contantsString.startsWith("http")) {
                        mWebView.loadUrl(contantsString);
                    } else {
                        mWebView.loadUrl(getServerURL(serverURL) + pathQR + contantsString);
                    }
                }
            } else {
                Toast.makeText(this, "Problem to secan the barcode.", Toast.LENGTH_LONG).show();
            }
        }


        if (resultCode != RESULT_OK) {
            Log.d(TAG, "Activity finished (not OK)");
        }

        initiateFaceDetectionCamera();

        if (goToHome == 1) {
            imgb_home_click(null);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "Activity resume");
        super.onResume();
        loadHomePage(0);
    }

    private void loadHomePage(int immediateLoad) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        localURL = prefs.getString(getString(R.string.localUrl), "");
        globalURL = prefs.getString(getString(R.string.globalUrl), "");
        pathHomepage = prefs.getString(getString(R.string.path_homepage), "");
        pathVoice = prefs.getString(getString(R.string.path_voice), "");
        pathQR = prefs.getString(getString(R.string.path_qr), "");
        pathGps = prefs.getString(getString(R.string.path_tracker), "");
        login = prefs.getString(getString(R.string.login), "");
        passw = prefs.getString(getString(R.string.passw), "");
        String dostup = prefs.getString(getString(R.string.dostup), "");
        String vid = prefs.getString(getString(R.string.vid), "");
        String wifiHomeNet = prefs.getString("wifihomenet", "");
        String wifiToast = "";
        TableLayout tl = (TableLayout) findViewById(R.id.homeTableLay);


        if (vid.contains("Обычный")) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            tl.setVisibility(View.VISIBLE);
        }

        if (vid.contains("Полноэкранный")) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            tl.setVisibility(View.VISIBLE);
        }

        if (vid.contains("Полноэкранный (без панели кнопок)")) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            tl.setVisibility(View.GONE);
        }

        if (!dostup.equals(tmpDostupAccess))
            firstLoad = false;

        if (dostup.contains("Локальный")) {
            outAccess = false;
            serverURL = localURL;
            wifiToast = "";
            tmpDostupAccess = dostup;

        } else if (dostup.contains("Глобальный")) {
            outAccess = true;
            serverURL = globalURL;
            wifiToast = "";
            tmpDostupAccess = dostup;

        } else if (dostup.contains("Автоматический")) {
            if (wifiHomeNet != "") {
                if (isConnectedToSSID(wifiHomeNet)) {
                    outAccess = false;
                    serverURL = localURL;
                    wifiToast = " (SSID: " + wifiHomeNet + ")";
                } else {
                    outAccess = true;
                    serverURL = globalURL;
                    wifiToast = " (не в домашней сети)";
                }
            } else {
                outAccess = false;
                serverURL = localURL;
                wifiToast = " (не задана домашняя wifi-сеть)";
            }
            tmpDostupAccess = dostup;
        }

        uploadURL = getServerURL(serverURL) + prefs.getString(getString(R.string.path_video), "");
        faceURL = getServerURL(serverURL) + prefs.getString(getString(R.string.path_face), "");

        if (!serverURL.equals(tmpAdressAccess))
            firstLoad = false;

        if ((!firstLoad) || (immediateLoad == 1)) {
            Toast toast = Toast.makeText(getApplicationContext(), "",
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            if (outAccess)
                toast.setText("Глобальный доступ" + wifiToast);
            else
                toast.setText("Локальный доступ" + wifiToast);
            if (serverURL == "") {
                toast.setText("Не задан адрес сервера в настройках");
                toast.show();
            } else {
                mWebView.loadUrl(getServerURL(serverURL) + pathHomepage);

                // потом использовать reload();


                firstLoad = true;
                if (!serverURL.equals(tmpAdressAccess))
                    toast.show();
                tmpAdressAccess = serverURL;
            }
        }

        update_period = Integer.parseInt(prefs.getString(getString(R.string.homepage_period), "0"));

        if (reloadTimerOn) {
            //reloadTimer.cancel();
            reloadWebViewTimer.cancelTimer();
        }

        if (update_period > 0) {
            Log.i(TAG, "Force reload timer started " + update_period);
            reloadTimerOn = true;
            /*
            reloadTimer = new Timer();
			reloadTimer.schedule(new TimerTask() {
			    @Override
			    public void run() {
	 			    Log.i(TAG, "Force reload task activated");
	 			    mWebView.loadUrl(getServerURL(serverURL) + pathHomepage);

			    }
			}, update_period * 60 * 1000L, update_period * 60 * 1000L);
			*/
            reloadWebViewTimer = new ReloadWebView(this, update_period * 60, mWebView);
        } else {
            reloadTimerOn = false;
        }


        gpsTimeOut = prefs.getString(getString(R.string.gps_period), "5");
        if ((prefs.getString(getString(R.string.gps_switch), "Выкл").equals("Вкл")) && (!timerOn)) {
            timer.schedule(doAsynchronousTask, 0,
                    Long.parseLong(gpsTimeOut) * 60 * 1000);
            timerOn = true;
        } else if ((prefs.getString(getString(R.string.gps_switch), "Выкл").equals("Выкл")) && (timerOn)) {
            timer.cancel();
            timerOn = false;
        }

        voiceRecognizer = new VoiceRecognizer(this, REQUEST_CODE_VOICE, new VoiceRecognizer.InitListener() {
            @Override
            public void initStarted() {
                turnOffFaceDetectionCamera();
            }

            @Override
            public void initFailed() {
                initiateFaceDetectionCamera();
            }
        });
        initiateFaceDetectionCamera();
    }

    private void initiateFaceDetectionCamera() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);


        if (prefs.getString(getString(R.string.facedetection), "off").equals("on")) {
            faceDetectionEnable = true;
            if (!faceDetectionWorking) {
                int cameraId = 0;
                if (prefs.getString(getString(R.string.facecamera_switch), "0").equals("1")) {
                    cameraId = getFrontFacingCameraId();
                } else {
                    cameraId = getBackFacingCameraId();
                }
                Log.d(TAG, "Initiating camera " + Integer.toString(cameraId));
                mCamera = Camera.open(cameraId);
                cameraSurface = new SurfaceView(this);
                if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
                    try {
                        mCamera.setPreviewDisplay(cameraSurface.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mCamera.setFaceDetectionListener(faceDetectionListener);
                    mCamera.startPreview();
                    mCamera.startFaceDetection();
                    Log.d(TAG, "Face detection started for camera " + Integer.toString(cameraId));
                    faceDetectionWorking = true;
                } else {
                    Toast.makeText(this, "Face detection is not supported for camera " + Integer.toString(cameraId), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Face detection is not supported for camera " + Integer.toString(cameraId));
                    faceDetectionWorking = false;
                    faceDetectionEnable = false;
                    mCamera.release();
                }
            }
        } else {
            Log.d(TAG, "Facedetection disabled");
            faceDetectionEnable = false;
            turnOffFaceDetectionCamera();
        }

    }

    private void turnOffFaceDetectionCamera() {
        if (faceDetectionWorking) {
            mCamera.setFaceDetectionListener(null);
            mCamera.setErrorCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            faceDetectionWorking = false;
            Log.d(TAG, "Facedetection stopped");
        }
    }

    private void extCommand(String command) {
        if (command.equals("hi") || command.equals("voice")) {
            imgb_voice_click(null);
        }
        if (command.equals("settings")) {
            imgb_settings_click(null);
        }
        if (command.equals("home")) {
            imgb_home_click(null);
        }
        if (command.equals("pult")) {
            imgb_pult_click(null);
        }
        if (command.equals("videomessage")) {
            if (voiceRecognizer != null) {
                voiceRecognizer.release();
            }
            turnOffFaceDetectionCamera();
            startActivityForResult(new Intent(this, VideoRecordActivity.class), REQUEST_CODE_MESSAGE);
        }
        if (command.equals("qrscan")) {
            imgb_qr_click(null);
        }
        if (command.startsWith("url:")) {
            mWebView.loadUrl(command.replace("url:", ""));
        }
        if (command.startsWith("tts:")) {
            voiceRecognizer.speak(command.replace("tts:", ""));
        }
        if (command.equals("pause")) {
            if (mediaPlayer.isPlaying())
                mediaPlayer.pause();
        }
        if (command.startsWith("play:")) {
            String url = command;
            url = url.replace("play:", "");
            try {
                mediaPlayer.setDataSource(url);
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        Toast toast = Toast.makeText(getApplicationContext(), command, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    private String getServerURL(String url) {
        String resURL;
        if (url.startsWith("http")) {
            resURL = url;
        } else {
            resURL = "http://" + url;
        }
        return resURL;
    }

    private void voiceCommand(String command) {
        disableFacePost = true;
        webPost.loadUrl(getServerURL(serverURL) + pathVoice + command);

        Toast toast = Toast.makeText(getApplicationContext(), command, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();

        new android.os.Handler().postDelayed(new Runnable() {
            public void run() {
                disableFacePost = false;
            }
        }, 3000);

/*
        try{
	           HttpClient httpclient = new DefaultHttpClient();
	           HttpGet request = new HttpGet();
	           String authorizationString = "Basic " + Base64.encodeToString((login + ":" + passw).getBytes(),Base64.NO_WRAP);
	           request.setHeader("Authorization", authorizationString);
	           URI website = new URI(getServerURL(serverURL) + pathVoice + URLEncoder.encode(command,"UTF-8"));
	           request.setURI(website);
	           httpclient.execute(request);
	       }catch(Exception e){
	           Log.e(TAG, "Error in http connection "+e.toString());
	       }
	*/

    }

    public void imgb_home_click(View v) {
        loadHomePage(1);
    }

    private int getFrontFacingCameraId() {
        int cameraCount = 0;
        int cam = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = camIdx;
                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }
        return cam;
    }

    private int getBackFacingCameraId() {
        int cameraCount = 0;
        int cam = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    cam = camIdx;
                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }
        return cam;
    }

    public void imgb_qr_click(View v) {
        if (voiceRecognizer != null) {
            voiceRecognizer.release();
        }
        turnOffFaceDetectionCamera();

        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        qrCameraSet = prefs.getString(getString(R.string.qrcamera_switch), "0");
        if (!qrCameraSet.equals("0")) {
            Log.d(TAG, "Searching front qr camera " + qrCameraSet);
            int camID = getFrontFacingCameraId();
            Log.d(TAG, "Camera qr found " + Integer.toString(camID));
            integrator.setCameraId(Integer.toString(camID));
        } else {
            Log.d(TAG, "Searching back qr camera " + qrCameraSet);
            int camID = getBackFacingCameraId();
            Log.d(TAG, "Camera qr found " + Integer.toString(camID));
            integrator.setCameraId(Integer.toString(camID));
        }

        integrator.initiateScan();


    }

    public void imgb_voice_click(View v) {
        if (voiceRecognizer != null) {
            voiceRecognizer.startGoogleVoiceRecognize();
        }
    }

    public void imgb_pult_click(View v) {
        Intent j = new Intent(this, ControsActivity.class);
        startActivity(j);
        gpsSend();
    }

    public void imgb_settings_click(View v) {
        Intent i = new Intent(this, Prefs.class);
        startActivity(i);
    }

    boolean isConnectedToSSID(String t) {
        try {
            WifiManager wifiMgr = (WifiManager) this
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            if (wifiInfo.getSSID().equals(t))
                return true;
        } catch (Exception a) {
        }
        return false;
    }

    private void gpsSend() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        double latitude = 0, longitude = 0, altitude = 0, speed = 0, accuracy = 0;
        String provider = "";
        LocationManager mlocManager = null;
        LocationListener mlocListener;
        mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mlocListener = new MyLocationListener();
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        mlocManager.requestLocationUpdates(mlocManager.getBestProvider(criteria, true), 10000, 0,
                mlocListener);

//		if (mlocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        if (MyLocationListener.latitude > 0) {
            latitude = MyLocationListener.latitude;
            longitude = MyLocationListener.longitude;
            altitude = MyLocationListener.altitude;
            speed = MyLocationListener.speed;
            accuracy = MyLocationListener.accuracy;
            provider = MyLocationListener.provider;
        }
//		}
//		else {
//			if (mlocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
//				if (MyLocationListener.latitude > 0) {
//					latitude = MyLocationListener.latitude;
//					longitude = MyLocationListener.longitude;
//					altitude = MyLocationListener.altitude;
//					speed = MyLocationListener.speed;
//					accuracy = MyLocationListener.accuracy;
//					provider = MyLocationListener.provider;
//				}
//			}
//		}

        String deviceid = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);
        String battlevel = Integer.toString(batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
        String gpsUrl = getServerURL(serverURL) + pathGps + "?";

        if (latitude != 0)
            gpsUrl += "latitude=" + latitude + "&";
        if (longitude != 0)
            gpsUrl += "longitude=" + longitude + "&";
        if (altitude != 0)
            gpsUrl += "altitude=" + altitude + "&";
        if (provider != "")
            gpsUrl += "provider=" + provider + "&";
        if (speed != 0)
            gpsUrl += "speed=" + speed + "&";
        if (battlevel != "")
            gpsUrl += "battlevel=" + battlevel + "&";
        if (deviceid != "")
            gpsUrl += "deviceid=" + deviceid + "&";
        if (accuracy != 0)
            gpsUrl += "accuracy=" + accuracy + "&";

        if (serverURL != "")
            webPost.loadUrl(gpsUrl);
    }

    public int uploadFile(String sourceFileUri) {
        String fileName = sourceFileUri;

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(sourceFileUri);

        if (!sourceFile.isFile()) {

            dialog.dismiss();

            Log.e("uploadFile", "Source File not exist :"
                    + sourceFileUri);


            return 0;

        } else {
            try {

                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL(uploadURL);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("uploaded_file", fileName);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                        + fileName + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {

                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                Log.i(TAG, "HTTP Response is : "
                        + serverResponseMessage + ": " + serverResponseCode);

                if (serverResponseCode == 200) {

                    runOnUiThread(new Runnable() {
                        public void run() {

                            String msg = "File Upload Completed.";

                            Toast.makeText(MainActivity.this, "File Upload Complete.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                //close the streams //
                fileInputStream.close();
                dos.flush();
                dos.close();

                File file = new File(sourceFileUri);
                boolean deleted = file.delete();

            } catch (MalformedURLException ex) {

                dialog.dismiss();
                ex.printStackTrace();

                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "MalformedURLException",
                                Toast.LENGTH_SHORT).show();
                    }
                });

                Log.e(TAG, "error: " + ex.getMessage(), ex);
            } catch (Exception e) {

                dialog.dismiss();
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Got Exception : see logcat ",
                                Toast.LENGTH_SHORT).show();
                    }
                });
                Log.e(TAG, "Exception : "
                        + e.getMessage(), e);
            }
            dialog.dismiss();
            return serverResponseCode;

        } // End else block
    }

    public static class MyLocationListener implements LocationListener {

        public static double latitude = 0;
        public static double longitude = 0;
        public static double altitude = 0;
        public static double speed = 0;
        public static double accuracy = 0;
        public static String provider = "";

        @Override
        public void onLocationChanged(Location loc) {
            loc.getLatitude();
            loc.getLongitude();
            latitude = loc.getLatitude();
            longitude = loc.getLongitude();
            altitude = loc.getAltitude();
            speed = loc.getSpeed();
            accuracy = loc.getAccuracy();
            provider = loc.getProvider();
        }

        @Override
        public void onProviderDisabled(String provider) {
            // print "Currently GPS is Disabled";
        }

        @Override
        public void onProviderEnabled(String provider) {
            // print "GPS got Enabled";
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // TODO Auto-generated method stub
        }
    }

    protected class ReloadWebView extends TimerTask {
        Activity context;
        Timer timer;
        WebView wv;

        public ReloadWebView(Activity context, int seconds, WebView wv) {
            this.context = context;
            this.wv = wv;

            timer = new Timer();
            /* execute the first task after seconds */
            timer.schedule(this,
                    seconds * 1000,  // initial delay
                    seconds * 1000); // subsequent rate
        }

        public void cancelTimer() {
            timer.cancel();
        }

        @Override
        public void run() {
            if (context == null || context.isFinishing()) {
                // Activity killed
                this.cancel();
                return;
            }

            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    wv.reload();
                }
            });
        }
    }

    /**
     * AsyncTask which handles the commiunication with clients
     */
    public class ServerAsyncTask extends AsyncTask<Socket, Void, String> {
        //Background task which serve for the client
        @Override
        protected String doInBackground(Socket... params) {
            String result = null;
            //Get the accepted socket object
            Socket mySocket = params[0];
            try {
                //Get the data input stream comming from the client
                InputStream is = mySocket.getInputStream();
                //Get the output stream to the client
                PrintWriter out = new PrintWriter(mySocket.getOutputStream(), true);
                //Write data to the data output stream
                //out.println("Hello from server");
                //Buffer the data input stream
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(is));
                //Read the contents of the data buffer
                result = br.readLine();
                //Close the client connection
                mySocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String s) {
            //After finishing the execution of background task data will be write the text view
            //tvClientMsg.setText(s);
            extCommand(s);
        }
    }

    public class MajorDroidWebViewer extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {


            if (url.startsWith("app://")) {

                Toast toast = Toast.makeText(getApplicationContext(),
                        url, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.BOTTOM, 0, 0);
                toast.show();
                String cmd = url;
                cmd = cmd.replace("app://", "");
                extCommand(cmd);
            } else {
                view.loadUrl(url);
            }
            return true;
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                                              HttpAuthHandler handler, String host, String realm) {
            if (outAccess)
                handler.proceed(login, passw);
        }

        // @Override
        // public void onPageStarted(WebView view, String url, Bitmap favicon) {
        // super.onPageStarted(view, url, favicon);
        //
        // }
    }

}
/*
 * На будущее: 1. Использовать reload(); при обновлении браузера 2. Использовать
 * окно браузера для вывода возможных ошибок, вот так String summary =
 * "<html><body>You scored <b>192</b> points.</body></html>";
 * webview.loadData(summary, "text/html", null);
 */