package com.ilay.englishkingdom.Activities; // The package where this file lives

import android.content.Intent; // Used to navigate between screens
import android.content.SharedPreferences; // Used to save small data locally on the device
import android.os.Bundle; // Used when creating the activity
import android.util.Patterns; // Used to validate email format
import android.view.View; // Used to reference UI elements
import android.widget.Button; // Used for buttons
import android.widget.CheckBox; // Used for the "Remember Me" checkbox
import android.widget.EditText; // Used for text input fields
import android.widget.TextView; // Used for clickable texts
import android.widget.Toast; // Used to show short popup messages

import androidx.appcompat.app.AlertDialog; // Used to show popup windows
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.security.crypto.EncryptedSharedPreferences; // Used to encrypt our local storage
import androidx.security.crypto.MasterKey; // Used to create the encryption key

import com.google.firebase.auth.FirebaseAuth; // Firebase Authentication
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException; // Thrown when email or password is wrong
import com.google.firebase.auth.FirebaseAuthInvalidUserException; // Thrown when no account found with this email
import com.ilay.englishkingdom.R; // Used to reference our XML resources

public class LoginActivity extends AppCompatActivity { // LoginActivity is a screen in our app

    // Declare all UI elements - declared here so we can use them in any method
    private EditText etEmail; // Input field for email
    private EditText etPassword; // Input field for password
    private Button btnLogin; // Login button
    private Button btnGuest; // Guest button
    private CheckBox cbRememberMe; // Remember Me checkbox
    private TextView tvForgotPassword; // Forgot password clickable text
    private TextView tvRegister; // Register clickable text

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private SharedPreferences sharedPreferences; // Our encrypted local storage

