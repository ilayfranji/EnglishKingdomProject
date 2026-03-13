package com.ilay.englishkingdom.Adapters; // החבילה (package) שבה הקובץ הזה נמצא

import android.content.Context; // Context נותן לנו גישה למשאבי אפליקציה ושירותי מערכת
import android.view.LayoutInflater; // LayoutInflater ממיר קבצי עיצוב XML לאובייקטי View אמיתיים
import android.view.View; // View הוא מחלקת הבסיס לכל אלמנטי ממשק המשתמש
import android.view.ViewGroup; // ViewGroup הוא מיכל המחזיק תצוגות (Views) אחרות
import android.widget.ImageView; // משמש להצגת תמונות
import android.widget.ProgressBar; // משמש להצגת פס התקדמות
import android.widget.TextView; // משמש להצגת טקסט

import androidx.annotation.NonNull; // NonNull אומר שפרמטר זה אינו יכול להיות null
import androidx.recyclerview.widget.RecyclerView; // RecyclerView היא הרשימה בה אנו משתמשים להצגת קטגוריות

import com.bumptech.glide.Glide; // Glide היא הספרייה בה אנו משתמשים לטעינת תמונות מכתובות URL
import com.ilay.englishkingdom.Models.Category; // מחלקת המודל Category שלנו
import com.ilay.englishkingdom.R; // משמש לרפרנס של משאבי ה-XML שלנו

import java.util.List; // List הוא אוסף המחזיק מספר אובייקטי Category

// CategoryAdapter מחבר את רשימת אובייקטי ה-Category שלנו ל-RecyclerView
// חשוב על זה כעל "גשר" בין הנתונים לבין ממשק המשתמש (UI)
// RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> אומר שהאדפטר הזה משתמש ב-CategoryViewHolder
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private Context context; // ה-Context נחוץ כדי "לנפח" (inflate) עיצובים ולטעון תמונות
    private List<Category> categoryList; // רשימת הקטגוריות שנציג
    private OnCategoryClickListener listener; // ממשק (Interface) לטיפול בלחיצות - מוסבר בהמשך

    // ממשק לטיפול בלחיצות רגילות וארוכות על קטגוריה
    // ממשק הוא כמו חוזה - מי שמשתמש באדפטר הזה חייב לממש את המתודות האלו
    public interface OnCategoryClickListener {
        void onCategoryClick(Category category); // נקרא כשלוחצים על קטגוריה
        void onCategoryLongClick(Category category); // נקרא כשמבצעים לחיצה ארוכה על קטגוריה
    }

    // בנאי (Constructor) - נקרא כשאנו יוצרים CategoryAdapter חדש
    public CategoryAdapter(Context context, List<Category> categoryList, OnCategoryClickListener listener) {
        this.context = context; // שמירת ה-context
        this.categoryList = categoryList; // שמירת רשימת הקטגוריות
        this.listener = listener; // שמירת מאזין הלחיצות
    }

    // onCreateViewHolder נקרא כש-RecyclerView זקוק לכרטיס (card view) חדש
    // הוא "מנפח" (יוצר) את עיצוב ה-single_category.xml עבור כל כרטיס
    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // LayoutInflater ממיר את ה-single_category.xml שלנו לאובייקט View אמיתי
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view); // החזרת ViewHolder חדש עם התצוגה שנוצרה
    }

    // onBindViewHolder נקרא עבור כל כרטיס קטגוריה כדי למלא אותו בנתונים
    // "position" הוא האינדקס של הקטגוריה הנוכחית ברשימה
    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categoryList.get(position); // קבלת הקטגוריה במיקום הנוכחי

        holder.tvCategoryName.setText(category.getCategoryName()); // הגדרת השם באנגלית
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // הגדרת השם בעברית
        holder.tvWordCount.setText(category.getWordCount() + " words"); // הגדרת כמות המילים

        // טעינת תמונת הקטגוריה מכתובת Cloudinary באמצעות Glide
        // Glide מטפל בהורדה, שמירה בזיכרון המטמון (caching) והצגת התמונה באופן אוטומטי
        Glide.with(context)
                .load(category.getImage()) // טעינת תמונה מה-URL השמור ב-Firestore
                .placeholder(R.drawable.ic_launcher_background) // הצגת תמונה זו בזמן שהתמונה נטענת
                .error(R.drawable.ic_launcher_background) // הצגת תמונה זו אם טעינת התמונה נכשלת
                .into(holder.imgCategory); // הכנסת התמונה לתוך ה-ImageView

        // הגדרת מאזין לחיצה על הכרטיס
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) { // וודא שהמאזין קיים
                listener.onCategoryClick(category); // עדכון ה-Activity שהקטגוריה הזו נלחצה
            }
        });

        // הגדרת מאזין לחיצה ארוכה על הכרטיס
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) { // וודא שהמאזין קיים
                listener.onCategoryLongClick(category); // עדכון ה-Activity שבוצעה לחיצה ארוכה על הקטגוריה
            }
            return true; // החזרת true אומרת שטיפלנו בלחיצה הארוכה
        });
    }

    // getItemCount מחזיר את המספר הכולל של קטגוריות ברשימה שלנו
    // RecyclerView משתמש בזה כדי לדעת כמה כרטיסים עליו ליצור
    @Override
    public int getItemCount() {
        return categoryList.size(); // החזרת גודל רשימת הקטגוריות שלנו
    }

    // מתודה לעדכון פס ההתקדמות עבור קטגוריה ספציפית
    public void updateProgress(int position, int wordsLearned, int totalWords) {
        if (totalWords > 0) { // וודא שאיננו מחלקים באפס
            int progress = (wordsLearned * 100) / totalWords; // חישוב אחוז ההתקדמות
            categoryList.get(position).setWordCount(totalWords); // עדכון כמות המילים
            notifyItemChanged(position); // הוראה ל-RecyclerView לרענן את הכרטיס הזה
        }
    }

    // CategoryViewHolder מחזיק רפרנסים לכל התצוגות בכרטיס קטגוריה בודד
    // זה מונע קריאה ל-findViewById() בכל פעם, פעולה שנחשבת לאיטית
    public class CategoryViewHolder extends RecyclerView.ViewHolder {

        ImageView imgCategory; // תמונת הקטגוריה
        TextView tvCategoryName; // השם באנגלית
        TextView tvCategoryNameHebrew; // השם בעברית
        TextView tvWordCount; // כמות המילים
        ProgressBar progressCategory; // פס ההתקדמות

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView); // קריאה לבנאי האב עם תצוגת הכרטיס
            // חיבור כל משתנה לתצוגת ה-XML שלו באמצעות ה-IDs שהגדרנו ב-single_category.xml
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
        }
    }
}