package com.ilay.englishkingdom.Receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class BootReceiver extends BroadcastReceiver {
    // מחלקה שבעזרתה אנחנו קובעים ומגדירים חדש את התזכורת היומית לאחר שהטלפון נכבה (ריסטארט)

    @Override
    public void onReceive(Context context, Intent intent) {
        // פעולה שרצה באופן אוטומטי כשהטלפון נדלק
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // בדיקה אם הטלפון נדלק מחדש, אם כן יוצרים שוב את התזכורת שנמחקה
            scheduleAlarm(context);
        }
    }

    private void scheduleAlarm(Context context) {
        // יצירת תזכורת חדשה

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);// מעניק גישה למערכת השעונים של הטלפון

        // קריאה למחלקה ReminderReceiver שמטפלת בשליחת ההודעות
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,// מזהה ייחודי לתזכורת היומית
                intent,// האינטנט שיצרנו למעלה שמפעיל את הקוד ברמיינדר רסיבר
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                // דגל 1 - מונע כפל התראות, אם יש כבר שעון במערכת והנתונים שונו אז הוא רק יתעדכן בנתונים החדשים
                // דגל 2 - יצירת אבטחה לאינטנט שאף אחד לא יוכל לפגוע בו ולהרוס אותו
        );

        Calendar calendar = Calendar.getInstance(); // שמירת הזמן הנוכחי
        calendar.set(Calendar.HOUR_OF_DAY, 11); // הגדרת השעון לשעה 11:00 בדיוק
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // אם כבר עברה השעה 11:00 באותו יום נשלח הודעה ביום שאחרי
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // מוסיפים יום אחד
        }


        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,// שלח תזכורת אפילו אם הטלפון במצב שינה והמסך כבוי
                calendar.getTimeInMillis(), // הזמן המדויק שהתזכורת תוצג
                AlarmManager.INTERVAL_DAY, // חוזר על עצמו כל 24 שעות
                pendingIntent // "ההוראות" שהתזכורת צריכה לעשות בכל פעם שמגיע הזמן לשלוח תזכורת
        );
    }
}