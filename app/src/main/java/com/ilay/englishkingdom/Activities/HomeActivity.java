package com.ilay.englishkingdom.Activities; // The package where this file lives

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

public class HomeActivity extends AppCompatActivity { // HomeActivity is a screen in our app

    // Declare all UI elements - declared here so we can use them in any method
    private TextView tvWelcome; // The welcome message text
    private TextView tvMenu; // The hamburger menu button
    private TextView tvQuote; // The motivational quote text
    private CardView cardLearn; // The Learn card
    private CardView cardPractice; // The Practice card

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private FirebaseFirestore db; // Our connection to Firestore database
    private SharedPreferences sharedPreferences; // Our encrypted local storage

    private String[] quotes; // Quotes array - will be loaded from strings.xml

    @Override
    protected void onCreate(Bundle savedInstanceState) { // onCreate runs when the screen is first created
        super.onCreate(savedInstanceState); // Call the parent class onCreate - always required
        setContentView(R.layout.activity_home); // Connect this Java file to the XML layout

        // Load the quotes array from strings.xml
        // getResources() gives us access to all res files
        // getStringArray() gets the string-array we defined in strings.xml
        quotes = getResources().getStringArray(R.array.motivational_quotes);

        mAuth = FirebaseAuth.getInstance(); // Get the Firebase Auth instance - connects us to Firebase
        db = FirebaseFirestore.getInstance(); // Get the Firestore instance - connects us to our database

        // Initialize EncryptedSharedPreferences to store data safely on the device
        try {
            // MasterKey is the encryption key that will protect our data
            // AES256_GCM is a very strong encryption method used by banks and governments
            MasterKey masterKey = new MasterKey.Builder(this) // "this" is the current activity
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM) // Set the encryption type
                    .build(); // Build the key

            // Create EncryptedSharedPreferences - works exactly like SharedPreferences but encrypts everything
            sharedPreferences = EncryptedSharedPreferences.create(
                    this, // The current activity context
                    "EnglishKingdomPrefs", // The name of our local storage file
                    masterKey, // The encryption key we just created
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Encrypts the keys
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Encrypts the values
            );
        } catch (Exception e) { // If something goes wrong with encryption
            // Fall back to regular SharedPreferences - not encrypted but app won't crash
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE);
        }

        // Connect each Java variable to its XML view using the ID we gave it in XML
        tvWelcome = findViewById(R.id.tvWelcome); // Connect welcome text
        tvMenu = findViewById(R.id.tvMenu); // Connect menu button
        tvQuote = findViewById(R.id.tvQuote); // Connect quote text
        cardLearn = findViewById(R.id.cardLearn); // Connect Learn card
        cardPractice = findViewById(R.id.cardPractice); // Connect Practice card

        loadUserName(); // Call the method that loads the user's name from Firestore
        showRandomQuote(); // Call the method that shows a random motivational quote

        // Set click listener on the menu button
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the menu button is clicked
                showMenu(v); // Call showMenu and pass the view that was clicked so menu appears near it
            }
        });

        // Set click listener on the Learn card
        cardLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the Learn card is clicked
                cardLearn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(HomeActivity.this, LearnActivity.class)); // Go to LearnActivity
                    }
                });
            }
        });

        // Set click listener on the Practice card
        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the Practice card is clicked
                // TODO: Go to Practice screen (we will build this later)
            }
        });
    }

    private void loadUserName() { // This method loads the user's name from Firestore
        if (mAuth.getCurrentUser() == null) { // Check if the user is a guest (not logged in)
            tvWelcome.setText("Welcome, Guest! 👋"); // Show generic welcome for guest
            return; // Stop the method - no need to fetch from Firestore
        }

        // User is logged in - get their unique ID from Firebase Auth
        String userId = mAuth.getCurrentUser().getUid(); // getUid() returns the unique ID of the logged in user

        // Access Firestore to get the user's data
        db.collection("users") // Access the "users" collection in Firestore
                .document(userId) // Get the specific document that has the user's ID as its name
                .get() // Fetch the document from Firestore
                .addOnSuccessListener(document -> { // Runs when Firestore responds successfully
                    if (document.exists()) { // Check if the document actually exists in Firestore
                        String firstName = document.getString("firstName"); // Get the "firstName" field from the document
                        tvWelcome.setText("Welcome, " + firstName + "! 👋"); // Set the welcome message with the user's real name
                    } else { // If the document doesn't exist in Firestore
                        tvWelcome.setText("Welcome! 👋"); // Show a generic welcome message
                    }
                })
                .addOnFailureListener(e -> { // Runs if something goes wrong with Firestore
                    tvWelcome.setText("Welcome! 👋"); // Show generic welcome if Firestore fails
                });
    }

    private void showRandomQuote() { // This method picks and shows a random motivational quote
        int randomIndex = (int) (Math.random() * quotes.length); // Pick a random number between 0 and the number of quotes
        tvQuote.setText(quotes[randomIndex]); // Set the quote at the random index
    }

    private void showMenu(View v) { // This method shows the hamburger menu
        PopupMenu popupMenu = new PopupMenu(this, v); // Create a PopupMenu - "this" is the activity, "v" is where it appears

        // Inflate the menu from our XML file in res/menu/home_menu.xml
        // "Inflate" means read the XML file and build the menu from it
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());

        // Handle menu item clicks
        popupMenu.setOnMenuItemClickListener(item -> { // This runs when any menu item is clicked
            int id = item.getItemId(); // Get the ID of the item that was clicked

            if (id == R.id.menu_profile) { // If Profile was clicked
                // TODO: Go to Profile screen (we will build this later)
                return true; // Return true means we handled this click

            } else if (id == R.id.menu_how_to_play) { // If How to Play was clicked
                // TODO: Go to How to Play screen (we will build this later)
                return true; // Return true means we handled this click

            } else if (id == R.id.menu_logout) { // If Logout was clicked
                logoutUser(); // Call the logout method
                return true; // Return true means we handled this click
            }

            return false; // Return false means we did not handle this click
        });

        popupMenu.show(); // Display the menu on screen
    }

    private void logoutUser() { // This method logs out the user
        mAuth.signOut(); // Tell Firebase to sign out the current user

        // Clear the Remember Me data from SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit(); // Open SharedPreferences for editing
        editor.putBoolean("rememberMe", false); // Set rememberMe to false
        editor.remove("email"); // Remove the saved email
        editor.apply(); // Save all the changes

        // Navigate back to the Login screen
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class); // Create intent to go to LoginActivity
        startActivity(intent); // Open LoginActivity
        finish(); // Close HomeActivity so user cannot press back and return to it
    }
}