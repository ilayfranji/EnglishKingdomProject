package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

public class EditLetterDialog {
    // This dialog handles editing an existing letter in the Letters category
    // Same as AddLetterDialog but pre-fills the existing data

    public interface OnLetterEditedListener {
        void onLetterEdited(); // Called after letter is successfully updated in Firestore
    }

    private final Activity activity;
    private final OnLetterEditedListener listener;
    private final FirebaseFirestore db;
    private final String categoryId;
    private AlertDialog openDialog;
    private Word currentLetter; // The letter being edited - we need its ID

    public EditLetterDialog(Activity activity, String categoryId, OnLetterEditedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word letter) {
        this.currentLetter = letter; // Save reference - needed in updateFirestore()

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_letter, null);
        EditText etLetterName = view.findViewById(R.id.etLetterName);
        EditText etLetterExplanation = view.findViewById(R.id.etLetterExplanation);

        // Pre-fill with existing letter data so admin can see current values
        // wordEnglish stores the letter name (A-a)
        // wordHebrew stores the Hebrew explanation (אלף)
        etLetterName.setText(letter.getWordEnglish());
        etLetterExplanation.setText(letter.getWordHebrew());

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Letter")
                .setView(view)
                .setPositiveButton("Save", null) // null = handle click manually
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String letterName = etLetterName.getText().toString().trim();
            String explanation = etLetterExplanation.getText().toString().trim();
            boolean hasError = false;

            // Validate letter name format A-a
            if (letterName.isEmpty()) {
                etLetterName.setError("Required");
                hasError = true;
            } else if (!letterName.matches("[A-Z]-[a-z]")) {
                etLetterName.setError("Format must be A-a");
                hasError = true;
            }

            // Validate Hebrew explanation
            if (explanation.isEmpty()) {
                etLetterExplanation.setError("Required");
                hasError = true;
            } else if (!explanation.matches("[\\u0590-\\u05FF ]+")) {
                etLetterExplanation.setError("Hebrew only");
                hasError = true;
            }

            if (hasError) return;

            // Update the letter document in Firestore
            // We only update wordEnglish and wordHebrew - other fields stay the same
            db.collection("categories").document(categoryId)
                    .collection("words").document(currentLetter.getIdFS())
                    .update("wordEnglish", letterName, "wordHebrew", explanation)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(activity, "Letter updated! ✅", Toast.LENGTH_SHORT).show();
                        if (openDialog != null) openDialog.dismiss();
                        if (listener != null) listener.onLetterEdited();
                    })
                    .addOnFailureListener(e -> Toast.makeText(activity, "Error updating letter", Toast.LENGTH_SHORT).show());
        });
    }
}