package com.ilay.englishkingdom.Models;

public enum UserRole {
    GUEST,  // Can access Learn and Practice games, but cannot save records and cannot use AI practice
    USER,   // Can access Learn, Practice games, and records are saved
    ADMIN   // Can access everything + add/edit categories and words
}