package com.ilay.englishkingdom.Receivers;

import android.app.NotificationChannel; // Used to create a notification channel (required on Android 8+)
import android.app.NotificationManager; // Used to send the notification to the phone
import android.app.PendingIntent; // Used to open the app when user taps the notification
import android.content.BroadcastReceiver; // Base class - this runs when AlarmManager wakes it up
import android.content.Context; // Needed to access system services and resources
import android.content.Intent; // Used to create the intent that opens the app
import android.os.Build; // Used to check the Android version

import androidx.core.app.NotificationCompat; // Used to build the notification

import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to check lastOpenDate
import com.ilay.englishkingdom.Activities.HomeActivity; // The screen that opens when user taps notification
import com.ilay.englishkingdom.R; // Used to reference app icon

import java.text.SimpleDateFormat; // Used to format today's date as a string
import java.util.Date; // Used to get today's date
import java.util.Locale; // Used for date formatting

public class ReminderReceiver extends BroadcastReceiver {
    // This class runs every day at 9:00 AM when AlarmManager wakes it up
    // It checks if the user already opened the app today
    // If yes - do nothing
    // If no - send a notification to remind them to practice

    // This ID is used to identify our notification channel
    // A channel is like a category for notifications - required on Android 8+
    private static final String CHANNEL_ID = "english_kingdom_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        // onReceive runs when AlarmManager fires at 9:00 AM
        // This is the entry point - everything starts here

        // If no user is logged in there is no point sending a notification
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Get today's date as a string e.g. "2026-03-17"
        // We compare this with lastOpenDate saved in Firestore
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());

        // Check Firestore to see if the user already opened the app today
        // We read the lastOpenDate field from the user's document
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return; // Document missing - skip

                    String lastOpenDate = document.getString("lastOpenDate");

                    if (today.equals(lastOpenDate)) {
                        // User already opened the app today - no notification needed
                        return;
                    }

                    // User has NOT opened the app today - send the reminder notification
                    sendNotification(context);
                });
    }

    private void sendNotification(Context context) {
        // This method builds and sends the actual notification

        // NotificationManager is the Android system service that handles notifications
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        // On Android 8+ we must create a notification channel before sending any notification
        // A channel groups notifications together and lets users control them in settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, // Unique ID for this channel
                    "Daily Reminder", // Name shown in phone settings
                    NotificationManager.IMPORTANCE_DEFAULT // Normal priority - makes a sound
            );
            manager.createNotificationChannel(channel); // Register the channel with Android
        }

        // PendingIntent tells Android what to do when user taps the notification
        // In our case it opens HomeActivity
        Intent openAppIntent = new Intent(context, HomeActivity.class);
        // FLAG_UPDATE_CURRENT means if this PendingIntent already exists just update it
        // FLAG_IMMUTABLE is required on Android 12+ for security
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0, // Request code - we only have one so 0 is fine
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification using NotificationCompat which works on all Android versions
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // The small icon shown in the status bar
                .setContentTitle("English Kingdom 👑") // The bold title of the notification
                .setContentText("You haven't practiced today yet! 📚 Don't break your streak!") // The message
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Normal priority
                .setContentIntent(pendingIntent) // Open app when tapped
                .setAutoCancel(true); // Dismiss notification when user taps it

        // Send the notification - the number 1 is just a unique ID for this notification
        manager.notify(1, builder.build());
    }
}