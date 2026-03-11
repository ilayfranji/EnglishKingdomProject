package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to navigate between screens
import android.content.SharedPreferences; // Used to clear Remember Me on logout
import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used to reference UI elements
import android.widget.PopupMenu; // Used for the hamburger menu popup
import android.widget.TextView; // Used for text views

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used for the card views
import androidx.security.crypto.EncryptedSharedPreferences; // Used to encrypt our local storage
import androidx.security.crypto.MasterKey; // Used to create the encryption key

import com.google.firebase.auth.FirebaseAuth; // Used to get current user and logout
import com.google.firebase.firestore.FirebaseFirestore; // Used to get user data from Firestore
import com.ilay.englishkingdom.R; // Used to reference our XML resources

public class HomeActivity extends AppCompatActivity {

    // Declare all UI elements
    private TextView tvWelcome; // The welcome message text
    private TextView tvMenu; // The hamburger menu button
    private TextView tvQuote; // The motivational quote text
    private CardView cardLearn; // The Learn card
    private CardView cardPractice; // The Practice card

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private FirebaseFirestore db; // Our connection to Firestore database
    private SharedPreferences sharedPreferences; // Our encrypted local storage

    private String[] quotes; // Array of motivational quotes loaded from strings.xml

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // getResources().getStringArray() loads the string-array from strings.xml
        // R.array.motivational_quotes is the ID of our quotes array in strings.xml
        quotes = getResources().getStringArray(R.array.motivational_quotes);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize EncryptedSharedPreferences to store data safely
        try {
            // MasterKey is the encryption key - AES256_GCM is a very strong encryption method
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            // EncryptedSharedPreferences works like SharedPreferences but encrypts everything
            sharedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "EnglishKingdomPrefs", // Name of our local storage file
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Encrypts the keys
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Encrypts the values
            );
        } catch (Exception e) {
            // If encryption fails fall back to regular SharedPreferences
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE);
        }

        // Connect each Java variable to its XML view
        tvWelcome = findViewById(R.id.tvWelcome);
        tvMenu = findViewById(R.id.tvMenu);
        tvQuote = findViewById(R.id.tvQuote);
        cardLearn = findViewById(R.id.cardLearn);
        cardPractice = findViewById(R.id.cardPractice);

        loadUserName(); // Load user's name from Firestore
        showRandomQuote(); // Show a random motivational quote

        // Menu button click - show the popup menu
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v); // Pass the view so the menu appears near it
            }
        });

        // Learn card click - navigate to LearnActivity
        // Previously this was WRONG because the click listener was nested inside itself!
        // The outer click set a new click listener instead of actually navigating
        // Now it correctly navigates to LearnActivity when clicked
        cardLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LearnActivity.class)); // Go to LearnActivity
            }
        });

        // Practice card click - TODO: navigate to Practice screen
        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Go to Practice screen (we will build this later)
            }
        });
    }

    private void loadUserName() { // Loads the user's first name from Firestore
        if (mAuth.getCurrentUser() == null) { // If user is a guest (not logged in)
            tvWelcome.setText("Welcome, Guest! 👋"); // Show generic welcome
            return; // Stop method - no need to fetch from Firestore
        }

        String userId = mAuth.getCurrentUser().getUid(); // Get the logged in user's unique ID

        db.collection("users")
                .document(userId) // Get the document with this user's ID
                .get() // Fetch from Firestore
                .addOnSuccessListener(document -> {
                    if (document.exists()) { // If document exists in Firestore
                        String firstName = document.getString("firstName"); // Get firstName field
                        tvWelcome.setText("Welcome, " + firstName + "! 👋"); // Set welcome with real name
                    } else {
                        tvWelcome.setText("Welcome! 👋"); // Fallback if document doesn't exist
                    }
                })
                .addOnFailureListener(e -> {
                    tvWelcome.setText("Welcome! 👋"); // Fallback if Firestore fails
                });
    }

    private void showRandomQuote() { // Picks and shows a random motivational quote
        // Math.random() returns a number between 0.0 and 1.0
        // Multiplying by quotes.length gives us a number between 0 and the number of quotes
        // (int) converts it to a whole number - this is our random index
        int randomIndex = (int) (Math.random() * quotes.length);
        tvQuote.setText(quotes[randomIndex]); // Show the quote at the random index
    }

    private void showMenu(View v) { // Shows the hamburger popup menu
        PopupMenu popupMenu = new PopupMenu(this, v); // Create popup - appears near the clicked view
        // Inflate means read the XML file and build the menu from it
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> { // Runs when any menu item is clicked
            int id = item.getItemId(); // Get the ID of the clicked item

            if (id == R.id.menu_profile) { // Profile clicked
                // TODO: Go to Profile screen
                return true; // true means we handled this click

            } else if (id == R.id.menu_how_to_play) { // How to Play clicked
                // TODO: Go to How to Play screen
                return true;

            } else if (id == R.id.menu_logout) { // Logout clicked
                logoutUser();
                return true;
            }

            return false; // false means we did not handle this click
        });

        popupMenu.show(); // Display the menu
    }

    private void logoutUser() { // Logs out the user and goes back to Login screen
        mAuth.signOut(); // Sign out from Firebase

        // Clear Remember Me data from SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("rememberMe", false); // Set rememberMe to false
        editor.remove("email"); // Remove saved email
        editor.apply(); // Save changes

        // Navigate to LoginActivity
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close HomeActivity so user can't press back to return
    }
}