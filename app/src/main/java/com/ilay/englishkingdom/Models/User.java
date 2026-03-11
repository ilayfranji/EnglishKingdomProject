package com.ilay.englishkingdom.Models; // The package where this file lives - in the Models package

// User is a Model class - it represents a user object in our app
// This class mirrors exactly what we store in Firestore under the "users" collection
public class User {

    // These are the fields of the User - each one represents a piece of data
    private String idFS; // The unique ID of the user in Firestore - matches the Firebase Auth UID
    private String firstName; // The first name of the user - must start with capital letter
    private String lastName; // The last name of the user - must start with capital letter
    private String email; // The email address of the user
    private String role; // The role of the user - either "USER" or "ADMIN"
    private String profilePicture; // The URL of the profile picture stored in Cloudinary - empty string if no picture
    private long createdAt; // The time the account was created - stored as milliseconds since 1970

    // Empty constructor - required by Firestore!
    // Firestore needs an empty constructor to convert database data back into a User object
    public User() {
    }

    // Full constructor - used when we create a new User object with all its data
    public User(String idFS, String firstName, String lastName, String email, String role, String profilePicture, long createdAt) {
        this.idFS = idFS; // "this.idFS" refers to the field above, "idFS" is the parameter passed in
        this.firstName = firstName; // Set the first name
        this.lastName = lastName; // Set the last name
        this.email = email; // Set the email
        this.role = role; // Set the role
        this.profilePicture = profilePicture; // Set the profile picture URL
        this.createdAt = createdAt; // Set the creation time
    }

    // Getters - allow other classes to READ the private fields
    public String getIdFS() {
        return idFS; // Return the Firestore ID
    }

    public String getFirstName() {
        return firstName; // Return the first name
    }

    public String getLastName() {
        return lastName; // Return the last name
    }

    public String getEmail() {
        return email; // Return the email
    }

    public String getRole() {
        return role; // Return the role
    }

    public String getProfilePicture() {
        return profilePicture; // Return the profile picture URL
    }

    public long getCreatedAt() {
        return createdAt; // Return the creation time
    }

    // Setters - allow other classes to WRITE/CHANGE the private fields
    public void setIdFS(String idFS) {
        this.idFS = idFS; // Update the Firestore ID
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName; // Update the first name
    }

    public void setLastName(String lastName) {
        this.lastName = lastName; // Update the last name
    }

    public void setEmail(String email) {
        this.email = email; // Update the email
    }

    public void setRole(String role) {
        this.role = role; // Update the role
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture; // Update the profile picture URL
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt; // Update the creation time
    }
}