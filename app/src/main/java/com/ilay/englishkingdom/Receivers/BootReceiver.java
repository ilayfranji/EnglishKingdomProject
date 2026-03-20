package com.ilay.englishkingdom.Receivers;

import android.app.AlarmManager; // Used to schedule the daily alarm
import android.app.PendingIntent; // Used to tell AlarmManager which receiver to wake up
import android.content.BroadcastReceiver; // Base class - runs when phone finishes booting
import android.content.Context; // Needed to access system services
import android.content.Intent; // Used to create the intent pointing to ReminderReceiver

import java.util.Calendar; // Used to set the alarm time to 9:00 AM

public class BootReceiver extends BroadcastReceiver {
    // When the phone restarts, AlarmManager loses all scheduled alarms
    // This class listens for the phone boot event and reschedules our daily alarm
    // Without this, the notification would stop working after every phone restart

    @Override
    public void onReceive(Context context, Intent intent) {
        // onReceive runs automatically when the phone finishes booting
        // We check the action to make sure it really is a boot event
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Phone just finished booting - reschedule the daily alarm
            scheduleAlarm(context);
        }
    }

    private void scheduleAlarm(Context context) {
        // This is the same alarm scheduling logic as in HomeActivity
        // We put it here so it also runs after phone restart

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Create the intent that points to ReminderReceiver
        // When the alarm fires, Android will start ReminderReceiver
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the alarm to fire at 9:00 AM today (or tomorrow if it's already past 9)
        Calendar calendar = Calendar.getInstance(); // Get current date and time
        calendar.set(Calendar.HOUR_OF_DAY, 9); // Set hour to 9
        calendar.set(Calendar.MINUTE, 0); // Set minute to 0
        calendar.set(Calendar.SECOND, 0); // Set second to 0

        // If 9:00 AM has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // Add 1 day
        }

        // setRepeating schedules the alarm to fire every 24 hours
        // AlarmManager.RTC_WAKEUP means wake up the phone even if it's sleeping
        // AlarmManager.INTERVAL_DAY = 24 hours in milliseconds
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(), // First fire time
                AlarmManager.INTERVAL_DAY, // Repeat every 24 hours
                pendingIntent
        );
    }
}