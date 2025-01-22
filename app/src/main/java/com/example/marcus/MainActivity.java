package com.example.marcus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.ContentValues;
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
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WebSocketClientManager.WebSocketConnectionListener {

    private static final int REQUEST_OVERLAY_PERMISSION = 1;
    private static final int REQUEST_PHONE_STATE_PERMISSION = 2;
    private static final int REQUEST_SEND_SMS_PERMISSION = 3;
    private static final int REQUEST_READ_CONTACTS_PERMISSION = 4;
    private static final int REQUEST_WRITE_CONTACTS_PERMISSION = 5;
    private static final String TAG = "MainActivity";
    private static final String CHANNEL_ID = "MarcusChannel";

    private WebSocketClientManager webSocketClientManager;
    private Handler mainHandler;
    private NotificationManager notificationManager;
    private AudioManager audioManager;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    private String pendingContactName;
    private String pendingMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webSocketClientManager = WebSocketClientManager.getInstance();
        webSocketClientManager.setWebSocketConnectionListener(this);

        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        createNotificationChannel();

        // Request READ_PHONE_STATE permission if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},
                    REQUEST_PHONE_STATE_PERMISSION);
        } else {
            registerPhoneStateListener();
        }

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
    protected void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
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
        handleCommand(command.trim());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_STATE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, register phone state listener
                registerPhoneStateListener();
            } else {
                // Permission denied, show a notification
                showNotification("Phone state permission is required.");
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
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
        } else if (requestCode == REQUEST_SEND_SMS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, send the SMS
                sendSms(pendingContactName, pendingMessage);
            } else {
                // Permission denied, show a notification
                showNotification("SMS permission is required to send a message.");
            }
        } else if (requestCode == REQUEST_WRITE_CONTACTS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, add the contact
                addContact(pendingContactName);
            } else {
                // Permission denied, show a notification
                showNotification("Write contacts permission is required to add a contact.");
            }
        }
    }

    private void handleCommand(String command) {
        if (command != null) {
            mainHandler.post(() -> {
                try {
                    if (command.toLowerCase().startsWith("call ")) {
                        String contactName = command.substring(5).trim();
                        callContact(contactName);
                    } else if (command.toLowerCase().startsWith("sms ")) {
                        int thatIndex = command.toLowerCase().indexOf(" that ");
                        if (thatIndex != -1) {
                            String contactName = command.substring(4, thatIndex).trim();
                            String message = command.substring(thatIndex + 6).trim();
                            sendSms(contactName, message);
                        } else {
                            showNotification("Invalid SMS command format. Use 'sms <contact_name> that <message>'.");
                        }
                    } else if (command.toLowerCase().startsWith("add ")) {
                        String contactName = command.substring(4).trim();
                        addContact(contactName);
                    } else if (command.equalsIgnoreCase("end call") || command.equalsIgnoreCase("cut the call")) {
                        endCall();
                    } else if (command.equalsIgnoreCase("speaker on")) {
                        setSpeakerphoneOn(true);
                    } else {
                        String appName = command.substring(5).trim();
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
    private static final int SEND_SMS_PERMISSION_REQUEST_CODE = 103;
    private static final int WRITE_CONTACTS_PERMISSION_REQUEST_CODE = 104;

    private void openApp(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String packageName = null;

        // Check for common system apps with explicit intents
        switch (appName.toLowerCase()) {
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
            if (label.contains(appName.toLowerCase())) {
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
        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                // Check if column indexes are valid
                if (nameIndex == -1 || numberIndex == -1) {
                    Log.e(TAG, "Column not found in the cursor");
                    showNotification("Failed to find contact information.");
                    return;
                }

                if (cursor.moveToFirst()) {
                    String phoneNumber = cursor.getString(numberIndex);

                    // Make the call
                    Intent callIntent = new Intent(Intent.ACTION_CALL);
                    callIntent.setData(Uri.parse("tel:" + phoneNumber));
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(callIntent);
                    Log.d(TAG, "Calling " + contactName + "...");
                } else {
                    Log.e(TAG, "Contact not found: " + contactName);
                    showNotification("Contact not found: " + contactName);
                }
            } finally {
                cursor.close();
            }
        } else {
            Log.e(TAG, "Cursor is null");
            showNotification("Failed to query contacts.");
        }
    }

    private void sendSms(String contactName, String message) {
        // Check if permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = contactName;
            pendingMessage = message;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},
                    READ_CONTACTS_PERMISSION_REQUEST_CODE);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = contactName;
            pendingMessage = message;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS},
                    SEND_SMS_PERMISSION_REQUEST_CODE);
            return;
        }

        // Query the contacts database to find the phone number for the given contact name
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + contactName + "%"};

        Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();

            // Send the SMS
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d(TAG, "SMS sent to " + contactName + ": " + message);
            showNotification("SMS sent to " + contactName);
        } else {
            if (cursor != null) {
                cursor.close();
            }
            Log.e(TAG, "Contact not found: " + contactName);
            showNotification("Contact not found: " + contactName);
        }
    }

    private void addContact(String contactName) {
        // Check if permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = contactName;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CONTACTS},
                    WRITE_CONTACTS_PERMISSION_REQUEST_CODE);
            return;
        }

        // Create a new contact entry
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.google");
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "Google");

        Uri rawContactUri = getContentResolver().insert(ContactsContract.RawContacts.CONTENT_URI, values);
        long rawContactId = ContentUris.parseId(rawContactUri);

        // Insert the contact name
        values.clear();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contactName);
        getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);

        showNotification("Contact " + contactName + " added.");
        Log.d(TAG, "Contact " + contactName + " added.");
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
            // Ensure the audio mode is set to IN_CALL
            audioManager.setMode(AudioManager.MODE_IN_CALL);

            // Turn on the speakerphone
            audioManager.setSpeakerphoneOn(on);
            Log.d(TAG, "Speakerphone " + (on ? "enabled" : "disabled") + ".");
            showNotification("Speakerphone " + (on ? "enabled" : "disabled") + ".");

            // Add a brief delay to ensure the setting takes effect
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                boolean isSpeakerOn = audioManager.isSpeakerphoneOn();
                Log.d(TAG, "Speakerphone state after delay: " + (isSpeakerOn ? "enabled" : "disabled"));
                showNotification("Speakerphone state: " + (isSpeakerOn ? "enabled" : "disabled"));
                if (!isSpeakerOn) {
                    // Retry enabling the speakerphone
                    audioManager.setSpeakerphoneOn(on);
                    handler.postDelayed(() -> {
                        boolean isSpeakerOnRetry = audioManager.isSpeakerphoneOn();
                        Log.d(TAG, "Speakerphone state after retry: " + (isSpeakerOnRetry ? "enabled" : "disabled"));
                        showNotification("Speakerphone state after retry: " + (isSpeakerOnRetry ? "enabled" : "disabled"));
                    }, 1000);
                }
            }, 1000);
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

    private void registerPhoneStateListener() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        Log.d(TAG, "Incoming call ringing: " + phoneNumber);
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        Log.d(TAG, "Call off-hook");
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        Log.d(TAG, "Call idle");
                        break;
                }
            }
        };

        if (telephonyManager != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        }
    }
}