package com.ilay.englishkingdom.Models;

public class Word {

    private String idFS;
    private String wordEnglish;
    private String wordHebrew;
    private String image;
    private String exampleSentence;

    // פעולה בונה ריקה כדי שהפייר בייס יוכל להמיר את דוקיומנטס לעצמים מהמחלקה Word
    public Word() {}

    public Word(String idFS, String wordEnglish, String wordHebrew, String image, String exampleSentence) {
        this.idFS = idFS;
        this.wordEnglish = wordEnglish;
        this.wordHebrew = wordHebrew;
        this.image = image;
        this.exampleSentence = exampleSentence;
    }

    public String getIdFS() { return idFS; }
    public void setIdFS(String idFS) { this.idFS = idFS; }

    public String getWordEnglish() { return wordEnglish; }
    public void setWordEnglish(String wordEnglish) { this.wordEnglish = wordEnglish; }

    public String getWordHebrew() { return wordHebrew; }
    public void setWordHebrew(String wordHebrew) { this.wordHebrew = wordHebrew; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getExampleSentence() { return exampleSentence; }
    public void setExampleSentence(String exampleSentence) { this.exampleSentence = exampleSentence; }
}
