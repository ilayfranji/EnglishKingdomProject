package com.ilay.englishkingdom.Models;

import android.util.Pair;

import java.util.List;

public class Stage {
    private List<Pair<String, String>> stageWordStorage;
    private Pair<String, String> firstWord, secondWord;

    private boolean firstWordSuccess, secondWordSuccess;

    public Stage(
            List<Pair<String, String>> stageWordStorage,
            Pair<String, String> firstWord,
            Pair<String, String> secondWord
    ) {
        this.stageWordStorage = stageWordStorage;
        this.firstWord = firstWord;
        this.secondWord = secondWord;
    }

    public Pair<String, String> getFirstWord() {
        return firstWord;
    }

    public List<Pair<String, String>> getStageWordStorage() {
        return stageWordStorage;
    }

    public Pair<String, String> getSecondWord() {
        return secondWord;
    }

    public boolean isFirstWordSuccess() {
        return firstWordSuccess;
    }

    public boolean isSecondWordSuccess() {
        return secondWordSuccess;
    }

    public void setFirstWordSuccess(boolean firstWordSuccess) {
        this.firstWordSuccess = firstWordSuccess;
    }

    public void setSecondWordSuccess(boolean secondWordSuccess) {
        this.secondWordSuccess = secondWordSuccess;
    }
}