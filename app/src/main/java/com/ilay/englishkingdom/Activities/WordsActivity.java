package com.ilay.englishkingdom.Activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Activities.Dialogs.AddLetterDialog;
import com.ilay.englishkingdom.Activities.Dialogs.AddSentenceDialog;
import com.ilay.englishkingdom.Activities.Dialogs.AddWordDialog;
import com.ilay.englishkingdom.Activities.Dialogs.EditLetterDialog;
import com.ilay.englishkingdom.Activities.Dialogs.EditSentenceDialog;
import com.ilay.englishkingdom.Activities.Dialogs.EditWordDialog;
import com.ilay.englishkingdom.Activities.Dialogs.FlashcardDialog;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.Adapters.WordAdapter;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.ArrayList; // Used to create the word list
import java.util.List; // The List interface for our word list

public class WordsActivity extends AppCompatActivity implements WordAdapter.OnWordClickListener {

    private RecyclerView recyclerWords;
    private FloatingActionButton fabWord;
    private FloatingActionButton fabExitEditMode;
    private TextView tvBack;
    private TextView tvEditMode;
    private TextView tvEditBanner;
    private TextView tvCategoryName;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private WordAdapter wordAdapter;
    private List<Word> wordList;


    private boolean isEditMode = false;
    private String categoryId;

    //סוג הקטגוריה, מועבר בעזרת אינטנט על ידי מסך הלמידה
    private CategoryType categoryType;


    //מצהירים על 3 סוגי הדיאלוגים למרות שנשתמש רק בסוג אחד
    private ImagePickerHelper imagePicker; // רק לסוג "מילים"
    private AddWordDialog addWordDialog;
    private EditWordDialog editWordDialog;
    private AddLetterDialog addLetterDialog;
    private EditLetterDialog editLetterDialog;
    private AddSentenceDialog addSentenceDialog;
    private EditSentenceDialog editSentenceDialog;
    private FlashcardDialog flashcardDialog; // דיאלוג שמוצג ברגע של לחיצה על מילה/משפט/אות


    private static final String KEY_CAMERA_URI = "camera_uri"; // מפתח לשמירת הנתיב של התמונה, איפה היא נשמרה


