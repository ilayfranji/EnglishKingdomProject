package com.ilay.englishkingdom.Models;

public class Category {
    // This is the data model for a category - maps directly to a Firestore document
    // Each field here = one field in the Firestore document
    // Firestore needs an empty constructor to convert documents into Category objects

    private String idFS; // Firestore document ID - not saved in the document itself
    private String categoryName; // English name e.g. "Animals"
    private String categoryNameHebrew; // Hebrew name e.g. "חיות"
    private String image; // Cloudinary URL of the category image
    private int wordCount; // Total number of words/letters/sentences in this category

    // The type of this category - tells us which dialog and card layout to use
    // Saved as a String in Firestore because Firestore doesn't understand Java enums
    // We use CategoryType.name() to convert enum → String when saving
    // We use CategoryType.valueOf() to convert String → enum when reading
    private String categoryType;

    // Empty constructor - Firestore requires this to convert documents into Category objects
    public Category() {}

    // Full constructor - used when creating a new Category object in AddCategoryDialog
    public Category(String idFS, String categoryName, String categoryNameHebrew,
                    String image, int wordCount, String categoryType) {
        this.idFS = idFS;
        this.categoryName = categoryName;
        this.categoryNameHebrew = categoryNameHebrew;
        this.image = image;
        this.wordCount = wordCount;
        this.categoryType = categoryType; // e.g. "WORDS", "LETTERS", "SENTENCES"
    }

    // Getters and setters - Firestore uses these to read and write each field
    public String getIdFS() { return idFS; }
    public void setIdFS(String idFS) { this.idFS = idFS; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryNameHebrew() { return categoryNameHebrew; }
    public void setCategoryNameHebrew(String categoryNameHebrew) { this.categoryNameHebrew = categoryNameHebrew; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }

    // Helper method - converts the String stored in Firestore back into a CategoryType enum
    // Returns WORDS as default if the type is missing or unrecognized
    // This way old categories without a type field still work correctly
    public CategoryType getCategoryTypeEnum() {
        try {
            return CategoryType.valueOf(categoryType); // Convert String → enum
        } catch (Exception e) {
            return CategoryType.WORDS; // Default to WORDS if something goes wrong
        }
    }
}