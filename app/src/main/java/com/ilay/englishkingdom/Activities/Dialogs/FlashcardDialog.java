package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FlashcardDialog {

    public interface OnStatusChangedListener {// סוג של חוזה שקובע עם מסך המילים שהפעולה תמומש בWordsActivity
        void onStatusChanged(); // מתודה שקוראים לה לעדכן את המסך ברגע שמשהו משתנה
    }

    private final Activity activity; //לצורך הצגת הדיאלוג והודעות הטוסט
    private final FirebaseFirestore db;
    private final String categoryId;
    private final List<Word> wordList;
    private final OnStatusChangedListener listener;

    public FlashcardDialog(Activity activity, String categoryId,
                           List<Word> wordList, OnStatusChangedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.wordList = wordList; //לצורך עדכון הפרוגרס בר
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word word) {
        // בונים את העיצוב שיצרנו של הדיאלוג ושומרים אותו במשתנה שג'אווה יוכל לעבוד איתו, לא לשים עדיין עיצוב גרפי
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_flashcard, null);

        ImageView imgWord = view.findViewById(R.id.imgFlashcardWord);
        TextView tvWordEnglish = view.findViewById(R.id.tvFlashcardEnglish);
        TextView tvWordHebrew = view.findViewById(R.id.tvFlashcardHebrew);
        TextView tvExampleSentence = view.findViewById(R.id.tvFlashcardExample);
        Button btnKnow = view.findViewById(R.id.btnKnow);
        Button btnStillLearning = view.findViewById(R.id.btnStillLearning);

        // טעינת נתוני המילה לדיאלוג
        tvWordEnglish.setText(word.getWordEnglish());
        tvWordHebrew.setText(word.getWordHebrew());
        tvExampleSentence.setText(word.getExampleSentence());
        Glide.with(activity).load(word.getImage()).into(imgWord);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)// הצבת הוויו שעיצבנו לדיאלוג
                .create();

        // בדיקה אם המשתמש אורח
        boolean isGuest = FirebaseAuth.getInstance().getCurrentUser() == null;

        if (isGuest) {
            // הסתרת הכפתורים
            btnKnow.setVisibility(View.GONE);
            btnStillLearning.setVisibility(View.GONE);

            // הצגת הודעה לאורח
            TextView tvGuestNote = new TextView(activity);
            tvGuestNote.setText("Register a free account to track your progress!");
            tvGuestNote.setTextColor(android.graphics.Color.parseColor("#B0BEC5")); // צבע אפור
            tvGuestNote.setTextSize(13);
            tvGuestNote.setGravity(Gravity.CENTER);
            tvGuestNote.setPadding(16, 16, 16, 16); // מרחק מהקצוות

            // btnKnow.getParent()מביא לנו את הלייאווט של איפה שהכפתורים היו אמורים להיות
            // מוסיפים את ההודעה במיקום המתאים
            ((LinearLayout) btnKnow.getParent()).addView(tvGuestNote);// הלבשת ההודעה

        } else {
            // אם המשתמש מחובר

            // מאזין ללחיצה על כפתור "יודע את המילה"
            btnKnow.setOnClickListener(v -> {
                markWord(word.getIdFS(), true); // קריאה למתודה עם המזהה הייחודי של המילה ועם המידע שהיא נלמדה
                dialog.dismiss(); // העלמת הדיאלוג
            });

            // מאזין ללחיצה על כפתור "עדיין לומד את המילה"
            btnStillLearning.setOnClickListener(v -> {
                markWord(word.getIdFS(), false); // קריאה למתודה עם המזהה הייחודי של המילה ועם המידע שהיא לא נלמדה
                dialog.dismiss(); // העלמת הדיאלוג
            });
        }

        dialog.show();// הצגת הדיאלוג
    }

    private void markWord(String wordId, boolean learned) {
        //מסמנת או מוחקת מילים שנלמדו או לא נלמדו ממאגר הנתונים
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return; // בדיקה נוספת למקרה והמשתמש או אורח

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();// שמירת המזהה הייחודי של המשתמש

        //מביאים את התקדמות המשתמש בקטגוריה שהוא נמצא בה
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .get()
                .addOnSuccessListener(document -> {//מאזין הצלחה

                    List<String> learnedWords = new ArrayList<>();// יוצרים רשימה ריקה חדשה שבה יהיו כל המילים שנלמדו
                    if (document.exists() && document.get("learnedWords") != null) {
                        List<Object> raw = (List<Object>) document.get("learnedWords");
                        for (Object item : raw) {//עוברים על כל המילים שנלמדו
                            learnedWords.add((String) item); // מוסיפים אותם לרשימה
                        }
                    }

                    if (learned) {
                        //המשתמש סימן שהוא למד את המילה, אם היא לא ברשימת המילים שנלמדה נוסיף אותה
                        if (!learnedWords.contains(wordId)) {
                            learnedWords.add(wordId);
                        }
                        Toast.makeText(activity, "Great job!", Toast.LENGTH_SHORT).show();
                    } else {
                        //המשתמש סימן שהוא לא למד את המילה, נוציא את המילה מהרשימה
                        learnedWords.remove(wordId);
                        Toast.makeText(activity, "Keep practicing!", Toast.LENGTH_SHORT).show();
                    }

                    // נבנה האש מאפ לעדכון המידע בפיירסטור
                    HashMap<String, Object> progress = new HashMap<>();
                    progress.put("learnedWords", learnedWords); // עדכון רשימת המילים שלמדנו
                    progress.put("wordsLearned", learnedWords.size()); // עדכון מספר המילים שלמדנו
                    progress.put("totalWords", wordList.size()); // עדכון מספר המילים בקטגוריה הזאת


                    db.collection("users").document(userId)
                            .collection("progress").document(categoryId)
                            .set(progress, SetOptions.merge())// הכנסת האש מאפ בנוסף לשדות שמופיעים בדוקיומנט ולא דירסה שלהם
                            .addOnSuccessListener(v -> {
                                if (listener != null) listener.onStatusChanged(); // ליידע את מסך המילים שמשהו שונה
                            });
                });
    }
}