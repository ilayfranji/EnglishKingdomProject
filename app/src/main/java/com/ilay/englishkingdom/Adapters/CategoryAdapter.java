package com.ilay.englishkingdom.Adapters;

import android.content.Context; // Needed to inflate layouts and load images with Glide
import android.view.LayoutInflater; // Converts XML layout files into real View objects
import android.view.View; // Base class for all UI elements
import android.view.ViewGroup; // A container that holds other views
import android.widget.ImageView; // Used to display the category image
import android.widget.ProgressBar; // Used to show the progress bar
import android.widget.TextView; // Used to show text labels

import androidx.annotation.NonNull; // Means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // The scrollable grid we use to show categories

import com.bumptech.glide.Glide; // Library we use to load images from Cloudinary URLs
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read progress data from Firestore
import com.ilay.englishkingdom.Models.Category; // Our Category data model
import com.ilay.englishkingdom.R; // Used to reference XML resources like layouts and drawables

import java.util.List; // List that holds our Category objects

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    // This adapter is the bridge between our category list and the RecyclerView
    // RecyclerView doesn't know how to display a Category object - this adapter teaches it how
    // RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> means this adapter uses CategoryViewHolder

    // ==================== INTERFACE ====================

    public interface OnCategoryClickListener {
        // This is a contract - whoever creates this adapter must implement these two methods
        // LearnActivity implements this so it can handle what happens when a card is tapped
        void onCategoryClick(Category category); // Called when a card is tapped normally
        void onCategoryLongClick(Category category); // Called when a card is long pressed
    }

    // ==================== FIELDS ====================

    private final Context context; // Needed to inflate layouts and load images
    private final List<Category> categoryList; // The list of categories to display
    private final OnCategoryClickListener listener; // Who handles tap and long press events

    // ==================== CONSTRUCTOR ====================

    public CategoryAdapter(Context context, List<Category> categoryList,
                           OnCategoryClickListener listener) {
        // Constructor saves everything we need to do our job
        this.context = context;
        this.categoryList = categoryList;
        this.listener = listener;
    }

    // ==================== CREATE VIEW HOLDER ====================

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // RecyclerView calls this when it needs a new card view
        // Inflate = read single_category.xml and build the View objects from it
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view); // Wrap the view in a ViewHolder and return it
    }

    // ==================== BIND DATA TO CARD ====================

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        // RecyclerView calls this for each card to fill it with data
        // position = the index of the category in the list (0 = first, 1 = second, etc.)
        Category category = categoryList.get(position); // Get the category at this position

        // Fill the card with the category's data
        holder.tvCategoryName.setText(category.getCategoryName()); // Set English name
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // Set Hebrew name
        holder.tvWordCount.setText(category.getWordCount() + " words"); // Set word count text

        // Load the category image from Cloudinary using Glide
        // Glide handles downloading, caching, and displaying the image automatically
        Glide.with(context)
                .load(category.getImage()) // Load from Cloudinary URL stored in Firestore
                .placeholder(R.drawable.ic_launcher_background) // Show this while image loads
                .error(R.drawable.ic_launcher_background) // Show this if image fails to load
                .into(holder.imgCategory); // Put the result into the ImageView

        // Set the progress bar's maximum value to the total number of words in this category
        // For example if a category has 10 words, the bar is full when progress = 10
        // We use 1 instead of 0 as the minimum to avoid dividing by zero later
        holder.progressCategory.setMax(
                category.getWordCount() > 0 ? category.getWordCount() : 1);

        // Reset the progress bar to 0 before fetching the real value from Firestore
        // Without this, while Firestore is still loading, the bar might show
        // a leftover value from a previous category that used this same card
        holder.progressCategory.setProgress(0);

        // Only load progress if a user is logged in - guests have no progress to show
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // No user logged in - stop here

        String userId = auth.getCurrentUser().getUid(); // Get the current user's ID

        // ===== FIX FOR THE WRONG CATEGORY PROGRESS BUG =====
        // The problem: RecyclerView reuses cards when you scroll
        // When a card scrolls off screen, RecyclerView takes it and gives it
        // to the next category that appears on screen
        // The issue is that we start a Firestore fetch for category X,
        // but by the time Firestore responds, the card has been recycled
        // and is now showing category Y - so category X's progress appears on category Y
        //
        // The fix: we tag the card with the current category's ID BEFORE starting the fetch
        // A tag is just a label we stick on the card - like writing a name on a sticky note
        // When Firestore responds, we check if the card's tag still matches our category ID
        // If it doesn't match, the card was recycled - we ignore the result and don't update the bar
        holder.itemView.setTag(category.getIdFS()); // Tag this card with the current category's ID

        // Fetch this user's progress for this specific category from Firestore
        // Path: users/[userId]/progress/[categoryId]
        // We use get() instead of addSnapshotListener() because having a permanent
        // connection open for every card was overloading the system
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("progress").document(category.getIdFS())
                .get() // Read once - no permanent connection kept open
                .addOnSuccessListener(document -> {
                    // This runs when Firestore responds with the progress data
                    // But by now the card might have been recycled for a different category
                    // So we check the tag to make sure the card still shows our category

                    // getTag() returns the label we stuck on the card in setTag() above
                    // If the tag no longer matches our category ID, the card was recycled
                    // We return early and don't update the bar to avoid showing wrong data
                    if (!category.getIdFS().equals(holder.itemView.getTag())) return;

                    // Tag matches - this card is still showing the same category we fetched for
                    // Safe to update the progress bar with the real value

                    // Read wordsLearned from the progress document
                    // If the document doesn't exist yet (user never opened this category) default to 0
                    int wordsLearned = 0;
                    if (document != null && document.exists()
                            && document.getLong("wordsLearned") != null) {
                        // getLong() reads a number field - intValue() converts it from Long to int
                        wordsLearned = document.getLong("wordsLearned").intValue();
                    }

                    // Update the progress bar with the real learned word count
                    // For example if wordsLearned=3 and max=10, the bar will be 30% full
                    holder.progressCategory.setProgress(wordsLearned);
                });

        // Set tap listener - tells LearnActivity which category was tapped
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
        });

        // Set long press listener - tells LearnActivity which category was long pressed
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onCategoryLongClick(category);
            return true; // true = we handled the long press, don't do anything else
        });
    }

    // ==================== ITEM COUNT ====================

    @Override
    public int getItemCount() {
        return categoryList.size(); // Tell RecyclerView how many cards to create
    }

    // ==================== VIEW HOLDER ====================

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        // ViewHolder stores references to all the views inside a single category card
        // This avoids calling findViewById() every time a card is shown which is slow
        // Think of it as a "handle" to all the views in one card

        ImageView imgCategory; // The category image on the card
        TextView tvCategoryName; // The English category name
        TextView tvCategoryNameHebrew; // The Hebrew category name
        TextView tvWordCount; // The word count text (e.g. "10 words")
        ProgressBar progressCategory; // The progress bar showing how many words were learned

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView); // Always call super - required by Android
            // Connect each variable to its view in single_category.xml using the view IDs
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
            // Note: we removed progressListener because we switched from addSnapshotListener
            // to get() - there is no longer a listener to store or cancel
        }
    }
}