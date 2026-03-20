package com.ilay.englishkingdom.Adapters;

import android.content.Context; // Needed to inflate layouts and load images with Glide
import android.view.LayoutInflater; // Converts XML layout files into real View objects
import android.view.View; // Base class for all UI elements
import android.view.ViewGroup; // A container that holds other views
import android.widget.ImageView; // Used to display the category image
import android.widget.ProgressBar; // Used to show how many words the user learned in this category
import android.widget.TextView; // Used to show text labels like category name and word count

import androidx.annotation.NonNull; // Means this parameter cannot be null
import androidx.recyclerview.widget.RecyclerView; // The scrollable grid we use to show categories

import com.bumptech.glide.Glide; // Library we use to load images from Cloudinary URLs
import com.google.firebase.auth.FirebaseAuth; // Used to check if a user is logged in
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
        // LayoutInflater.from(context) creates an inflater that knows about our app's resources
        // parent = the RecyclerView that will hold this card
        // false = don't attach to parent yet - RecyclerView will do that itself
        View view = LayoutInflater.from(context).inflate(R.layout.single_category, parent, false);
        return new CategoryViewHolder(view); // Wrap the view in a ViewHolder and return it
    }

    // ==================== BIND DATA TO CARD ====================

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        // RecyclerView calls this for each card to fill it with the correct data
        // position = the index of the category in the list (0 = first, 1 = second etc.)
        // This method can be called many times for the same card as RecyclerView reuses cards
        Category category = categoryList.get(position); // Get the category at this position

        // ==================== FILL CARD WITH DATA ====================

        holder.tvCategoryName.setText(category.getCategoryName()); // Set the English category name
        holder.tvCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // Set the Hebrew name
        holder.tvWordCount.setText(category.getWordCount() + " words"); // e.g. "10 words"

        // Load the category image from its Cloudinary URL using Glide
        // Glide handles downloading, caching, and displaying the image automatically
        // placeholder = image shown while the real image is still downloading
        // error = image shown if the download fails
        // into = which ImageView to put the final image into
        Glide.with(context)
                .load(category.getImage()) // The Cloudinary URL stored in Firestore
                .placeholder(R.drawable.ic_launcher_background) // Show while loading
                .error(R.drawable.ic_launcher_background) // Show if load fails
                .into(holder.imgCategory); // Put the result into the category ImageView

        // ==================== SET CLICK LISTENERS FIRST ====================

        // IMPORTANT: Click listeners must be set BEFORE any early return statements
        // If we set them after the guest check return, guests would never get click listeners
        // and their category cards would be unresponsive even though they should be tappable

        // Tap listener - notifies LearnActivity which category was tapped
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
            // LearnActivity.onCategoryClick() handles what happens next
            // In edit mode it opens the edit dialog
            // In normal mode it opens WordsActivity
            // Both guests and logged in users can tap categories
        });

        // Long press listener - notifies LearnActivity which category was long pressed
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onCategoryLongClick(category);
            // LearnActivity.onCategoryLongClick() handles what happens next
            // In edit mode it shows the delete confirmation dialog
            return true; // true = we handled the long press, don't trigger any other behavior
        });

        // ==================== PROGRESS BAR SETUP ====================

        // Set the progress bar's maximum to the total number of words in this category
        // e.g. if a category has 10 words the bar is 100% full when progress = 10
        // We use 1 as minimum instead of 0 to avoid any division by zero issues
        holder.progressCategory.setMax(
                category.getWordCount() > 0 ? category.getWordCount() : 1);

        // Reset progress bar to 0 BEFORE fetching from Firestore
        // This is important because RecyclerView reuses cards when scrolling
        // Without this reset, a card that showed 5/10 for category A might still show 5
        // while it's loading the progress for category B - which would be wrong
        holder.progressCategory.setProgress(0);

        // ==================== GUEST CHECK ====================

        // Only load progress data if a user is logged in
        // Guests have no Firestore data so there's nothing to load for them
        // But click listeners are already set above so guests can still tap cards
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // Guest - skip progress loading

        String userId = auth.getCurrentUser().getUid(); // Get the logged in user's ID

        // ==================== FIX FOR WRONG CATEGORY PROGRESS BUG ====================

        // The problem this fixes:
        // RecyclerView reuses cards when you scroll - a card that showed "Animals"
        // might be reused to show "Food" when Animals scrolls off screen
        // We start a Firestore fetch for Animals, but by the time it responds
        // the card is already showing Food - so Animals progress appears on Food's card
        //
        // The fix:
        // Before fetching, we tag the card with the current category's ID
        // A tag is like a sticky note we put on the card - "this card currently shows Animals"
        // When Firestore responds, we check if the tag still matches our category ID
        // If it doesn't match the card was recycled - we ignore the result
        holder.itemView.setTag(category.getIdFS()); // Tag this card with the current category's ID

        // Fetch this user's progress for this specific category from Firestore
        // Path in Firestore: users/[userId]/progress/[categoryId]
        // We use get() instead of addSnapshotListener() because having a permanent
        // listener open for every card in the grid would overload Firestore with connections
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("progress").document(category.getIdFS())
                .get() // Read once - no permanent connection kept open
                .addOnSuccessListener(document -> {
                    // This runs when Firestore responds with the progress data
                    // By this time the card might have been recycled for a different category
                    // So we check the tag to make sure this card still shows our category

                    // getTag() reads the label we put on the card in setTag() above
                    // If it no longer matches our category ID the card was recycled
                    // We return early so we don't show the wrong progress on the wrong card
                    if (!category.getIdFS().equals(holder.itemView.getTag())) return;

                    // Tag matches - this card is still showing the same category we fetched for
                    // It is safe to update the progress bar with the real learned word count

                    // Read wordsLearned from the progress document
                    // If document doesn't exist yet (user never opened this category) default to 0
                    int wordsLearned = 0;
                    if (document != null && document.exists()
                            && document.getLong("wordsLearned") != null) {
                        // getLong() reads a number from Firestore
                        // intValue() converts it from Long to int since our progress bar uses int
                        wordsLearned = document.getLong("wordsLearned").intValue();
                    }

                    // Update the progress bar with the real learned word count
                    // e.g. if wordsLearned=3 and max=10 the bar will be 30% full
                    holder.progressCategory.setProgress(wordsLearned);
                });
    }

    // ==================== ITEM COUNT ====================

    @Override
    public int getItemCount() {
        // RecyclerView calls this to know how many cards to create
        return categoryList.size(); // One card per category in the list
    }

    // ==================== VIEW HOLDER ====================

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        // ViewHolder stores references to all the views inside a single category card
        // This avoids calling findViewById() every time a card needs to be shown
        // which would be slow because it searches through the entire view hierarchy each time
        // Think of ViewHolder as a "handle" that gives us quick access to all views in one card

        ImageView imgCategory; // The category image at the top of the card
        TextView tvCategoryName; // The English category name shown below the image
        TextView tvCategoryNameHebrew; // The Hebrew category name shown below the English name
        TextView tvWordCount; // The word count text e.g. "10 words"
        ProgressBar progressCategory; // The progress bar showing how many words were learned

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView); // Always call super - required by Android
            // Connect each variable to its view in single_category.xml using the view IDs
            // findViewById searches the card layout for a view with the matching ID
            imgCategory = itemView.findViewById(R.id.imgCategory);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryNameHebrew = itemView.findViewById(R.id.tvCategoryNameHebrew);
            tvWordCount = itemView.findViewById(R.id.tvWordCount);
            progressCategory = itemView.findViewById(R.id.progressCategory);
        }
    }
}