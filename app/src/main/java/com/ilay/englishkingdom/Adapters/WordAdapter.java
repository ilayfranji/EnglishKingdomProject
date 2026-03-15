package com.ilay.englishkingdom.Adapters;

import android.content.Context; // Needed to load images with Glide
import android.view.LayoutInflater; // Converts XML layout files into real View objects
import android.view.View; // Base class for all UI elements
import android.view.ViewGroup; // A container that holds other views
import android.widget.ImageView; // Used to display the word image
import android.widget.TextView; // Used to show text

import androidx.annotation.NonNull; // Means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // The scrollable list we use to show words

import com.bumptech.glide.Glide; // Library we use to load images from URLs
import com.ilay.englishkingdom.Models.CategoryType; // Our CategoryType enum
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to create an empty list as default
import java.util.List; // List that holds our Word objects

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
    // This adapter connects our word list to the RecyclerView
    // It receives the category type so it knows which fields to show/hide per card
    // WORDS → show image + English + Hebrew + example sentence
    // LETTERS → show letter name + Hebrew explanation only
    // SENTENCES → show English sentence + Hebrew translation only

    public interface OnWordClickListener {
        void onWordClick(Word word); // Called when card is tapped
        void onWordLongClick(Word word); // Called when card is long pressed
    }

    private final Context context; // Needed to load images with Glide
    private final List<Word> wordList; // The list of words to display
    private final OnWordClickListener listener; // Who handles the click events
    private final CategoryType categoryType; // The type of this category - controls which fields show

    // List of wordIds the user has learned - passed from WordsActivity
    // Used to show/hide the green checkmark on each card
    private List<String> learnedWords = new ArrayList<>();

    public WordAdapter(Context context, List<Word> wordList,
                       String categoryId, CategoryType categoryType, OnWordClickListener listener) {
        this.context = context;
        this.wordList = wordList;
        this.categoryType = categoryType; // Save type so we can use it in onBindViewHolder
        this.listener = listener;
    }

    // WordsActivity calls this when the learned words list changes in Firestore
    public void setLearnedWords(List<String> learnedWords) {
        this.learnedWords = learnedWords != null ? learnedWords : new ArrayList<>();
        notifyDataSetChanged(); // Redraw all cards with updated checkmarks
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate single_word.xml - same layout for all types, we show/hide fields below
        View view = LayoutInflater.from(context).inflate(R.layout.single_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        // This runs for each card - fills it with data and shows/hides fields based on type
        Word word = wordList.get(position);

        // Show/hide fields based on category type
        if (categoryType == CategoryType.WORDS) {
            // Regular words - show all fields
            holder.imgWord.setVisibility(View.VISIBLE); // Show image
            holder.tvExampleSentence.setVisibility(View.VISIBLE); // Show example sentence

            // Load word image from Cloudinary URL
            Glide.with(context).load(word.getImage()).into(holder.imgWord);
            holder.tvExampleSentence.setText(word.getExampleSentence()); // Set example sentence

        } else if (categoryType == CategoryType.LETTERS) {
            // Letters - hide image and example sentence
            holder.imgWord.setVisibility(View.GONE); // No image for letters
            holder.tvExampleSentence.setVisibility(View.GONE); // No example sentence for letters

        } else if (categoryType == CategoryType.SENTENCES) {
            // Sentences - hide image and example sentence
            holder.imgWord.setVisibility(View.GONE); // No image for sentences
            holder.tvExampleSentence.setVisibility(View.GONE); // No example sentence for sentences
        }

        // These fields show for ALL types - just with different content
        holder.tvWordEnglish.setText(word.getWordEnglish()); // Word / Letter name / English sentence
        holder.tvWordHebrew.setText(word.getWordHebrew()); // Hebrew word / Hebrew explanation / Hebrew translation

        // Show green checkmark if this word/letter/sentence is in the learned list
        if (learnedWords.contains(word.getIdFS())) {
            holder.tvLearned.setVisibility(View.VISIBLE); // Show checkmark
        } else {
            holder.tvLearned.setVisibility(View.GONE); // Hide checkmark
        }

        // Set tap listener
        holder.itemView.setOnClickListener(v -> listener.onWordClick(word));

        // Set long press listener
        holder.itemView.setOnLongClickListener(v -> {
            listener.onWordLongClick(word);
            return true; // true = we handled the long press
        });
    }

    @Override
    public int getItemCount() {
        return wordList.size(); // Tell RecyclerView how many cards to show
    }

    public static class WordViewHolder extends RecyclerView.ViewHolder {
        ImageView imgWord; // The word image - only shown for WORDS type
        TextView tvWordEnglish; // English word / Letter name / English sentence
        TextView tvWordHebrew; // Hebrew word / Hebrew explanation / Hebrew translation
        TextView tvExampleSentence; // Example sentence - only shown for WORDS type
        TextView tvLearned; // Green checkmark - shown when item is learned

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            imgWord = itemView.findViewById(R.id.imgWord);
            tvWordEnglish = itemView.findViewById(R.id.tvWordEnglish);
            tvWordHebrew = itemView.findViewById(R.id.tvWordHebrew);
            tvExampleSentence = itemView.findViewById(R.id.tvExampleSentence);
            tvLearned = itemView.findViewById(R.id.tvLearned);
        }
    }
}