    //יצירת launcher לגלרייה
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (imagePicker != null) imagePicker.onGalleryResult(uri); });

    // יצירת launcher למצלמה
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> { if (imagePicker != null) imagePicker.onCameraResult(success); });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_words);

        // לקיחה ושמירת הנתונים שהועברו ממסך הלמידה
        categoryId = getIntent().getStringExtra("categoryId"); // מזהה ייחודי לקטגוריה
        String categoryName = getIntent().getStringExtra("categoryName"); // שם הקטגוריה באנגלית
        String categoryTypeString = getIntent().getStringExtra("categoryType"); // סוג הקטגוריה

        // המרת הטיפוס לEnum, אם לא הצלחנו אז כדי שלא תקורס האפליקציה
        // נציג את המילים/ משפטים/ אותיות  בתור מילים כברירת מחדל
        try {
            categoryType = CategoryType.valueOf(categoryTypeString);
        } catch (Exception e) {
            categoryType = CategoryType.WORDS;
        }

        if (categoryId == null) {// אם אין סוג לקטגוריה
            Toast.makeText(this, "Error loading words", Toast.LENGTH_SHORT).show();
            finish();//סגירת מסך המילים
            return;
        }

        // שמירת הנתיב של התמונה במקרה ואנדרואיד סוגר את האפליקציה בזמן פתיחת המצלמה
        Uri restoredUri = null;
        if (savedInstanceState != null) {// אם יש נתונים שנשמרו בסגירת האפליקציה ניקח את הנתיב של התמונה
            restoredUri = savedInstanceState.getParcelable(KEY_CAMERA_URI);
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        recyclerWords = findViewById(R.id.recyclerWords);
        fabWord = findViewById(R.id.fabWord);
        fabExitEditMode = findViewById(R.id.fabExitEditMode);
        tvBack = findViewById(R.id.tvBack);
        tvEditMode = findViewById(R.id.tvEditMode);
        tvEditBanner = findViewById(R.id.tvEditBanner);
        tvCategoryName = findViewById(R.id.tvCategoryName);

        if (categoryName != null) tvCategoryName.setText(categoryName);// הצגת שם הקטגוריה

        wordList = new ArrayList<>();// יצירת מערך מילים חדש
        recyclerWords.setLayoutManager(new LinearLayoutManager(this));// מסגר את הריסייקלר וויו במסך המילים

        wordAdapter = new WordAdapter(this, wordList, categoryId, categoryType, this);// מעבירים לאדפטר מספר נתונים ובמקרה של לחיצה אומרים לו שיודיע לנו על מה לחצו
        recyclerWords.setAdapter(wordAdapter);// חיבור האדפטר לריסייקלר וויו

        checkIfAdmin();// קריאה למתודות
        loadWords();
        loadLearnedWords();

        // מגדירים את דיאלוג האימג' פיקר רק לסוג "מילים"
        if (categoryType == CategoryType.WORDS) {
            imagePicker = new ImagePickerHelper(this,
                    (uri, fromGallery) -> {
                        // בדיקה אם קיים דיאלוג כזה, אם כן בבחירת תמונה נשלח את התמונה עצמה לדיאלוג
                        if (addWordDialog != null) addWordDialog.onImagePicked(uri);
                        if (editWordDialog != null) editWordDialog.onImagePicked(uri);
                    },
                    galleryLauncher,// העברת הלאנצ'רים לאימג' פיקר הלפר
                    cameraLauncher);

            if (restoredUri != null) imagePicker.setPendingCameraUri(restoredUri);// אם קיים נתיב תמונה שחילץ אותנו מאיבוד התמונה נשלח אותו

            //יוצר את הדיאלוגים של "מילים" ומעביר להם את דיאלוג האימג' פיקר
            addWordDialog = new AddWordDialog(this, imagePicker, categoryId, () -> {});//סוגריים מסולסלים ריקים, לא לעשות כלום בסיום
            editWordDialog = new EditWordDialog(this, imagePicker, categoryId, () -> {});

        } else if (categoryType == CategoryType.LETTERS) {
            //יצירת הדיאלוגים של "אותיות", אין צורך להעביר את אימג' פיקר
            addLetterDialog = new AddLetterDialog(this, categoryId, () -> {});
            editLetterDialog = new EditLetterDialog(this, categoryId, () -> {});

            //יצירת הדיאלוגים של "משפטים", אין צורך להעביר את אימג' פיקר
        } else if (categoryType == CategoryType.SENTENCES) {
            // Create sentence dialogs - no image picker needed
            addSentenceDialog = new AddSentenceDialog(this, categoryId, () -> {});
            editSentenceDialog = new EditSentenceDialog(this, categoryId, () -> {});
        }

        // דיאלוג למידת מילה, מוצג עבור לחיצה על כל סוגי המילים/משפטים/אותיות
        flashcardDialog = new FlashcardDialog(this, categoryId, wordList, () -> {});

        tvBack.setOnClickListener(v -> finish());
        tvEditMode.setOnClickListener(v -> enterEditMode());
        fabWord.setOnClickListener(v -> showAddDialog()); //
        fabExitEditMode.setOnClickListener(v -> exitEditMode());
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (imagePicker != null && imagePicker.getPendingCameraUri() != null) {// שמירה במאין "כספת" את הכתובת של התמונה שנלקחה מהמצלמה במקרה
            // והאנדוראיד יסגור את האפליקציה כשהמצלמה דולקת
            outState.putParcelable(KEY_CAMERA_URI, imagePicker.getPendingCameraUri());
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {// בדיקת ההרשאות
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //בדיקה אם קיים בכלל אימג' פיקר שלא נקרא לו סתם
        if (imagePicker != null) imagePicker.onPermissionResult(requestCode, grantResults);// שולח את התשובה לבקשת ההרשאה מהמשתמש לimagePicker
    }


    private void showAddDialog() {
        // פותח את דיאלוג ההוספה המתאים לפי סוג הקטגוריה
        if (categoryType == CategoryType.WORDS) {
            addWordDialog.show();
        } else if (categoryType == CategoryType.LETTERS) {
            addLetterDialog.show();
        } else if (categoryType == CategoryType.SENTENCES) {
            addSentenceDialog.show();
        }
    }


    private void showEditDialog(Word word) {
        // פותח את דיאלוג העריכה המתאים לפי סוג הקטגוריה
        if (categoryType == CategoryType.WORDS) {
            editWordDialog.show(word);
        } else if (categoryType == CategoryType.LETTERS) {
            editLetterDialog.show(word);
        } else if (categoryType == CategoryType.SENTENCES) {
            editSentenceDialog.show(word);
        }
    }


    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return;// משתמש לא מחובר לא יכול להיות מנהל
        String userId = mAuth.getCurrentUser().getUid();// שמירת מזהה ייחודי של המשתמש
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {// בדיקה אם קיים המסמך של המשתמש
                        String role = document.getString("role");// שמירת התפקיד של המשתמש
                        if (role != null && role.equals("ADMIN")) {
                            tvEditMode.setVisibility(View.VISIBLE);//אם המשתמש מנהל, מציגים את העיפרון עריכה
                        }
                    }
                });
    }

    private void loadWords() {
        // מאזין זמן אמת מתעדכן ברגע שיש שינוי במילים שבקטגוריה מסויימת
        db.collection("categories").document(categoryId)
                .collection("words")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {// אם יש שגיאה
                        Toast.makeText(this, "Error loading words", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    wordList.clear();// ניקוי רשימת המילים לפני שעוברים על המילים
                    for (QueryDocumentSnapshot doc : snapshots) {// עוברים על כל המילים בקטגוריה
                        Word word = doc.toObject(Word.class);// שמירת הנתונים בטיפוס word
                        word.setIdFS(doc.getId()); //שמירת המזהה הייחודי של המילה
                        wordList.add(word);//הוספת המילה לרשימת המילים
                    }
                    wordAdapter.notifyDataSetChanged();// אומרים לאדפטר לשים לב אם שונו נתונים,
                    //במידה ושונו הוא מעדכן את תצוגת המסך
                });
    }


    private void loadLearnedWords() {
        if (mAuth.getCurrentUser() == null) return;// משתמש לא מחובר לא יכול לסמן מילים שלמד
        String userId = mAuth.getCurrentUser().getUid();// שמירת מזהה ייחודי של המשתמש

        // מאזין זמן אמת מתעדכן ברגע שיש שינוי במילים שבקטגוריה מסויימת
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) return;

                    List<String> learnedWords = new ArrayList<>();// יוצרים רשימה חדשה שבה יוכנסו המילים שנלמדו
                    if (document != null && document.exists()// אם קיים מסמך של הקטגוריה של המילים
                            && document.get("learnedWords") != null) {
                        List<Object> raw = (List<Object>) document.get("learnedWords");// שומר את רשימת המילים שהמשתמש סימן שלמד
                        for (Object item : raw) {// עוברים על כל הרשימה
                            learnedWords.add((String) item);// ממירים את האובייקטים שנשמרו שם לסטרינג
                        }
                    }
                    wordAdapter.setLearnedWords(learnedWords);// מעבירים לאדפטר את רשימת מילים שנלמדו (המעודכנת)
                });
    }


    private void enterEditMode() {// כניסה למצב עריכה
        isEditMode = true;// המשתמש במצב עריכה
        tvEditBanner.setVisibility(View.VISIBLE);
        fabWord.setVisibility(View.VISIBLE);
        fabExitEditMode.setVisibility(View.VISIBLE);
        tvEditMode.setVisibility(View.GONE);// העלמת העיפרון
    }

    private void exitEditMode() {// יציאה ממצב עריכה
        isEditMode = false;// המשתמש לא במצב עריכה
        //העלמת כל הכפתורים והרכיבים שמוםיעים במצב עריכה והחזרת העיפרון
        tvEditBanner.setVisibility(View.GONE);
        fabWord.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE);
    }


    @Override
    public void onWordClick(Word word) {//בלחיצה על מילה
        if (isEditMode) {
            showEditDialog(word); // אם המשתמש במצב עריכה, נציג דיאלוג עריכה
        } else {
            flashcardDialog.show(word); // אם לא במצב עריכה נציג את דיאלוג (יודע/לא יודע)
        }
    }

    @Override
    public void onWordLongClick(Word word) {//בלחיצה ארוכה על המילה
        if (isEditMode) {
            showDeleteConfirmationDialog(word);//קריאה למתודה
        }
    }


    private void showDeleteConfirmationDialog(Word word) {//הצגת דיאלוג "אתה בטוח שאתה רוצה למחוק את המילה?"
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete \"" + word.getWordEnglish() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteWord(word.getIdFS()))//קריאה למתודת מחיקת מילה
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteWord(String wordId) {// מחיקת מילה
        //מוחקים את המילה ממאגר הנתונים
        db.collection("categories").document(categoryId)
                .collection("words").document(wordId).delete()
                .addOnSuccessListener(v -> {// אם הצלחנו למחוק מציגים הודעה
                    Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show();

                    // ספרית המילים שנשארו בקטגוריה
                    db.collection("categories").document(categoryId)
                            .collection("words").get()
                            .addOnSuccessListener(snapshot -> {
                                db.collection("categories").document(categoryId)
                                        .update("wordCount", snapshot.size());// מעדכנים את שדה "wordCount" למספר המילים שיש בתצלום
                            });

                    // Step 3 - remove this wordId from ALL users' learnedWords arrays
                    // so their progress doesn't count a deleted item
                    db.collection("users").get()
                            .addOnSuccessListener(usersSnapshot -> {// אם הצלחנו להביא את הקולקשן
                                for (QueryDocumentSnapshot userDoc : usersSnapshot) {// עוברים על כל המסמכים בקולקשן של יוזרס
                                    String userId = userDoc.getId();// שמירת המזהה הייחודי של המשתמש
                                    db.collection("users").document(userId)
                                            .collection("progress").document(categoryId)
                                            .get()// הבאת ההתקדמות בקטגוריה הספציפית
                                            .addOnSuccessListener(progressDoc -> {// אם הצלחנו
                                                if (!progressDoc.exists()) return;// נבדוק אם קיים מסמך כזה

                                                List<String> learnedWords = new ArrayList<>();// יצירת מערך חדש שבו ישמרו המילים שנלמדו
                                                if (progressDoc.get("learnedWords") != null) {// אם יש שדה של מילים שנלמדו
                                                    List<Object> raw = (List<Object>) progressDoc.get("learnedWords");// שמירה במערך של אובייקטים
                                                    for (Object item : raw) learnedWords.add((String) item);//עוברים על כל המילים שנלמדו ומוסיפים לרשימה
                                                }

                                                // אם רשימת המילים שנלמדו מכילה את המילה שנמחקה
                                                if (learnedWords.contains(wordId)) {
                                                    db.collection("users").document(userId)
                                                            .collection("progress").document(categoryId)
                                                            .update(
                                                                    //נעדכן את רשימת המילים שנלמדו על ידי מחיקת המילה מהמילים שנלמדו
                                                                    "learnedWords", FieldValue.arrayRemove(wordId),
                                                                    "wordsLearned", learnedWords.size() - 1// מעדכן את מס המילים שנלמדו בקטגוריה
                                                                    // ומחסיר מילה אחת
                                                            );
                                                }
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this,// אם לא הצלחנו למחוק מציגים הודעה
                        "Error deleting", Toast.LENGTH_SHORT).show());
    }
}