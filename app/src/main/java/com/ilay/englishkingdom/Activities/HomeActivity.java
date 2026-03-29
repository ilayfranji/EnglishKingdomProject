package com.ilay.englishkingdom.Activities;

import android.app.AlarmManager; // Used to schedule the daily alarm
import android.app.PendingIntent; // Used to tell AlarmManager which receiver to wake up
import android.content.Context; // Used to get system services
import android.content.Intent; // Used to navigate between screens
import android.content.SharedPreferences; // Used to clear remember me data on logout
import android.os.Build; // Used to check Android version
import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used to reference UI elements
import android.widget.PopupMenu; // Used for the hamburger menu
import android.widget.TextView; // Used for text views

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used for the Learn and Practice cards
import androidx.security.crypto.EncryptedSharedPreferences; // Used to store data securely
import androidx.security.crypto.MasterKey; // Used to create the encryption key

import com.google.firebase.auth.FirebaseAuth; // Used to get the current user and sign out
import com.google.firebase.firestore.FirebaseFirestore; // Used to get user data from Firestore
import com.ilay.englishkingdom.R; // Used to reference XML resources
import com.ilay.englishkingdom.Receivers.ReminderReceiver; // Our reminder receiver
import com.ilay.englishkingdom.Utils.PermissionManager; // Handles all permission requests

import java.util.Calendar; // Used to set the alarm time

public class HomeActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvWelcome; // Welcome message showing the user's first name
    private TextView tvMenu; // Hamburger menu button
    private TextView tvQuote; // Random motivational quote shown below the cards
    private CardView cardLearn; // Card that opens the Learn screen
    private CardView cardPractice; // Card that opens the Practice screen

    // ==================== FIREBASE ====================

    private FirebaseAuth mAuth; // Used to get current user and sign out
    private FirebaseFirestore db; // Used to read user's first name from Firestore

    // ==================== SHARED PREFERENCES ====================

    private SharedPreferences sharedPreferences; // Used to clear remember me data on logout

    // ==================== QUOTES ====================

    private String[] quotes; // Array of motivational quotes loaded from strings.xml

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Load motivational quotes from strings.xml
        quotes = getResources().getStringArray(R.array.motivational_quotes);

        // Initialize Firebase connections
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize EncryptedSharedPreferences for secure local storage
        // We use this to store remember me data safely on the device
        try {
            // MasterKey is the encryption key - AES256_GCM is a very strong encryption method
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "EnglishKingdomPrefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Encrypts keys
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Encrypts values
            );
        } catch (Exception e) {
            // If encryption fails fall back to regular SharedPreferences
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE);
        }

        // Connect each variable to its XML view
        tvWelcome = findViewById(R.id.tvWelcome);
        tvMenu = findViewById(R.id.tvMenu);
        tvQuote = findViewById(R.id.tvQuote);
        cardLearn = findViewById(R.id.cardLearn);
        cardPractice = findViewById(R.id.cardPractice);

        loadUserName(); // Load the user's first name from Firestore
        showRandomQuote(); // Pick and show a random motivational quote
        scheduleReminder(); // Schedule the daily 11:00 AM reminder notification
        PermissionManager.askNotificationPermission(this); // Ask for notification permission on Android 13+
        // We use PermissionManager here instead of writing the permission code directly
        // because PermissionManager is our dedicated helper class for all permissions
        // keeping HomeActivity clean and putting all permission logic in one place

        // Hamburger menu click - shows profile, how to play and logout options
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });

        // Learn card click - opens the Learn screen
        cardLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LearnActivity.class));
            }
        });

        // Practice card click - opens the Practice screen
        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, PracticeActivity.class));
            }
        });
    }

    // ==================== LOAD USER NAME ====================

    private void loadUserName() {
        // Reads the user's first name from Firestore and shows it in the welcome message
        if (mAuth.getCurrentUser() == null) {
            // Guest user - show generic welcome message
            tvWelcome.setText("Welcome, Guest!");
            return;
        }

        String userId = mAuth.getCurrentUser().getUid(); // Get the logged in user's ID

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        tvWelcome.setText("Welcome, " + firstName + "!"); // Personalized welcome
                    } else {
                        tvWelcome.setText("Welcome!"); // Document missing - show generic welcome
                    }
                })
                .addOnFailureListener(e -> {
                    tvWelcome.setText("Welcome!"); // Firestore failed - show generic welcome
                });
    }

    // ==================== RANDOM QUOTE ====================

    private void showRandomQuote() {
        // Picks a random motivational quote from our quotes array and shows it
        // Math.random() returns a number between 0 and 1
        // Multiplying by quotes.length gives us a random index in the array
        int randomIndex = (int) (Math.random() * quotes.length);
        tvQuote.setText(quotes[randomIndex]);
    }

    // ==================== MENU ====================

    private void showMenu(View v) {
        // Shows the hamburger popup menu with Profile, How to Play and Logout options
        PopupMenu popupMenu = new PopupMenu(this, v);
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_profile) {
                // Open profile screen
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                return true;

            } else if (id == R.id.menu_how_to_play) {
                // Open how to play screen
                startActivity(new Intent(HomeActivity.this, HowToPlayActivity.class));
                return true;

            } else if (id == R.id.menu_logout) {
                logoutUser(); // Sign out and go back to login screen
                return true;
            }
            return false;
        });

        popupMenu.show(); // Display the menu
    }

    // ==================== LOGOUT ====================

    private void logoutUser() {
        // Signs out the user and clears all locally saved data
        mAuth.signOut(); // Sign out from Firebase

        // Clear remember me data from local storage
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("rememberMe", false);
        editor.remove("email");
        editor.apply();

        // Go back to the login screen
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close HomeActivity so user can't press back to return here
    }

    // ==================== SCHEDULE REMINDER ====================

    private void scheduleReminder() {
        // Schedules a daily alarm that fires at 11:00 AM
        // If the user restarts their phone just before 11 AM and it comes back on after 11 AM
        // BootReceiver will fire the alarm immediately when the phone finishes booting
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // This intent points to ReminderReceiver - it runs when the alarm fires
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the target time to today at 11:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 11); // 11 AM
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If 11:00 AM already passed today schedule for tomorrow
        // For example if the user opens the app at 2:00 PM we don't fire immediately
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // Move to tomorrow at 11:00 AM
        }

        // Android 12+ requires permission to schedule exact alarms
        // If permission is not granted we fall back to setAndAllowWhileIdle()
        // which is not perfectly exact but still fires when the app is closed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                // Permission granted - schedule exact alarm so it fires at exactly 11:00 AM
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                // Permission not granted - use non-exact fallback
                // Still fires when app is closed but might be a few minutes late
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            // Android 11 and below - always exact no permission needed
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
}