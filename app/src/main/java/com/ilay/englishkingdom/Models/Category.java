package com.ilay.englishkingdom.Models; // The package where this file lives - in the Models package

// Category is a Model class - it represents a category object in our app
// This class mirrors exactly what we store in Firestore
public class Category {

    // These are the fields of the Category - each one represents a piece of data
    private String idFS; // The unique ID of the category in Firestore - "FS" stands for Firestore
    private String categoryName; // The name of the category in English (e.g. "Animals")
    private String categoryNameHebrew; // The name of the category in Hebrew (e.g. "בעלי חיים")
    private String image; // The URL of the category image stored in Cloudinary
    private int wordCount; // The number of words in this category

    // Empty constructor - required by Firestore!
    // Firestore needs an empty constructor to convert database data back into a Category object
    public Category() {
    }

    // Full constructor - used when we create a new Category object with all its data
    public Category(String idFS, String categoryName, String categoryNameHebrew, String image, int wordCount) {
        this.idFS = idFS; // "this.idFS" refers to the field above, "idFS" is the parameter passed in
        this.categoryName = categoryName; // Set the English name
        this.categoryNameHebrew = categoryNameHebrew; // Set the Hebrew name
        this.image = image; // Set the image URL
        this.wordCount = wordCount; // Set the word count
    }

    // Getters - these methods allow other classes to READ the private fields
    // Fields are private so only getters and setters can access them - this is called Encapsulation
    public String getIdFS() {
        return idFS; // Return the Firestore ID
    }

    public String getCategoryName() {
        return categoryName; // Return the English name
    }

    public String getCategoryNameHebrew() {
        return categoryNameHebrew; // Return the Hebrew name
    }

    public String getImage() {
        return image; // Return the image URL
    }

    public int getWordCount() {
        return wordCount; // Return the word count
    }

    // Setters - these methods allow other classes to WRITE/CHANGE the private fields
    public void setIdFS(String idFS) {
        this.idFS = idFS; // Update the Firestore ID
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName; // Update the English name
    }

    public void setCategoryNameHebrew(String categoryNameHebrew) {
        this.categoryNameHebrew = categoryNameHebrew; // Update the Hebrew name
    }

    public void setImage(String image) {
        this.image = image; // Update the image URL
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount; // Update the word count
    }
}