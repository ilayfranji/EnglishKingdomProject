package com.ilay.englishkingdom.Activities; // The package where this file lives

import android.os.Bundle; // Used when creating the activity
import android.util.Patterns; // Used to validate email format
import android.view.View; // Used to reference UI elements
import android.widget.Button; // Used for buttons
import android.widget.EditText; // Used for text input fields
import android.widget.TextView; // Used for clickable texts
import android.widget.Toast; // Used to show short popup messages

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.google.firebase.auth.FirebaseAuth; // Firebase Authentication
import com.google.firebase.auth.FirebaseAuthUserCollisionException; // Thrown when email is already registered
import com.google.firebase.auth.FirebaseUser; // Represents the currently logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Firestore database
import com.ilay.englishkingdom.Models.UserRole; // Our UserRole enum
import com.ilay.englishkingdom.R; // Used to reference our XML resources

import java.util.HashMap; // Used to create a map of key/value pairs to save in Firestore
import java.util.Map; // Used with HashMap

public class RegisterActivity extends AppCompatActivity { // RegisterActivity is a screen in our app

    // Declare all UI elements - declared here so we can use them in any method
    private EditText etFirstName; // Input field for first name
    private EditText etLastName; // Input field for last name
    private EditText etEmail; // Input field for email
    private EditText etPassword; // Input field for password
    private EditText etConfirmPassword; // Input field for confirm password
    private Button btnRegister; // Register button
    private TextView tvLogin; // Already have account clickable text

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private FirebaseFirestore db; // Our connection to Firestore database

    @Override
    protected void onCreate(Bundle savedInstanceState) { // onCreate runs when the screen is first created
        super.onCreate(savedInstanceState); // Call the parent class onCreate - always required
        setContentView(R.layout.activity_register); // Connect this Java file to the XML layout

        mAuth = FirebaseAuth.getInstance(); // Initialize Firebase Auth - connects us to Firebase
        db = FirebaseFirestore.getInstance(); // Initialize Firestore - connects us to our database

        // Connect each Java variable to its XML view using the ID we gave it in XML
        etFirstName = findViewById(R.id.etFirstName); // Connect first name input
        etLastName = findViewById(R.id.etLastName); // Connect last name input
        etEmail = findViewById(R.id.etEmail); // Connect email input
        etPassword = findViewById(R.id.etPassword); // Connect password input
        etConfirmPassword = findViewById(R.id.etConfirmPassword); // Connect confirm password input
        btnRegister = findViewById(R.id.btnRegister); // Connect register button
        tvLogin = findViewById(R.id.tvLogin); // Connect already have account text

        // Set click listener on the Register button
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the register button is clicked
                registerUser(); // Call the registerUser method
            }
        });

        // Set click listener on the already have account text
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // This runs when the text is clicked
                finish(); // Close RegisterActivity and go back to LoginActivity
            }
        });
    }

    private void registerUser() { // This method handles the registration logic
        // Get text from all input fields and remove extra spaces
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false; // This flag tracks if any field has an error - if true we stop the method

        // Validate First Name - check if it is empty
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required"); // Show error under first name field
            hasError = true; // Mark that there is an error
        } else if (!firstName.matches("[a-zA-Z]+")) { // Check if first name contains only letters
            etFirstName.setError("First name must contain letters only"); // Show error under first name field
            hasError = true; // Mark that there is an error
        }

        // Validate Last Name - check if it is empty
        if (lastName.isEmpty()) {
            etLastName.setError("Last name is required"); // Show error under last name field
            hasError = true; // Mark that there is an error
        } else if (!lastName.matches("[a-zA-Z]+")) { // Check if last name contains only letters
            etLastName.setError("Last name must contain letters only"); // Show error under last name field
            hasError = true; // Mark that there is an error
        }

        // Validate Email - check if it is empty
        if (email.isEmpty()) {
            etEmail.setError("Email is required"); // Show error under email field
            hasError = true; // Mark that there is an error
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // Check if email format is valid
            etEmail.setError("Please enter a valid email"); // Show error under email field
            hasError = true; // Mark that there is an error
        }

        // Validate Password - check if it is empty
        if (password.isEmpty()) {
            etPassword.setError("Password is required"); // Show error under password field
            hasError = true; // Mark that there is an error
        } else if (password.length() < 6) { // Check if password is at least 6 characters
            etPassword.setError("Password must be at least 6 characters"); // Show error under password field
            hasError = true; // Mark that there is an error
        }

        // Validate Confirm Password - check if it is empty
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password"); // Show error under confirm password field
            hasError = true; // Mark that there is an error
        } else if (!password.equals(confirmPassword)) { // Check if passwords match
            etConfirmPassword.setError("Passwords do not match"); // Show error under confirm password field
            hasError = true; // Mark that there is an error
        }

        if (hasError) return; // If any field has an error stop the method - ALL errors show at once

        // All validation passed - tell Firebase to create the account
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> { // addOnCompleteListener waits for Firebase to finish
                    if (task.isSuccessful()) { // If account was created successfully
                        FirebaseUser user = mAuth.getCurrentUser(); // Get the newly created user

                        user.sendEmailVerification() // Send a verification email to the user
                                .addOnCompleteListener(emailTask -> {
                                    if (!emailTask.isSuccessful()) { // If sending verification email failed
                                        Toast.makeText(this, "Could not send verification email, please try again", Toast.LENGTH_LONG).show();
                                    }
                                });

                        saveUserToFirestore(user.getUid(), firstName, lastName, email); // Save user data to Firestore

                    } else { // If registration failed
                        Exception exception = task.getException(); // Get the exception from Firebase

                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // FirebaseAuthUserCollisionException means this email is already registered
                            etEmail.setError("This email is already registered, please login instead");
                            etEmail.requestFocus(); // Move cursor to email field
                        } else if (exception != null && exception.getMessage() != null && exception.getMessage().contains("network")) {
                            // Network error - no internet connection
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                        } else {
                            // Firebase issue - something went wrong on Firebase's side
                            Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToFirestore(String userId, String firstName, String lastName, String email) { // This method saves the user's data to Firestore
        // Create a Map with the user's data - Map is a collection of key/value pairs
        Map<String, Object> user = new HashMap<>(); // HashMap is an implementation of Map
        user.put("firstName", firstName); // Save first name with key "firstName"
        user.put("lastName", lastName); // Save last name with key "lastName"
        user.put("email", email); // Save email with key "email"
        user.put("role", UserRole.USER.name()); // Save role as USER by default - .name() converts the enum to a String
        user.put("createdAt", System.currentTimeMillis()); // Save the current time in milliseconds

        // Save the user document in Firestore
        db.collection("users") // Access the "users" collection in Firestore
                .document(userId) // Use the Firebase Auth UID as the document ID so they are linked
                .set(user) // Set all the data we put in the Map
                .addOnCompleteListener(task -> { // Wait for Firestore to finish
                    if (task.isSuccessful()) { // If data was saved successfully
                        Toast.makeText(this, "Account created! Please verify your email before logging in 📧", Toast.LENGTH_LONG).show();
                        finish(); // Close RegisterActivity and go back to LoginActivity
                    } else { // If saving to Firestore failed
                        Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                    }
                });
    }
}