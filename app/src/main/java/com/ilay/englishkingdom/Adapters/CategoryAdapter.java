package com.ilay.englishkingdom.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    //יורש ממחלקת הריסייקלר וויו אדפטר של אנדרואיד, משתשמש בקטגורי וויו הולדר


    public interface OnCategoryClickListener {// בלחיצה על קטגוריות האדפטר מודיע למסך הלמידה ומשם מסך הלמידה מטפל באירועי הלחיצה
        void onCategoryClick(Category category); // לחיצה רגילה
        void onCategoryLongClick(Category category); // לחיצה ארוכה
    }

    private final Context context; // צריך כדי לקרוא קבצי XML וכדי לטעון תמונות
    private final List<Category> categoryList; // רשימת הקטגוריות שיוצגו
    private final OnCategoryClickListener listener; // ברגע של לחיצה נשמרת הלחיצה (ארוכה או קצרה)


    public CategoryAdapter(Context context, List<Category> categoryList,
                           OnCategoryClickListener listener) {
        // בנאי של האדפטר
        this.context = context;
        this.categoryList = categoryList;
        this.listener = listener;
    }


    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //יוצרים את הקארד וויו הריק
        // Inflate = קורא את המידע של "קטגוריה יחידה" והופך אותו לאובייקט שג'אווה יוכל לעבוד איתו
        // parent = הריסייקלר וויו שיהיו בו הקארד וויו
        // false = עדיין לא לשים את זה בריסייקלר וויו, יצורף אחר כך
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view); // שומרים את הרכיבים שבתוך הקארד וויו לצורך זמינות מיידית
    }


    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {// ממלא את הקארד וויו בנתונים
        // position = האינדקס של הקטגוריה ברשימה של הקטגוריות
        // יכולה להיקרא על אותה כרטיסייה כמה וכמה פעמים כתוצאה ממחזור הכרטיסיות
        Category category = categoryList.get(position); // שמירת הקטגוריה שבמיקום position


        //מילוי נתונים בכרטיסיית קטגוריה
        holder.tvCategoryName.setText(category.getCategoryName()); // שם הכרטיסייה באנגלית
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // שם הכרטיסייה בעברית
        holder.tvWordCount.setText(category.getWordCount() + " words"); // מספר המילים בקטגוריה

        //טעינת התמונה מקלאודינארי
        Glide.with(context)
                .load(category.getImage()) // לקיחת התמונה מהפייר סטור וטעינה שלה
                .placeholder(R.drawable.ic_launcher_background) // בזמן העלאת התמונה תציג תמונה ירוקה
                .error(R.drawable.ic_launcher_background) // אם העלאה לא עבדה מציג תמוה ירוקה כברירת מחדל
                .into(holder.imgCategory); // השמת התמונה בכרטיסייה של הקטגוריה (במיקום המתאים לתמונה)


        // אם נשים את מאזיני הלחיצות מתחת לבדיקה אם המשתמש מחובר אז אורח לא יוכל ללחוץ על קטגוריות

        // מעדכן את מסך הלמידה כשקטגוריה נלחצת
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
            //בודקים אם המאזין לא ריק,
            // אם הוא לא ריק שולחים אותו למסך הלמידה עם הקטגוריה שנלחצה ופעולת הלחיצה והוא מטפל בה שם
        });

        // Long press listener - notifies LearnActivity which category was long pressed
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onCategoryLongClick(category);
            //בודקים אם המאזין לא ריק,
            // אם הוא לא ריק שולחים אותו למסך הלמידה עם הקטגוריה שנלחצה ופעולת הלחיצה והוא מטפל בה שם
            return true; // true = טיפול בלחיצה ארוכה, לא צריך להפעיל את הלחיצה הקצרה
        });


        //הצבת מקסימום ומינימום של הפרוגרס בר, אם נציב 0 במינימום חלוקה ב0 תגרום לשגיאה ולכן
        //ברירת מחדל תהיה להציב מינימום של 1
        holder.progressCategory.setMax(
                category.getWordCount() > 0 ? category.getWordCount() : 1);

        //איפוס הפרוגרס בר כך שלאחר מחזור שלו לקטגוריה אחרת ישר יופיע 0
        holder.progressCategory.setProgress(0);



        FirebaseAuth auth = FirebaseAuth.getInstance();// חיבור לפיירבייס אותנטיקיישן
        if (auth.getCurrentUser() == null) return; // אם המשתמש הוא אורח נעצור את עיצוב הכרטיסיות פה

        String userId = auth.getCurrentUser().getUid(); // שמירת מזהה ייחודי של המשתמש


        holder.itemView.setTag(category.getIdFS()); // סימון הקארד וויו עם המזהה הייחודי הנוכחי שלו


        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("progress").document(category.getIdFS())
                .get() // מביאים את ההתקדמות של המשתמש בקטגוריה הייחודית
                //לא שמים סנאפשוט ליסנר כי אין צורך בהאזנה קבועה אלא רק בלקיחה חד פעמית
                .addOnSuccessListener(document -> {
                    //בדיקה אם הקטגוריה שלקחנו מהמאגר נתונים זהה לסימון ששמנו על הקטגוריה,
                    //אם לא אנחנו עוצרים את המשך המתודה
                    if (!category.getIdFS().equals(holder.itemView.getTag())) return;


                    //בודק אם יש בכלל מילים שנלמדו, אם כן לוקח את הערך ושומר אותו
                    int wordsLearned = 0;// ברירת מחדל 0 מילים
                    if (document != null && document.exists()
                            && document.getLong("wordsLearned") != null) {
                        //מביא את מספר המילים שנלמדו בקטגוריה הזאת
                        wordsLearned = document.getLong("wordsLearned").intValue();
                    }

                    holder.progressCategory.setProgress(wordsLearned);// מעדכן את הפרוגרס בר בהתאם לאחוז המילים שנלמדו
                });
    }


    @Override
    public int getItemCount() {
        // קורא למתודה הזאת כדי לדעת כמה קארד וויו ליצור
        return categoryList.size(); // קארד וויו אחד לכל קטגוריה ברשימה
    }


    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        //במחלקה הזאת המצאים כל הרכיבים שימוחזרו כדי לייצר
        //קארד וויו חדשים עם נתונים ממוחזרים

        ImageView imgCategory; // תמונת הקטגוריה
        TextView tvCategoryName; //שם הקטגוריה באנגלית
        TextView tvCategoryNameHebrew; // שם הקטגוריה בעברית
        TextView tvWordCount; // מספר המילים שיש בקטגוריה
        ProgressBar progressCategory; // מד ההתקדמות באחוזים בלמידת המילים בקטגוריה

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView); // קריאה למחלקת האב עם הוויו כדי שאנדוראיד תדע לנהל לו מקום בזיכרון
            //חיבור המשתנים הגרפיים למשתנים הלוגיים
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
        }
    }
}