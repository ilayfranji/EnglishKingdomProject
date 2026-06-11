package com.ilay.englishkingdom.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.ArrayList;
import java.util.List;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
    //יורש ממחלקת הריסייקלר וויו אדפטר של אנדרואיד, משתשמש בוורד וויו הולדר

    public interface OnWordClickListener {// בלחיצה על מילים האדפטר מודיע למסך המילים ומשם מסך המילים מטפל באירועי הלחיצה
        void onWordClick(Word word); // לחיצה רגילה
        void onWordLongClick(Word word); // לחיצה ארוכה
    }

    private final Context context; // כדי לטעון תמונות
    private final List<Word> wordList; // רשימת המילים שיהיו במסך
    private final OnWordClickListener listener; // ברגע של לחיצה נשמרת הלחיצה (ארוכה או קצרה)
    private final CategoryType categoryType; // סוג הקטגוריה של המילים, כדי לדעת איזה מילים להציג

    //רשימת המילים שהמשתמש למד, מועבר ממסך המילים, נועד להציג וי על מילה שנלמדה
    private List<String> learnedWords = new ArrayList<>();

    //פעולה בונה של האדפטר
    public WordAdapter(Context context, List<Word> wordList,
                       String categoryId, CategoryType categoryType, OnWordClickListener listener) {
        this.context = context;
        this.wordList = wordList;
        this.categoryType = categoryType; // שמירת סוג הקטגוריה לצורך פעולת OnBind
        this.listener = listener;
    }

    // קריאה למתודה כאשר יש שינוי במספר המילים שנלמדו במאגר הנתונים
    public void setLearnedWords(List<String> learnedWords) {//מקבל את רשימת המילים שנלמדו
        this.learnedWords = learnedWords != null ? learnedWords : new ArrayList<>();// אם אין רשימת מילים שנלמדו ניצור אחת חדשה
        notifyDataSetChanged(); // לצורך סימון וי מחדש אם משהו השתנה
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //יוצרים את הקארד וויו הריק
        // Inflate = קורא את המידע של "מילה יחידה" והופך אותו לאובייקט שג'אווה יוכל לעבוד איתו
        // parent = הריסייקלר וויו שיהיו בו הקארד וויו
        // false = עדיין לא לשים את זה בריסייקלר וויו, יצורף אחר כך
        View view = LayoutInflater.from(context).inflate(R.layout.single_word, parent, false);
        return new WordViewHolder(view);// שומרים את הרכיבים שבתוך הקארד וויו לצורך זמינות מיידית
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        // position = האינדקס של המילה ברשימה של המילים
        // יכולה להיקרא על אותה כרטיסייה כמה וכמה פעמים כתוצאה ממיחזור הכרטיסיות
        Word word = wordList.get(position);

        //הצגת או הסתרת דברים בקארד וויו בהתאם לסוג הקטגוריה
        if (categoryType == CategoryType.WORDS) {
            // הצגת כל השדות בקארד וויו
            holder.imgWord.setVisibility(View.VISIBLE); // הצגת תמונה
            holder.tvExampleSentence.setVisibility(View.VISIBLE); // הצגת משפט דוגמא

            // טעינת התמונה מהקלאודינארי
            Glide.with(context).load(word.getImage()).into(holder.imgWord);
            holder.tvExampleSentence.setText(word.getExampleSentence()); // השמת משפט הדוגמא שיש במאגר הנתונים

        } else if (categoryType == CategoryType.LETTERS) {// אם אותיות
            holder.imgWord.setVisibility(View.GONE); // אין תמונה
            holder.tvExampleSentence.setVisibility(View.GONE); // אין משפט דוגמא

        } else if (categoryType == CategoryType.SENTENCES) {// אם משפטים
            holder.imgWord.setVisibility(View.GONE); // אין תמונה
            holder.tvExampleSentence.setVisibility(View.GONE); // אין משפט דוגמא
        }

        // מוצג לכל סוגי הקטגוריות, בכל אחד תוכן שונה
        holder.tvWordEnglish.setText(word.getWordEnglish()); // המילה באנגלית
        holder.tvWordHebrew.setText(word.getWordHebrew()); // המילה בעברית

        // אם המילה נמצאת ברשימת המילים שנלמדו
        if (learnedWords.contains(word.getIdFS())) {
            holder.tvLearned.setVisibility(View.VISIBLE); // הצגת סימון הוי הירוק
        } else {// אם לא נמצאת
            holder.tvLearned.setVisibility(View.GONE); // להחביא את סימון הוי הירוק
        }

        // מאזין ללחיצה קצרה
        holder.itemView.setOnClickListener(v -> listener.onWordClick(word));

        // מאזין ללחיצה ארוכה
        holder.itemView.setOnLongClickListener(v -> {
            listener.onWordLongClick(word);
            return true; // true = טיפול בלחיצה ארוכה, לא צריך להפעיל את הלחיצה הקצרה
        });
    }

    @Override
    public int getItemCount() {
        // קורא למתודה הזאת כדי לדעת כמה קארד וויו ליצור
        return wordList.size(); // קארד וויו אחד לכל מילה ברשימה
    }

    public static class WordViewHolder extends RecyclerView.ViewHolder {
        //במחלקה הזאת המצאים כל הרכיבים שימוחזרו כדי לייצר
        //קארד וויו חדשים עם נתונים ממוחזרים
        ImageView imgWord; // תמונת המילה (רק לסוג "מילים")
        TextView tvWordEnglish; // המילה באנגלית
        TextView tvWordHebrew; // המילה בעברית
        TextView tvExampleSentence; // משפט דוגמא (רק לסוג "מילים")
        TextView tvLearned; // סימון הוי הירוק (רק למילים שנלמדו)

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);// קריאה למחלקת האב עם הוויו כדי שאנדוראיד תדע לנהל לו מקום בזיכרון
            //חיבור המשתנים הגרפיים למשתנים הלוגיים
            imgWord = itemView.findViewById(R.id.imgWord);
            tvWordEnglish = itemView.findViewById(R.id.tvWordEnglish);
            tvWordHebrew = itemView.findViewById(R.id.tvWordHebrew);
            tvExampleSentence = itemView.findViewById(R.id.tvExampleSentence);
            tvLearned = itemView.findViewById(R.id.tvLearned);
        }
    }
}