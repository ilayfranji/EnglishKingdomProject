package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open WordsActivity and pass categoryId
import android.net.Uri; // Used to store the camera photo file path
import android.os.Bundle; // Used when creating the activity and saving state
import android.view.View; // Used to show and hide UI elements
import android.widget.TextView; // Used for the back button, edit button, banner
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery/camera
import androidx.activity.result.contract.ActivityResultContracts; // Provides contracts for gallery and camera
import androidx.appcompat.app.AlertDialog; // Used to show the delete confirmation popup
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.recyclerview.widget.GridLayoutManager; // Arranges category cards in a 2 column grid
import androidx.recyclerview.widget.RecyclerView; // The scrollable grid of category cards

import com.google.android.material.floatingactionbutton.FloatingActionButton; // The round + and X buttons
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read/write categories
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single category document
import com.ilay.englishkingdom.Activities.Dialogs.AddCategoryDialog; // Handles the Add Category flow
import com.ilay.englishkingdom.Activities.Dialogs.EditCategoryDialog; // Handles the Edit Category flow
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery/permissions
import com.ilay.englishkingdom.Adapters.CategoryAdapter; // Connects our category list to RecyclerView
import com.ilay.englishkingdom.Models.Category; // Our Category data model
import com.ilay.englishkingdom.R; // Used to reference XML resources
import com.ilay.englishkingdom.Utils.PermissionManager; // Our helper class for permissions

