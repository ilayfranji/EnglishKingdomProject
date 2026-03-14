package com.ilay.englishkingdom.Adapters;

import android.content.Context; // Needed to inflate layouts and load images
import android.view.LayoutInflater; // Converts XML layout files into real View objects
import android.view.View; // Base class for all UI elements
import android.view.ViewGroup; // A container that holds other views
import android.widget.ImageView; // Used to display images
import android.widget.ProgressBar; // Used to show the progress bar
import android.widget.TextView; // Used to show text

import androidx.annotation.NonNull; // Means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // The scrollable list we use to show categories

import com.bumptech.glide.Glide; // Library we use to load images from URLs
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read progress data from Firestore
import com.ilay.englishkingdom.Models.Category; // Our Category data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.List; // List that holds our Category objects

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    // This adapter connects our category list to the RecyclerView
    // We use a simple one time get() to load progress instead of a real time listener
    // because having a permanent Firestore connection per card was overloading the system
    // and causing "System UI isn't responding" crashes

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category); // Called when card is tapped
        void onCategoryLongClick(Category category); // Called when card is long pressed
    }

    private final Context context; // Needed to inflate layouts and load images
    private final List<Category> categoryList; // The list of categories to display
    private final OnCategoryClickListener listener; // Who handles the click events

    public CategoryAdapter(Context context, List<Category> categoryList,
                           OnCategoryClickListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate = read single_category.xml and build the View from it
        // This creates a new card view every time RecyclerView needs one
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        // This runs for each category card - fills it with data
        Category category = categoryList.get(position);

        holder.tvCategoryName.setText(category.getCategoryName()); // Set English name
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // Set Hebrew name
        holder.tvWordCount.setText(category.getWordCount() + " words"); // Set word count text

        // Load category image from Cloudinary using Glide
        // Glide handles downloading, caching and displaying automatically
        Glide.with(context)
                .load(category.getImage())
                .placeholder(R.drawable.ic_launcher_background) // Show while image loads
                .error(R.drawable.ic_launcher_background) // Show if image fails to load
                .into(holder.imgCategory);

        // Set progress bar max to total words in this category
        // If wordCount is 0 we use 1 to avoid dividing by zero
        holder.progressCategory.setMax(
                category.getWordCount() > 0 ? category.getWordCount() : 1);

        // Reset bar to 0 before loading - prevents showing wrong leftover value
        // while Firestore is still fetching the real progress
        holder.progressCategory.setProgress(0);

        // Only load progress if a user is logged in - guests have no progress
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // Guest - skip progress loading

        String userId = auth.getCurrentUser().getUid(); // Get current user ID

        // Simple one time read - much lighter than a real time listener
        // We use get() instead of addSnapshotListener() because having a permanent
        // Firestore connection open for every card at the same time was overloading
        // the system and causing the "System UI isn't responding" freeze
        // Progress still refreshes correctly because LearnActivity calls
        // notifyDataSetChanged() in onResume() every time you come back from WordsActivity
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("progress").document(category.getIdFS())
                .get() // Read once - no permanent connection kept open
                .addOnSuccessListener(document -> {
                    // Read wordsLearned - default to 0 if document doesn't exist yet
                    int wordsLearned = 0;
                    if (document != null && document.exists()
                            && document.getLong("wordsLearned") != null) {
                        wordsLearned = document.getLong("wordsLearned").intValue();
                    }
                    // Update the progress bar with the real value
                    // e.g. if wordsLearned=3 and max=10, bar will be 30% full
                    holder.progressCategory.setProgress(wordsLearned);
                });

        // Set tap listener - tells LearnActivity which category was tapped
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
        });

        // Set long press listener - tells LearnActivity which category was long pressed
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onCategoryLongClick(category);
            return true; // true = we handled the long press
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size(); // Tell RecyclerView how many cards to show
    }

    // ViewHolder holds references to all the views inside a single category card
    // This avoids calling findViewById() every time a card is shown which would be slow
    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCategory; // The category image
        TextView tvCategoryName; // The English name
        TextView tvCategoryNameHebrew; // The Hebrew name
        TextView tvWordCount; // The word count text
        ProgressBar progressCategory; // The progress bar
        // Note: progressListener was removed because we switched from addSnapshotListener
        // to get() - no listener to store or cancel anymore

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // Connect each variable to its view in single_category.xml
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
        }
    }
}