package com.ilay.englishkingdom.Adapters; // The package where this file lives

import android.content.Context; // Context gives us access to app resources and system services
import android.view.LayoutInflater; // LayoutInflater converts XML layout files into actual View objects
import android.view.View; // View is the base class for all UI elements
import android.view.ViewGroup; // ViewGroup is a container that holds other views
import android.widget.ImageView; // Used to display images
import android.widget.ProgressBar; // Used to display the progress bar
import android.widget.TextView; // Used to display text

import androidx.annotation.NonNull; // NonNull means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // RecyclerView is the list we use to display categories

import com.bumptech.glide.Glide; // Glide is the library we use to load images from URLs
import com.ilay.englishkingdom.Models.Category; // Our Category model class
import com.ilay.englishkingdom.R; // Used to reference our XML resources

import java.util.List; // List is a collection that holds multiple Category objects

// CategoryAdapter connects our list of Category objects to the RecyclerView
// Think of it like a "bridge" between the data and the UI
// RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> means this adapter uses CategoryViewHolder
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private Context context; // Context is needed to inflate layouts and load images
    private List<Category> categoryList; // The list of categories we will display
    private OnCategoryClickListener listener; // Interface for handling clicks - explained below

    // Interface for handling category clicks and long presses
    // An interface is like a contract - whoever uses this adapter must implement these methods
    public interface OnCategoryClickListener {
        void onCategoryClick(Category category); // Called when a category is clicked
        void onCategoryLongClick(Category category); // Called when a category is long pressed
    }

    // Constructor - called when we create a new CategoryAdapter
    public CategoryAdapter(Context context, List<Category> categoryList, OnCategoryClickListener listener) {
        this.context = context; // Save the context
        this.categoryList = categoryList; // Save the list of categories
        this.listener = listener; // Save the click listener
    }

    // onCreateViewHolder is called when RecyclerView needs a new card view
    // It inflates (creates) the single_category.xml layout for each card
    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // LayoutInflater converts our single_category.xml into an actual View object
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view); // Return a new ViewHolder with the inflated view
    }

    // onBindViewHolder is called for each category card to fill it with data
    // "position" is the index of the current category in the list
    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categoryList.get(position); // Get the category at this position

        holder.tvCategoryName.setText(category.getCategoryName()); // Set the English name
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // Set the Hebrew name
        holder.tvWordCount.setText(category.getWordCount() + " words"); // Set the word count

        // Load the category image from Cloudinary URL using Glide
        // Glide handles downloading, caching and displaying the image automatically
        Glide.with(context)
                .load(category.getImage()) // Load image from the URL stored in Firestore
                .placeholder(R.drawable.ic_launcher_background) // Show this while image is loading
                .error(R.drawable.ic_launcher_background) // Show this if image fails to load
                .into(holder.imgCategory); // Put the image into the ImageView

        // Set click listener on the card
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) { // Make sure listener exists
                listener.onCategoryClick(category); // Tell the Activity that this category was clicked
            }
        });

        // Set long click listener on the card
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) { // Make sure listener exists
                listener.onCategoryLongClick(category); // Tell the Activity that this category was long pressed
            }
            return true; // Return true means we handled the long click
        });
    }

    // getItemCount returns the total number of categories in our list
    // RecyclerView uses this to know how many cards to create
    @Override
    public int getItemCount() {
        return categoryList.size(); // Return the size of our category list
    }

    // Method to update the progress bar for a specific category
    public void updateProgress(int position, int wordsLearned, int totalWords) {
        if (totalWords > 0) { // Make sure we don't divide by zero
            int progress = (wordsLearned * 100) / totalWords; // Calculate progress percentage
            categoryList.get(position).setWordCount(totalWords); // Update word count
            notifyItemChanged(position); // Tell RecyclerView to refresh this card
        }
    }

    // CategoryViewHolder holds references to all the views in a single category card
    // This avoids calling findViewById() every time which is slow
    public class CategoryViewHolder extends RecyclerView.ViewHolder {

        ImageView imgCategory; // The category image
        TextView tvCategoryName; // The English name
        TextView tvCategoryNameHebrew; // The Hebrew name
        TextView tvWordCount; // The word count
        ProgressBar progressCategory; // The progress bar

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView); // Call the parent constructor with the card view
            // Connect each variable to its XML view using the IDs we set in single_category.xml
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
        }
    }
}