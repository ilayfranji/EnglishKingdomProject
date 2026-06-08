package com.ilay.englishkingdom.Receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Activities.HomeActivity;
import com.ilay.englishkingdom.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    //הקוד רץ כל יום בשעה 11:00

    //מייצרים ערוץ להתראה על תזכורות להיכנס לאפליקציה (חייב מגרסה 8+)
    private static final String CHANNEL_ID = "english_kingdom_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        //רץ לאחר שAlarmManager מפעיל אותו בשעה 11:00

        // אם אין משתמש מחובר לא נשלח הודעה
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();// שומרים את המזהה הייחודי של המשתמש

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());// יוצרים פורמט של תאריך
        String today = sdf.format(new Date());// שומר את התאריך של היום בפורמט שהוגדר

        //בודקים מתי המשתמש התחבר בפעם האחרונה לאפליקציה
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return; // אם הוא התחבר בפעם הראשונה נדלג על זה

                    String lastOpenDate = document.getString("lastOpenDate"); // שמירת התאריך כניסה אחרון לאפליקציה

                    if (today.equals(lastOpenDate)) {
                        // אם המשתמש כבר התחבר היום לאפליקציה אין צורך לשלוח הודעה
                        return;
                    }

                    //אם הוא לא פתח את האפליקציה קוראים למתודה sendNotification
                    sendNotification(context);
                });
    }

    private void sendNotification(Context context) {
        // בונים את ההתראה שנשלחת לטלפון כהודעה

        // שומרים את הגישה למנהל ההתראות של הטלפון
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        //בודק אם הגרסה היא 8+ , אם כן נשתמש בערוץ התראות שיצרנו
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, // הערוץ הייחודי של ההתראה
                    "Daily Reminder", // השם שמוצג בטלפון
                    NotificationManager.IMPORTANCE_DEFAULT // חשיבות ההתראה (לא דחופה, תעשה סאונד רגיל)
            );
            manager.createNotificationChannel(channel); // רושם את הערוץ הזה במערכת ההפעלה (ממול אנדרואיד)
        }

        //פותח את מסך הבית בלחיצה על ההודעה שנשלחה
        Intent openAppIntent = new Intent(context, HomeActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, // "תעודה" שמאפשרת לאפליקציה לפתוח את המסך
                0, // מזהה ייחודי להתראה
                openAppIntent, // האינטנט שכתבנו למעלה (פותח את מסך הבית)
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                // דגל 1 - מונע כפל התראות, אם יש כבר שעון במערכת והנתונים שונו אז הוא רק יתעדכן בנתונים החדשים
                // דגל 2 - יצירת אבטחה לאינטנט שאף אחד לא יוכל לפגוע בו ולהרוס אותו
        );

        // מעצב את ההודעה שתישלח
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)// קישור ההודעה שתישלח לערוץ המתאים לה
                .setSmallIcon(R.mipmap.ic_launcher) // הצגת האייקון בהודעה
                .setContentTitle("English Kingdom") // כותרת ההודעה
                .setContentText("You haven't practiced today yet! Don't break your streak! 🔥") // ההודעה עצמה
                .setContentIntent(pendingIntent) // מפעיל את התוכן שיש באינטנט בלחיצה על ההודעה (פותח את האפליקציה)
                .setAutoCancel(true); // להעלים את ההודעה כשלוחצים עליה

        manager.notify(1, builder.build());// יוצר את ההודעה והיא מוכנה לשליחה
        // נוטיפיי משגר את ההודעה למסך של המשתמש
        // מזהה ייחודי 1 להודעה
    }
}