    @Override
    protected void onCreate(Bundle savedInstanceState) { // onCreate runs when the screen is first created
        super.onCreate(savedInstanceState); // Call the parent class onCreate - always required
        setContentView(R.layout.activity_login); // Connect this Java file to the XML layout

        mAuth = FirebaseAuth.getInstance(); // Get the Firebase Auth instance - connects us to Firebase

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
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE); // Fall back to regular SharedPreferences
        }

        // Connect each Java variable to its XML view using the ID we gave it in XML
        etEmail = findViewById(R.id.etEmail); // Connect email input
        etPassword = findViewById(R.id.etPassword); // Connect password input
        btnLogin = findViewById(R.id.btnLogin); // Connect login button
        btnGuest = findViewById(R.id.btnGuest); // Connect guest button
        cbRememberMe = findViewById(R.id.cbRememberMe); // Connect remember me checkbox
        tvForgotPassword = findViewById(R.id.tvForgotPassword); // Connect forgot password text
        tvRegister = findViewById(R.id.tvRegister); // Connect register text

        // Check if the user previously checked "Remember Me"
        boolean rememberMe = sharedPreferences.getBoolean("rememberMe", false); // Get saved value, default is false
        if (rememberMe && mAuth.getCurrentUser() != null) { // If remember me is true AND user is still logged in Firebase
            goToHome(); // Skip login screen and go directly to Home
            return; // Stop running the rest of onCreate
        }

        // Load the saved email if it exists
        String savedEmail = sharedPreferences.getString("email", ""); // Get saved email, default is empty string
        if (!savedEmail.isEmpty()) { // If there is a saved email
            etEmail.setText(savedEmail); // Fill the email field automatically
        }

        // Set click listener on the Login button
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the login button is clicked
                loginUser(); // Call the loginUser method
            }
        });

        // Set click listener on the Guest button
        btnGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the guest button is clicked
                showGuestWarning(); // Call the showGuestWarning method
            }
        });

        // Set click listener on the Forgot Password text
        tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when forgot password is clicked
                resetPassword(); // Call the resetPassword method
            }
        });

        // Set click listener on the Register text
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when register text is clicked
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); // Go to RegisterActivity
            }
        });
    }

    private void loginUser() { // This method handles the login logic
        String email = etEmail.getText().toString().trim(); // Get email text and remove extra spaces
        String password = etPassword.getText().toString().trim(); // Get password text and remove extra spaces

        boolean hasError = false; // This flag tracks if any field has an error - if true we stop the method

        // Validate email - check if it is empty
        if (email.isEmpty()) {
            etEmail.setError("Email is required"); // Show error directly under the email field
            hasError = true; // Mark that there is an error
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // Check if email format is valid
            etEmail.setError("Please enter a valid email"); // Show error under email field
            hasError = true; // Mark that there is an error
        }

        // Validate password - check if it is empty
        if (password.isEmpty()) {
            etPassword.setError("Password is required"); // Show error under password field
            hasError = true; // Mark that there is an error
        } else if (password.length() < 6) { // Check if password is at least 6 characters
            etPassword.setError("Password must be at least 6 characters"); // Show error under password field
            hasError = true; // Mark that there is an error
        }

        if (hasError) return; // If any field has an error stop the method - ALL errors show at once

        // All validation passed - tell Firebase to sign in with email and password
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> { // addOnCompleteListener waits for Firebase to finish
                    if (task.isSuccessful()) { // If login was successful

                        // Check if the user has verified their email
                        if (mAuth.getCurrentUser().isEmailVerified()) { // isEmailVerified() returns true if user clicked the verification link
                            // Email is verified - save Remember Me and go to Home
                            SharedPreferences.Editor editor = sharedPreferences.edit(); // Open SharedPreferences for editing
                            if (cbRememberMe.isChecked()) { // If Remember Me checkbox is checked
                                editor.putBoolean("rememberMe", true); // Save rememberMe = true
                                editor.putString("email", email); // Save the email
                            } else { // If Remember Me is NOT checked
                                editor.putBoolean("rememberMe", false); // Save rememberMe = false
                                editor.remove("email"); // Delete any saved email
                            }
                            editor.apply(); // Apply all changes to SharedPreferences
                            goToHome(); // Navigate to Home screen

                        } else { // If email is NOT verified yet
                            mAuth.signOut(); // Sign out the user immediately so they cannot access the app

                            // Show a popup telling the user to verify their email first
                            new AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("Email Not Verified") // Popup title
                                    .setMessage("Please verify your email before logging in. Check your inbox.") // Popup message
                                    .setPositiveButton("Resend Email", (dialog, which) -> { // Button to resend the verification email
                                        // Sign in temporarily just to resend the verification email
                                        mAuth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener(authResult -> {
                                                    authResult.getUser().sendEmailVerification(); // Send verification email again
                                                    mAuth.signOut(); // Sign out immediately after sending
                                                    Toast.makeText(LoginActivity.this, "Verification email sent! Check your inbox 📧", Toast.LENGTH_LONG).show();
                                                });
                                    })
                                    .setNegativeButton("OK", null) // Close the popup
                                    .show(); // Display the popup
                        }

                    } else { // If login failed
                        Exception exception = task.getException(); // Get the exception from Firebase

                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            // FirebaseAuthInvalidUserException is a specific Firebase exception
                            // It means no account exists with this email in Firebase
                            etEmail.setError("No user found, please create an account first"); // Show error under email field
                            etEmail.requestFocus(); // Move cursor to email field

                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            // FirebaseAuthInvalidCredentialsException means the credentials are wrong
                            // In newer Firebase versions, it no longer tells us if it's the email or password
                            // This is intentional for security - to prevent hackers from knowing which emails are registered
                            // So we show a combined message for both wrong email and wrong password
                            Toast.makeText(LoginActivity.this, "Incorrect email or password, please try again", Toast.LENGTH_LONG).show();

                        } else if (exception != null && exception.getMessage() != null && exception.getMessage().contains("network")) {
                            // Network error - no internet connection
                            Toast.makeText(LoginActivity.this, "No internet connection", Toast.LENGTH_LONG).show();

                        } else {
                            // Firebase issue - something went wrong on Firebase's side
                            // This could be a server issue or any other unexpected error
                            Toast.makeText(LoginActivity.this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void resetPassword() { // This method sends a password reset email
        String email = etEmail.getText().toString().trim(); // Get the email from the input field

        if (email.isEmpty()) { // If the email field is empty
            etEmail.setError("Please enter your email first"); // Show error under email field
            etEmail.requestFocus(); // Move cursor to email field
            return; // Stop the method
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // If email format is invalid
            etEmail.setError("Please enter a valid email"); // Show error under email field
            etEmail.requestFocus(); // Move cursor to email field
            return; // Stop the method
        }

        // Tell Firebase to send a password reset email to the user
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> { // Wait for Firebase to finish
                    if (task.isSuccessful()) { // If email was sent successfully
                        Toast.makeText(LoginActivity.this, "Reset email sent! Check your inbox 📧", Toast.LENGTH_LONG).show();
                    } else { // If something went wrong
                        Toast.makeText(LoginActivity.this, "Error sending reset email, please try again", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showGuestWarning() { // This method shows a warning popup before continuing as guest
        new AlertDialog.Builder(this) // Create a new popup dialog
                .setTitle("Continue as Guest") // Set the title of the popup
                .setMessage("As a guest you can access the Learn section and Practice games, but your records will not be saved and AI practice will not be available.") // Warning message
                .setPositiveButton("Continue", (dialog, which) -> { // "Continue" button - runs when clicked
                    goToHome(); // Go to Home screen as guest
                })
                .setNegativeButton("Cancel", null) // "Cancel" button - closes the popup and does nothing
                .show(); // Display the popup on screen
    }

    private void goToHome() { // This method navigates to the Home screen
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class); // Create intent to go to HomeActivity
        startActivity(intent); // Open HomeActivity
        finish(); // Close LoginActivity so user cannot press back and return to login
    }
}