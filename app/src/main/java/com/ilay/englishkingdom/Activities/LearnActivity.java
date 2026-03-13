package com.ilay.englishkingdom.Activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Activities.Dialogs.AddCategoryDialog;
import com.ilay.englishkingdom.Activities.Dialogs.EditCategoryDialog;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.Adapters.CategoryAdapter;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;
import com.ilay.englishkingdom.Utils.PermissionManager;

import java.util.ArrayList;
import java.util.List;

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    // ==================== אלמנטי ממשק משתמש (UI) ====================

    private RecyclerView recyclerCategories; // הגריד הניתן לגלילה של כרטיסי הקטגוריות
    private FloatingActionButton fabCategory; // כפתור ה-+ שמוצג במצב עריכה להוספת קטגוריות
    private FloatingActionButton fabExitEditMode; // כפתור ה-X ליציאה ממצב עריכה
    private TextView tvBack; // חץ חזרה כדי לחזור ל-HomeActivity
    private TextView tvEditMode; // כפתור העיפרון שמוצג רק למנהלים
    private TextView tvEditBanner; // באנר אדום שמוצג למעלה כשנמצאים במצב עריכה

    // ==================== פיירבייס (FIREBASE) ====================

    private FirebaseFirestore db; // החיבור שלנו למסד הנתונים Firestore
    private FirebaseAuth mAuth; // החיבור שלנו ל-Firebase Authentication

    // ==================== אדפטר ונתונים ====================

    private CategoryAdapter categoryAdapter; // מחבר את רשימת הקטגוריות שלנו ל-RecyclerView
    private List<Category> categoryList; // רשימת כל הקטגוריות שנטענו מ-Firestore

    // ==================== דגלי מצב (STATE FLAGS) ====================

    private boolean isEditMode = false; // true = מנהל נמצא במצב עריכה, false = מצב רגיל
    private boolean isAdmin = false; // true = המשתמש הנוכחי הוא מנהל

    // ==================== עוזרים ודיאלוגים ====================

    // ImagePickerHelper מטפל בכל לוגיקת המצלמה/גלריה/הרשאות
    // אנחנו יוצרים אותו פעם אחת כאן ומעבירים אותו לשני הדיאלוגים כדי שישתפו אותו
    private ImagePickerHelper imagePicker;

    // AddCategoryDialog מטפל בכל תהליך ה-"הוסף קטגוריה חדשה"
    // אנחנו יוצרים אותו פעם אחת וקוראים ל-addDialog.show() כשהמנהל לוחץ על +
    private AddCategoryDialog addDialog;

    // EditCategoryDialog מטפל בכל תהליך ה-"ערוך קטגוריה"
    // אנחנו יוצרים אותו פעם אחת וקוראים ל-editDialog.show(category) כשהמנהל לוחץ על כרטיס
    private EditCategoryDialog editDialog;

    // ==================== שמירה/שחזור מצב (STATE) ====================

    // מפתח המשמש לשמירה ושחזור של ה-URI של המצלמה כשאנדרואיד הורג את ה-activity
    // אנדרואיד עשוי להרוג את LearnActivity כשהמצלמה נפתחת כדי לפנות זיכרון
    // אנחנו שומרים את ה-URI לפני שזה קורה ומשחזרים אותו כשה-activity חוזרת
    private static final String KEY_CAMERA_URI = "camera_uri";

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // אלו חייבים להיווצר כאן בתוך ה-Activity - אנדרואיד לא מרשה ליצור אותם
    // בתוך מחלקות עזר או דיאלוגים. אנחנו מעבירים אותם ל-ImagePickerHelper
    // כדי שיוכל להפעיל אותם, אבל הם חיים כאן.

    // Launcher לגלריה - פותח גלריה, התוצאה חוזרת לכאן, אנחנו מעבירים אותה ל-ImagePickerHelper
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // העברת התוצאה ל-ImagePickerHelper

    // Launcher למצלמה - פותח מצלמה, התוצאה חוזרת לכאן, אנחנו מעבירים אותה ל-ImagePickerHelper
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> imagePicker.onCameraResult(success)); // העברת התוצאה ל-ImagePickerHelper

    // ==================== מחזור חיים (LIFECYCLE) ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        // שחזור ה-URI של המצלמה אם אנדרואיד הרג ובנה מחדש את ה-activity
        // savedInstanceState הוא null בפעם הראשונה, אבל מכיל את הנתונים השמורים שלנו אם נבנה מחדש
        Uri restoredUri = null;
        if (savedInstanceState != null) {
            restoredUri = savedInstanceState.getParcelable(KEY_CAMERA_URI);
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        recyclerCategories = findViewById(R.id.recyclerCategories);
        fabCategory = findViewById(R.id.fabCategory);
        fabExitEditMode = findViewById(R.id.fabExitEditMode);
        tvBack = findViewById(R.id.tvBack);
        tvEditMode = findViewById(R.id.tvEditMode);
        tvEditBanner = findViewById(R.id.tvEditBanner);

        categoryList = new ArrayList<>();
        setupRecyclerView();
        checkIfAdmin();
        loadCategories();

        // יצירת ImagePickerHelper - העברת ה-launchers ו-callback
        // ה-callback (onImagePicked) רץ כשהמשתמש בוחר תמונה
        // אנחנו מחליטים כאן איזה דיאלוג מקבל את התמונה על סמך מי מהם פתוח כרגע
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    // זה רץ אחרי שהמשתמש בוחר או מצלם תמונה
                    // אנחנו מעדכנים את שניהם לגבי התמונה החדשה
                    // כל דיאלוג מתעלם מזה אם הוא לא פתוח כרגע (נבדק באמצעות isShowing())
                    addDialog.onImagePicked(uri);
                    editDialog.onImagePicked(uri);
                },
                galleryLauncher,
                cameraLauncher);

        // שחזור ה-URI של המצלמה לתוך ImagePickerHelper אם ה-activity נבנתה מחדש
        if (restoredUri != null) {
            imagePicker.setPendingCameraUri(restoredUri);
        }

        // יצירת הדיאלוגים - העברת ה-imagePicker כדי שיוכלו להפעיל בחירת תמונה
        addDialog = new AddCategoryDialog(this, imagePicker,
                () -> {}); // Callback ריק - המאזין (snapshot listener) כבר מרענן את הרשימה אוטומטית

        editDialog = new EditCategoryDialog(this, imagePicker,
                () -> {}); // Callback ריק - מאותה סיבה

        // הגדרת מאזיני לחיצה (click listeners)
        tvBack.setOnClickListener(v -> finish()); // סגירת המסך הזה וחזרה אחורה
        tvEditMode.setOnClickListener(v -> enterEditMode()); // כניסה למצב עריכה
        fabCategory.setOnClickListener(v -> addDialog.show()); // הצגת דיאלוג הוספה - שורה אחת!
        fabExitEditMode.setOnClickListener(v -> exitEditMode()); // יציאה ממצב עריכה
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // שמירת ה-URI של המצלמה לפני שאנדרואיד עשוי להרוג את ה-activity
        // imagePicker.getPendingCameraUri() מחזיר את ה-URI שיצרנו עבור המצלמה
        // אם הוא null (אין מצלמה בתהליך) שום דבר לא נשמר - וזה בסדר
        if (imagePicker != null && imagePicker.getPendingCameraUri() != null) {
            outState.putParcelable(KEY_CAMERA_URI, imagePicker.getPendingCameraUri());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // העברת תוצאת ההרשאה ל-ImagePickerHelper
        // ImagePickerHelper הוא זה שביקש את ההרשאה ולכן הוא מטפל בתוצאה
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== הגדרות (SETUP) ====================

    private void setupRecyclerView() {
        // GridLayoutManager עם 2 עמודות מסדר את הכרטיסים זה לצד זה
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerCategories.setLayoutManager(layoutManager);
        categoryAdapter = new CategoryAdapter(this, categoryList, this);
        recyclerCategories.setAdapter(categoryAdapter);
    }

    // ==================== בדיקת מנהל (ADMIN CHECK) ====================

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return; // אין משתמש מחובר - דלג

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        if (role != null && role.equals("ADMIN")) {
                            isAdmin = true;
                            tvEditMode.setVisibility(View.VISIBLE); // הצגת כפתור העיפרון למנהל בלבד
                        }
                    }
                });
    }

    // ==================== טעינת קטגוריות ====================

    private void loadCategories() {
        // addSnapshotListener = עדכונים בזמן אמת - רץ בכל פעם שנתוני Firestore משתנים
        // כך שכשמוסיפים/עורכים/מוחקים קטגוריה הרשימה מתרעננת אוטומטית
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    categoryList.clear(); // ניקוי נתונים ישנים לפני הוספת נתונים טריים
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Category category = doc.toObject(Category.class); // המרת המסמך לאובייקט Category
                        category.setIdFS(doc.getId()); // שמירת ה-ID של מסמך ה-Firestore לתוך האובייקט
                        categoryList.add(category);
                    }
                    categoryAdapter.notifyDataSetChanged(); // הוראה ל-RecyclerView להתרענן
                });
    }

    // ==================== מצב עריכה (EDIT MODE) ====================

    private void enterEditMode() {
        isEditMode = true;
        tvEditBanner.setVisibility(View.VISIBLE); // הצגת באנר העריכה האדום
        fabCategory.setVisibility(View.VISIBLE); // הצגת כפתור ה-+
        fabExitEditMode.setVisibility(View.VISIBLE); // הצגת כפתור ה-X
        tvEditMode.setVisibility(View.GONE); // הסתרת כפתור העיפרון
    }

    private void exitEditMode() {
        isEditMode = false;
        tvEditBanner.setVisibility(View.GONE);
        fabCategory.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE); // הצגת כפתור העיפרון שוב
    }

    // ==================== מאזיני לחיצה על קטגוריה ====================

    @Override
    public void onCategoryClick(Category category) {
        // נקרא על ידי ה-CategoryAdapter כשלוחצים על כרטיס
        if (isEditMode) {
            editDialog.show(category); // מצב עריכה ← פתיחת דיאלוג עריכה - שורה אחת!
        } else {
            // TODO: ניווט למסך המילים
        }
    }

    @Override
    public void onCategoryLongClick(Category category) {
        // נקרא על ידי ה-CategoryAdapter בלחיצה ארוכה על כרטיס
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // מצב עריכה ← הצגת אישור מחיקה
        }
    }

    // ==================== מחיקה (DELETE) ====================

    private void showDeleteConfirmationDialog(Category category) {
        // פופ-אפ אישור פשוט לפני מחיקה קבועה - מונע תקלות
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete \"" + category.getCategoryName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirestore(category.getIdFS()))
                .setNegativeButton("Cancel", null) // null = פשוט סגור, אל תמחק
                .show();
    }

    private void deleteCategoryFromFirestore(String categoryId) {
        // מחיקה קבועה של מסמך הקטגוריה מ-Firestore
        db.collection("categories").document(categoryId).delete()
                .addOnSuccessListener(v -> Toast.makeText(this, "Category deleted! 🗑️", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show());
    }
}