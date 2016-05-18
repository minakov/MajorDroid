package ru.galakart.majordroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import java.util.Timer;
import java.util.TimerTask;

public class LocationDetector implements LocationListener {
    @NonNull
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    @Nullable
    private static Location location = null;
    @Nullable
    private Timer timer;

    public LocationDetector(@NonNull final Context context, @NonNull final Listener listener) {
        if (!Prefs.isLocationDetectionOn(context)) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        final String provider = locationManager.getBestProvider(criteria, true);
        locationManager.requestLocationUpdates(provider, 10000, 0, this);

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                HANDLER.post(new Runnable() {
                    public void run() {
                        try {
                            if (location != null) {
                                listener.onLocationChanged(location);
                            }
                        } catch (@NonNull final Exception ignored) {
                        }
                    }
                });
            }
        };
        timer = new Timer();
        timer.schedule(task, 0, Prefs.getLocationDetectionPeriodMillis(context));
    }

    @Nullable
    static Location getLocation() {
        return location;
    }

    void cancel() {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public void onLocationChanged(Location newLocation) {
        location = newLocation;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    interface Listener {
        void onLocationChanged(@NonNull Location location);
    }
}