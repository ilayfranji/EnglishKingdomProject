package com.ilay.englishkingdom.Models; // החבילה שבה הקובץ הזה נמצא - בחבילת ה-Models

// Category היא מחלקת מודל (Model) - היא מייצגת אובייקט של קטגוריה באפליקציה שלנו
// המחלקה הזו משקפת בדיוק את מה שאנחנו שומרים ב-Firestore
public class Category {

    // אלו הם השדות של הקטגוריה - כל אחד מייצג פיסת מידע
    private String idFS; // ה-ID הייחודי של הקטגוריה ב-Firestore - "FS" מייצג את Firestore
    private String categoryName; // שם הקטגוריה באנגלית (למשל "Animals")
    private String categoryNameHebrew; // שם הקטגוריה בעברית (למשל "בעלי חיים")
    private String image; // כתובת ה-URL של תמונת הקטגוריה השמורה ב-Cloudinary
    private int wordCount; // מספר המילים בקטגוריה הזו

    // בנאי ריק - נדרש על ידי Firestore!
    // Firestore זקוק לבנאי ריק כדי להמיר נתונים מהדאטה-בייס בחזרה לאובייקט Category
    public Category() {
    }

    // בנאי מלא - משמש כאשר אנו יוצרים אובייקט Category חדש עם כל הנתונים שלו
    public Category(String idFS, String categoryName, String categoryNameHebrew, String image, int wordCount) {
        this.idFS = idFS; // "this.idFS" מתייחס לשדה למעלה, "idFS" הוא הפרמטר שהועבר
        this.categoryName = categoryName; // הגדרת השם באנגלית
        this.categoryNameHebrew = categoryNameHebrew; // הגדרת השם בעברית
        this.image = image; // הגדרת כתובת ה-URL של התמונה
        this.wordCount = wordCount; // הגדרת כמות המילים
    }

    // Getters - מתודות אלו מאפשרות למחלקות אחרות לקרוא (READ) את השדות הפרטיים
    // השדות הם פרטיים (private) כך שרק getters ו-setters יכולים לגשת אליהם - זה נקרא "כמוסה" (Encapsulation)
    public String getIdFS() {
        return idFS; // החזרת ה-ID מ-Firestore
    }

    public String getCategoryName() {
        return categoryName; // החזרת השם באנגלית
    }

    public String getCategoryNameHebrew() {
        return categoryNameHebrew; // החזרת השם בעברית
    }

    public String getImage() {
        return image; // החזרת כתובת ה-URL של התמונה
    }

    public int getWordCount() {
        return wordCount; // החזרת כמות המילים
    }

    // Setters - מתודות אלו מאפשרות למחלקות אחרות לכתוב או לשנות (WRITE/CHANGE) את השדות הפרטיים
    public void setIdFS(String idFS) {
        this.idFS = idFS; // עדכון ה-ID מ-Firestore
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName; // עדכון השם באנגלית
    }

    public void setCategoryNameHebrew(String categoryNameHebrew) {
        this.categoryNameHebrew = categoryNameHebrew; // עדכון השם בעברית
    }

    public void setImage(String image) {
        this.image = image; // עדכון כתובת ה-URL של התמונה
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount; // עדכון כמות המילים
    }
}