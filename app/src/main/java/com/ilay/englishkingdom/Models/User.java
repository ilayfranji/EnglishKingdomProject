package com.ilay.englishkingdom.Models; // החבילה שבה הקובץ הזה נמצא - בחבילת ה-Models

// User היא מחלקת מודל (Model) - היא מייצגת אובייקט של משתמש באפליקציה שלנו
// המחלקה הזו משקפת בדיוק את מה שאנחנו שומרים ב-Firestore תחת האוסף "users"
public class User {

    // אלו הם השדות של המשתמש - כל אחד מייצג פיסת מידע
    private String idFS; // ה-ID הייחודי של המשתמש ב-Firestore - תואם ל-UID של Firebase Auth
    private String firstName; // השם הפרטי של המשתמש - חייב להתחיל באות גדולה (באנגלית)
    private String lastName; // שם המשפחה של המשתמש - חייב להתחיל באות גדולה (באנגלית)
    private String email; // כתובת האימייל של המשתמש
    private String role; // תפקיד המשתמש - או "USER" או "ADMIN"
    private String profilePicture; // כתובת ה-URL של תמונת הפרופיל ב-Cloudinary - מחרוזת ריקה אם אין תמונה
    private long createdAt; // זמן יצירת החשבון - נשמר כמילישניות מאז 1970

    // בנאי ריק - נדרש על ידי Firestore!
    // Firestore זקוק לבנאי ריק כדי להמיר נתונים מהדאטה-בייס בחזרה לאובייקט User
    public User() {
    }

    // בנאי מלא - משמש כאשר אנו יוצרים אובייקט User חדש עם כל הנתונים שלו
    public User(String idFS, String firstName, String lastName, String email, String role, String profilePicture, long createdAt) {
        this.idFS = idFS; // "this.idFS" מתייחס לשדה למעלה, "idFS" הוא הפרמטר שהועבר
        this.firstName = firstName; // הגדרת השם הפרטי
        this.lastName = lastName; // הגדרת שם המשפחה
        this.email = email; // הגדרת האימייל
        this.role = role; // הגדרת התפקיד
        this.profilePicture = profilePicture; // הגדרת כתובת ה-URL של תמונת הפרופיל
        this.createdAt = createdAt; // הגדרת זמן היצירה
    }

    // Getters - מאפשרים למחלקות אחרות לקרוא (READ) את השדות הפרטיים
    public String getIdFS() {
        return idFS; // החזרת ה-ID מ-Firestore
    }

    public String getFirstName() {
        return firstName; // החזרת השם הפרטי
    }

    public String getLastName() {
        return lastName; // החזרת שם המשפחה
    }

    public String getEmail() {
        return email; // החזרת האימייל
    }

    public String getRole() {
        return role; // החזרת התפקיד
    }

    public String getProfilePicture() {
        return profilePicture; // החזרת כתובת התמונה
    }

    public long getCreatedAt() {
        return createdAt; // החזרת זמן היצירה
    }

    // Setters - מאפשרים למחלקות אחרות לכתוב או לשנות (WRITE/CHANGE) את השדות הפרטיים
    public void setIdFS(String idFS) {
        this.idFS = idFS; // עדכון ה-ID מ-Firestore
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName; // עדכון השם הפרטי
    }

    public void setLastName(String lastName) {
        this.lastName = lastName; // עדכון שם המשפחה
    }

    public void setEmail(String email) {
        this.email = email; // עדכון האימייל
    }

    public void setRole(String role) {
        this.role = role; // עדכון התפקיד
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture; // עדכון כתובת התמונה
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt; // עדכון זמן היצירה
    }
}