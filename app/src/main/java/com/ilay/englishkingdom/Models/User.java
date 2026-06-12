package com.ilay.englishkingdom.Models;

public class User {

    private String idFS;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String profilePicture;
    private long createdAt;

    //בנאי ריק
    public User() {
    }

    //בונה משתמש
    public User(String idFS, String firstName, String lastName, String email, String role, String profilePicture, long createdAt) {
        this.idFS = idFS;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.profilePicture = profilePicture;
        this.createdAt = createdAt;
    }

    public String getIdFS() {
        return idFS;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setIdFS(String idFS) {
        this.idFS = idFS;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}