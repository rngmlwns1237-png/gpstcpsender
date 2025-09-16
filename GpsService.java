package com.example.gpstcpsender;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;

import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GpsService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private static final String CHANNEL_ID = "GpsTcpSenderChannel";
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 1234;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GPS TCP 송신 중")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(1, notification);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            for (Location location : result.getLocations()) {
                if (location != null) {
                    String nmea = generateGGA(location);
                    new Thread(() -> sendToServer(nmea)).start();
                }
            }
        }
    };

    private String generateGGA(Location loc) {
        String time = new SimpleDateFormat("HHmmss", Locale.US).format(new Date());
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();

        int latDeg = (int) lat;
        double latMin = (lat - latDeg) * 60;
        String latStr = String.format(Locale.US, "%02d%07.4f", latDeg, latMin);

        int lonDeg = (int) lon;
        double lonMin = (lon - lonDeg) * 60;
        String lonStr = String.format(Locale.US, "%03d%07.4f", lonDeg, lonMin);

        return String.format(Locale.US,
                "$GPGGA,%s,%s,N,%s,E,1,08,0.9,10.0,M,46.9,M,,",
                time, latStr, lonStr);
    }

    private void sendToServer(String data) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
            OutputStream os = socket.getOutputStream();
            os.write((data + "
").getBytes());
            os.flush();
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "GPS TCP Sender Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
