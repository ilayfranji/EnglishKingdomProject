package com.ilay.englishkingdom.Adapters;

import android.content.Context; // Needed to load images with Glide
import android.view.LayoutInflater; // Converts XML layout files into real View objects
import android.view.View; // Base class for all UI elements
import android.view.ViewGroup; // A container that holds other views
import android.widget.ImageView; // Used to display images
import android.widget.TextView; // Used to show text

import androidx.annotation.NonNull; // Means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // The scrollable list we use to show words

import com.bumptech.glide.Glide; // Library we use to load images from URLs
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to create an empty list as default
import java.util.List; // List that holds our Word objects

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
    // This adapter connects our word list to the RecyclerView
    // Instead of fetching Firestore inside every card (slow and unreliable),
    // WordsActivity fetches the learned words ONCE and passes them here via setLearnedWords()
    // This way checkmarks show instantly without any delay

    public interface OnWordClickListener {
        void onWordClick(Word word); // Called when card is tapped
        void onWordLongClick(Word word); // Called when card is long pressed
    }

    private final Context context; // Needed to load images with Glide
    private final List<Word> wordList; // The list of words to display
    private final OnWordClickListener listener; // Who handles the click events

    // This list holds the wordIds the user has marked as learned
    // It starts empty and gets updated when WordsActivity calls setLearnedWords()
    // Each item in this list is a Firestore document ID of a learned word
    private List<String> learnedWords = new ArrayList<>();

    public WordAdapter(Context context, List<Word> wordList,
                       String categoryId, OnWordClickListener listener) {
        this.context = context;
        this.wordList = wordList;
        this.listener = listener;
        // Note: categoryId is no longer used here because WordsActivity
        // now handles fetching learned words and passes them via setLearnedWords()
    }

    public void setLearnedWords(List<String> learnedWords) {
        // WordsActivity calls this every time the learned words list changes in Firestore
        // We save the new list and redraw all cards so checkmarks update immediately
        this.learnedWords = learnedWords != null ? learnedWords : new ArrayList<>();
        notifyDataSetChanged(); // Redraw all cards with the updated learned status
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate = read single_word.xml and build the View from it
        // This creates a new card view every time RecyclerView needs one
        View view = LayoutInflater.from(context).inflate(R.layout.single_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        // This runs for each word card - fills it with the word's data
        Word word = wordList.get(position);

        holder.tvWordEnglish.setText(word.getWordEnglish()); // Set English word
        holder.tvWordHebrew.setText(word.getWordHebrew()); // Set Hebrew translation
        holder.tvExampleSentence.setText(word.getExampleSentence()); // Set example sentence

        // Load word image from Cloudinary URL using Glide
        Glide.with(context).load(word.getImage()).into(holder.imgWord);

        // Check if this word's ID exists in the learnedWords list
        // learnedWords is already loaded so this check is instant - no Firestore call needed
        // If the wordId is in the list → show green checkmark
        // If not → hide the checkmark
        if (learnedWords.contains(word.getIdFS())) {
            holder.tvLearned.setVisibility(View.VISIBLE); // Show green checkmark ✅
        } else {
            holder.tvLearned.setVisibility(View.GONE); // Hide green checkmark
        }

        // Set tap listener - tells WordsActivity which word was tapped
        holder.itemView.setOnClickListener(v -> listener.onWordClick(word));

        // Set long press listener - tells WordsActivity which word was long pressed
        holder.itemView.setOnLongClickListener(v -> {
            listener.onWordLongClick(word);
            return true; // true = we handled the long press, don't do anything else
        });
    }

    @Override
    public int getItemCount() {
        return wordList.size(); // Tell RecyclerView how many cards to show
    }

    // ViewHolder holds references to all the views inside a single word card
    // This avoids calling findViewById() every time a card is shown which would be slow
    public static class WordViewHolder extends RecyclerView.ViewHolder {
        ImageView imgWord; // The word image
        TextView tvWordEnglish; // The English word
        TextView tvWordHebrew; // The Hebrew translation
        TextView tvExampleSentence; // The example sentence
        TextView tvLearned; // The green checkmark - shown when word is learned

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            // Connect each variable to its view in single_word.xml
            imgWord = itemView.findViewById(R.id.imgWord);
            tvWordEnglish = itemView.findViewById(R.id.tvWordEnglish);
            tvWordHebrew = itemView.findViewById(R.id.tvWordHebrew);
            tvExampleSentence = itemView.findViewById(R.id.tvExampleSentence);
            tvLearned = itemView.findViewById(R.id.tvLearned); // The checkmark
        }
    }
}