import java.util.ArrayList; // Used to create the category list
import java.util.List; // The List interface for our category list

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    private RecyclerView recyclerCategories;
    private FloatingActionButton fabCategory;
    private FloatingActionButton fabExitEditMode;
    private TextView tvBack;
    private TextView tvEditMode;
    private TextView tvEditBanner;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private CategoryAdapter categoryAdapter;
    private List<Category> categoryList;

    private boolean isEditMode = false;
    private boolean isAdmin = false;

    private ImagePickerHelper imagePicker;
    private AddCategoryDialog addDialog;
    private EditCategoryDialog editDialog;

    private static final String KEY_CAMERA_URI = "camera_uri"; // מפתח לשמירת הנתיב של התמונה

    //יצירת launcher לגלרייה
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), // חוזה שיש לאפליקציה עם הגלרייה לצורך הבאת תמונה בלבד וחזרה לאפליקציה
            uri -> imagePicker.onGalleryResult(uri)); // מחזירים את התמונה ישר לדיאלוג הImagePicker

    // יצירת launcher למצלמה
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),// חוזה שיש לאפליקציה עם המצלמה לצורך צילום תמונה בלבד וחזרה לאפליקציה
            success -> imagePicker.onCameraResult(success)); // מחזירים את התמונה ישר לדיאלוג הImagePicker


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        // לצורך שמירה של התמונה אם אנדרואיד סוגר את האפליקציה בזמן שהמצלמה נפתחת
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

        categoryList = new ArrayList<>();// יצירת מערך ריק של הקטגוריות

        recyclerCategories.setLayoutManager(new GridLayoutManager(this, 2));// סידור הכרטיסיות בריסייקלר וויו ב2 עמודות
        categoryAdapter = new CategoryAdapter(this, categoryList, this);// מעבירים לאדפטר את רשימת הקטגוריות ובמקרה של לחיצה אומרים לו שיודיע לנו על מה לחצו
        recyclerCategories.setAdapter(categoryAdapter);// חיבור האדפטר לריסייקלר וויו

        checkIfAdmin();// קריאה למתודות
        loadCategories();

        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {// שולחים לImagePicker את קישור התמונה והאם היא צולמה מהגלריה
                // התמונה נשלחת לשני הדיאלוגים ולבסוף בזכות המתודה isShowing() רק אחד מהם משתמש בה, הדיאלוג שפתוח
                    addDialog.onImagePicked(uri);
                    editDialog.onImagePicked(uri);
                },
                galleryLauncher,// העברת הלאנצ'ר למחלקת העזר כדי שהיא תוכל ללכת לגלרייה ולמצלמה בעצמה
                cameraLauncher);

        if (restoredUri != null) imagePicker.setPendingCameraUri(restoredUri);// שליחת התמונה השמורה אם האפליקציה נסגרה אל imagePicker

        // יצירת הדיאלוגים
        addDialog = new AddCategoryDialog(this, imagePicker, () -> {});
        editDialog = new EditCategoryDialog(this, imagePicker, () -> {});


        tvBack.setOnClickListener(v -> finish());
        tvEditMode.setOnClickListener(v -> enterEditMode());// קריאה למתודה
        fabCategory.setOnClickListener(v -> addDialog.show());// מציג את דיאלוג ההוספה
        fabExitEditMode.setOnClickListener(v -> exitEditMode());// קריאה למתודה
    }


    @Override
    protected void onResume() {// כאשר חוזרים למסך הלמידה, מפעילים את המתודה כדי שכל ההישגים וההתקדמות של המשתמש תישאר
        super.onResume();
        if (categoryAdapter != null) {// אם קיים אדפטר, מבקשים ממנו לשים לב למה השתנה
            categoryAdapter.notifyDataSetChanged();
        }
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
        imagePicker.onPermissionResult(requestCode, grantResults);// שולח ישר את התשובה לבקשת ההרשאה מהמשתמש לimagePicker
    }

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return; // אם המשתמש לא רשום לא ממשיכים במתודה

        String userId = mAuth.getCurrentUser().getUid();// שמירת מזהה ייחודי
        db.collection("users").document(userId).get()//הבאת המשתמש ממאגר הנתונים
                .addOnSuccessListener(document -> {
                    if (document.exists()) {// אם קיים משתמש
                        String role = document.getString("role");// שמירת הrole של המשתמש
                        if (role != null && role.equals("ADMIN")) {// אם קיים role והוא מנהל
                            isAdmin = true;// המשתמש אדמין
                            tvEditMode.setVisibility(View.VISIBLE); // מציג את עיפרון העריכה אם המשתמש הוא אדמין
                        }
                    }
                });
    }


    private void loadCategories() {
        // Real time listener - updates automatically when categories are added/edited/deleted
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {// מאזין 24/7 למאגר הנתונים ורואה מה משתנה בו כל הזמן
                    if (error != null) {// במקרה של שגיאה בטעינת הקטגוריות
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    categoryList.clear(); // מחיקת רשימת הקטגוריות הישנה
                    for (QueryDocumentSnapshot doc : snapshots) {// מעבר על כל הקטגוריות במאגר
                        Category category = doc.toObject(Category.class); // המרת הקטגוריה לאובייקט שג'אווה יוכל להשתמש בו
                        category.setIdFS(doc.getId()); // שמירת המזהה הייחודי של הקטגוריה
                        categoryList.add(category);// הוספת הקטגוריה לרשימת הקטגוריות
                    }
                    categoryAdapter.notifyDataSetChanged(); // רענון המסך שיופיעו הקטגוריות לפי העגכון האחרון שהן עברו
                });
    }

    private void enterEditMode() {
        isEditMode = true;// המנהל במצב עריכה
        tvEditBanner.setVisibility(View.VISIBLE); // הצגת סרגל עם פירוט על מצב העריכה במעלה המסך
        fabCategory.setVisibility(View.VISIBLE); // הצגת כפתור ההוספה +
        fabExitEditMode.setVisibility(View.VISIBLE); // הצגת כפתור היציאה ממצב עריכה X
        tvEditMode.setVisibility(View.GONE); // הסתרת העיפרון
    }

    private void exitEditMode() {
        isEditMode = false;// המנהל לא במצב עריכה
        tvEditBanner.setVisibility(View.GONE);
        fabCategory.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCategoryClick(Category category) {
        // המתודה הזאת נקראת על ידי האדפטר ומעבירה לו קטגוריה שעליה לחצו
        if (isEditMode) {
            editDialog.show(category); // בדיקה אם המנהל במצב עריכה, אם כן, לחיצה על קטגוריה מציגה את דיאלוג העריכה
        } else {
            // אם אנחנו במצב רגיל, לחיצה על קטגוריה מעביר למסך המילים של הקטגוריה
            Intent intent = new Intent(this, WordsActivity.class);
            intent.putExtra("categoryId", category.getIdFS()); // מעבירים למסך המילים את המזהה הייחודי של הקטגוריה
            intent.putExtra("categoryName", category.getCategoryName()); // מעבירים גם את שם הקטגוריה
            intent.putExtra("categoryType", category.getCategoryType()); // מעבירים גם את סוג הקטגוריה
            startActivity(intent);// עוברים למסך המילים
        }
    }

    @Override
    public void onCategoryLongClick(Category category) {
        // המתודה הזאת נקראת על ידי האדפטר ומעבירה לו קטגוריה שעליה לחצו לחיצה ארוכה
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // בדיקה אם המנהל במצב עריכה, אם כן מציגים את דיאלוג המחיקה
        }
    }

    private void showDeleteConfirmationDialog(Category category) {
        // מציגים דיאלוג "האם אתה בטוח שאתה רוצה למחוק את הקטגוריה?"
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete \"" + category.getCategoryName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirestore(category.getIdFS()))// מוחקים את הקטגוריה ממאגר הנתונים ובכך היא גם נמחקת מהמסך
                .setNegativeButton("Cancel", null) // סגירת הדיאלוג ללא מחיקה
                .show();
    }

    private void deleteCategoryFromFirestore(String categoryId) {
        // המתודה מוחקת את הדיאלוג ממאגר הנתונים לצמיתות
        db.collection("categories").document(categoryId).delete()// הולך לקטגוריה שקיבלנו במתודה ומוחק אותה
                .addOnSuccessListener(v -> Toast.makeText(this, "Category deleted!", Toast.LENGTH_SHORT).show())// אם הקטגוריה נמחקה נציג הודעת טוסט מתאימה
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show());// אם לא הצלחנו למחוק מסיבה כלשהי של מאגר הנתונים נציג הודעה מתאימה
    }
}