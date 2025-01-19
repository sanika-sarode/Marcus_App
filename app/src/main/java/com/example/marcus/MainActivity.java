package com.example.marcus;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WebSocketClientManager.WebSocketConnectionListener {

    private static final int REQUEST_OVERLAY_PERMISSION = 1;
    private static final String TAG = "MainActivity";
    private static final String CHANNEL_ID = "MarcusChannel";

    private WebSocketClientManager webSocketClientManager;
    private Handler mainHandler;
    private NotificationManager notificationManager;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webSocketClientManager = WebSocketClientManager.getInstance();
        webSocketClientManager.setWebSocketConnectionListener(this);

        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();

        // Check if overlay permission is granted
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            startFloatingService();
            minimizeApp();
        }

        // Log all installed apps for debugging purposes
        logInstalledApps();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService();
                minimizeApp();
            } else {
                showNotification("Overlay permission is required.");
            }
        }
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "WebSocket Connected");
    }

    @Override
    public void onCommandReceived(String command) {
        Log.d(TAG, "Command received: " + command);
        handleCommand(command);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the camera
                openApp("camera");
            } else {
                // Permission denied, show a notification
                showNotification("Camera permission is required to open the camera.");
            }
        } else if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, make the call
                String contactName = pendingContactName;
                pendingContactName = null;
                callContact(contactName);
            } else {
                // Permission denied, show a notification
                showNotification("Phone call permission is required to make a call.");
            }
        } else if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, read contacts and make the call
                String contactName = pendingContactName;
                pendingContactName = null;
                callContact(contactName);
            } else {
                // Permission denied, show a notification
                showNotification("Contacts permission is required to make a call.");
            }
        }
    }

    private void handleCommand(String command) {
        if (command != null) {
            mainHandler.post(() -> {
                try {
                    if (command.toLowerCase().startsWith("call ")) {
                        String contactName = command.toLowerCase().replace("call ", "").trim();
                        callContact(contactName);
                    } else if (command.toLowerCase().equals("end call")) {
                        endCall();
                    } else if (command.toLowerCase().equals("speaker on")) {
                        setSpeakerphoneOn(true);
                    } else {
                        String appName = command.toLowerCase().replace("open ", "").trim();
                        openApp(appName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling command: " + command, e);
                }
            });
        }
    }

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 101;
    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 102;
    private String pendingContactName;

    private void openApp(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String packageName = null;

        // Check for common system apps with explicit intents
        switch (appName) {
            case "camera":
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_REQUEST_CODE);
                } else {
                    Intent cameraIntent = new Intent("android.media.action.IMAGE_CAPTURE");
                    if (cameraIntent.resolveActivity(pm) != null) {
                        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(cameraIntent);
                        Log.d(TAG, "Camera launched successfully.");
                    }
                }
                return;

            case "phone":
                Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
                phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(phoneIntent);
                Log.d(TAG, "Phone dialer launched successfully.");
                return;

            case "chrome":
                packageName = "com.android.chrome";
                break;
        }

        // Fallback to general app search
        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase();
            if (label.contains(appName)) {
                packageName = app.packageName;
                break;
            }
        }

        // Launch the app by package name
        if (packageName != null) {
            try {
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Log.d(TAG, appName + " launched successfully.");
                } else {
                    Log.e(TAG, appName + " launch intent is null.");
                    showNotification("Failed to launch " + appName + ".");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error launching " + appName + ".", e);
                showNotification("Error handling " + appName + " request.");
            }
        } else {
            Log.e(TAG, "App not found: " + appName);
            showNotification("App not found: " + appName);
        }
    }

    private void callContact(String contactName) {
        // Check if permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = contactName;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},
                    READ_CONTACTS_PERMISSION_REQUEST_CODE);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = contactName;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
            return;
        }

        // Query the contacts database to find the phone number for the given contact name
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + contactName + "%"};

        Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (cursor != null && cursor.moveToFirst()) {
            String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();

            // Make the call
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
            Log.d(TAG, "Calling " + contactName + "...");
        } else {
            if (cursor != null) {
                cursor.close();
            }
            Log.e(TAG, "Contact not found: " + contactName);
            showNotification("Contact not found: " + contactName);
        }
    }

    private void endCall() {
        // Check if permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
            return;
        }

        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            try {
                Method endCallMethod = telecomManager.getClass().getDeclaredMethod("endCall");
                endCallMethod.setAccessible(true);
                endCallMethod.invoke(telecomManager);
                Log.d(TAG, "Call ended successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to end call.", e);
                showNotification("Failed to end call.");
            }
        } else {
            Log.e(TAG, "TelecomManager is null.");
            showNotification("Failed to end call.");
        }
    }

    private void setSpeakerphoneOn(boolean on) {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            audioManager.setSpeakerphoneOn(on);
            Log.d(TAG, "Speakerphone " + (on ? "enabled" : "disabled") + ".");
            showNotification("Speakerphone " + (on ? "enabled" : "disabled") + ".");
        } else {
            Log.e(TAG, "AudioManager is null.");
            showNotification("Failed to set speakerphone.");
        }
    }

    private void showNotification(String message) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Marcus Assists")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this drawable exists
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        notificationManager.notify(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Marcus Channel";
            String description = "Channel for Marcus notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingService.class);
        startService(serviceIntent);
    }

    private void minimizeApp() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    private void logInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString();
            String packageName = app.packageName;
            Log.d(TAG, "Installed app: " + label + " (" + packageName + ")");
        }
    }
}