package com.ilay.englishkingdom.Views;

import android.content.Context; // Needed for the View constructor - gives access to app resources
import android.graphics.Canvas; // Used to draw everything on screen - letters, colors, borders
import android.graphics.Color; // Used to get color values like Color.WHITE or Color.parseColor()
import android.graphics.Paint; // Used to configure how things are drawn - color, size, style
import android.graphics.RectF; // Used to define a rectangle with float coordinates for drawing cells
import android.util.AttributeSet; // Needed when the view is created from XML - holds XML attributes
import android.view.MotionEvent; // Used to detect when user touches, drags, and lifts finger
import android.view.View; // The base class for all UI elements in Android

public class WordSearchGridView extends View {
    // This is a custom View that draws the word search letter grid
    // and handles touch events for selecting letters
    // We use a custom View instead of a GridLayout because we need
    // full control over drawing colors and handling drag gestures
    // Only horizontal and vertical selections are supported - no diagonals

    // ==================== LISTENER INTERFACE ====================

    // Interface = a contract - whoever uses this must provide this method
    // WordSearchActivity implements this so it gets notified when user finishes selecting
    public interface OnWordSelectedListener {
        void onWordSelected(String word); // Called when user lifts finger after dragging
    }

    // ==================== GRID DATA ====================

    private char[][] grid; // The 2D array holding all letters - grid[row][col]
    private boolean[][] found; // Tracks which cells belong to a found word - true = green
    private int gridSize = 12; // The grid is 12x12 cells
    private float cellSize; // The size of each cell in pixels - calculated when view size is known

    // ==================== SELECTION STATE ====================

    private int startRow = -1; // Row where user started touching - -1 means no touch yet
    private int startCol = -1; // Column where user started touching
    private int endRow = -1; // Row where user's finger currently is while dragging
    private int endCol = -1; // Column where user's finger currently is while dragging
    private boolean isTouching = false; // true = user is currently dragging their finger

    // ==================== PAINT OBJECTS ====================

    // Paint objects store drawing settings like color and style
    // We create them once here instead of inside onDraw() to avoid
    // creating new objects every frame which would slow down the app
    private Paint cellPaint; // Draws normal cell backgrounds - dark blue
    private Paint selectedPaint; // Draws cells being selected - gold highlight
    private Paint foundPaint; // Draws cells that are part of a found word - green
    private Paint textPaint; // Draws the letters inside each cell - white
    private Paint borderPaint; // Draws the border around each cell

    // ==================== LISTENER ====================

    private OnWordSelectedListener listener; // Reference to WordSearchActivity

    // ==================== CONSTRUCTOR ====================

    public WordSearchGridView(Context context, AttributeSet attrs) {
        super(context, attrs); // Call the parent View constructor - required by Android
        initPaints(); // Set up all paint objects
    }

    // ==================== PAINT SETUP ====================

    private void initPaints() {
        // We set up all paints here once so onDraw() doesn't create new objects every frame

        // Normal cell background - dark blue
        cellPaint = new Paint();
        cellPaint.setColor(Color.parseColor("#1A237E")); // Dark blue
        cellPaint.setStyle(Paint.Style.FILL); // Fill the entire rectangle with color

        // Selected cell - gold, semi-transparent so letter is still visible underneath
        selectedPaint = new Paint();
        selectedPaint.setColor(Color.parseColor("#FFD700")); // Gold
        selectedPaint.setStyle(Paint.Style.FILL);
        selectedPaint.setAlpha(150); // 0=fully transparent, 255=fully opaque, 150=semi-transparent

        // Found word cell - green, slightly more opaque than selection highlight
        foundPaint = new Paint();
        foundPaint.setColor(Color.parseColor("#2E7D32")); // Dark green
        foundPaint.setStyle(Paint.Style.FILL);
        foundPaint.setAlpha(180); // Slightly more visible than the gold selection

        // Letter text - white, centered, smooth edges
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER); // Center text horizontally in the cell
        textPaint.setAntiAlias(true); // Smooth the text edges so letters don't look jagged

        // Cell border - slightly lighter blue to visually separate cells
        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#283593")); // Slightly lighter than cell background
        borderPaint.setStyle(Paint.Style.STROKE); // Only draw the outline, not fill
        borderPaint.setStrokeWidth(1f); // 1 pixel border thickness
    }

    // ==================== PUBLIC METHODS ====================

    public void setGrid(char[][] grid, int gridSize) {
        // Called by WordSearchActivity after the grid is built with all words placed
        this.grid = grid; // Save the 2D letter array
        this.gridSize = gridSize; // Save the grid size (12)
        this.found = new boolean[gridSize][gridSize]; // All cells start as not found (false)
        invalidate(); // Tell Android to redraw this view with the new grid
    }

    public void setOnWordSelectedListener(OnWordSelectedListener listener) {
        // Called by WordSearchActivity to register itself as the listener
        // So when user selects letters we can call listener.onWordSelected()
        this.listener = listener;
    }

    public void markWordAsFound(int startRow, int startCol, int endRow, int endCol) {
        // Called by WordSearchActivity when selected letters match a hidden word
        // Marks every cell along the word's path as found so they turn green

        // Calculate which direction the word goes
        int rowDir = Integer.compare(endRow, startRow); // -1=up, 0=same row, 1=down
        int colDir = Integer.compare(endCol, startCol); // -1=left, 0=same col, 1=right

        int row = startRow;
        int col = startCol;

        // Walk along the word's path and mark each cell as found
        while (true) {
            // Make sure we're within bounds before marking
            if (row >= 0 && row < gridSize && col >= 0 && col < gridSize) {
                found[row][col] = true; // This cell will now draw green
            }
            if (row == endRow && col == endCol) break; // Reached the last letter - stop
            row += rowDir; // Move one step in the row direction
            col += colDir; // Move one step in the column direction
        }

        invalidate(); // Tell Android to redraw so the green cells appear
    }

    // ==================== SIZE CALCULATION ====================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Called automatically when Android knows the actual pixel size of this view
        // We use the smaller of width or height so cells are always square
        cellSize = Math.min(w, h) / (float) gridSize; // e.g. 480px wide / 12 = 40px per cell
        textPaint.setTextSize(cellSize * 0.5f); // Letter size = 50% of cell size so it fits nicely
    }

    // ==================== DRAWING ====================

    @Override
    protected void onDraw(Canvas canvas) {
        // Called every time this view needs to be redrawn
        // Android calls this after every invalidate() call
        // We loop through every cell and draw it with the right color and letter
        if (grid == null) return; // Grid not set yet - nothing to draw

        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {

                // Calculate the pixel boundaries of this cell
                float left = col * cellSize; // e.g. column 3 starts at 3 * 40 = 120px
                float top = row * cellSize; // e.g. row 2 starts at 2 * 40 = 80px
                float right = left + cellSize; // Cell ends at 120 + 40 = 160px
                float bottom = top + cellSize; // Cell ends at 80 + 40 = 120px

                RectF cellRect = new RectF(left, top, right, bottom); // Rectangle for this cell

                // Choose which background color to use for this cell
                if (found[row][col]) {
                    // Cell is part of a found word - draw green
                    canvas.drawRoundRect(cellRect, 4, 4, foundPaint);
                } else if (isTouching && isCellSelected(row, col)) {
                    // User is currently selecting this cell - draw gold
                    canvas.drawRoundRect(cellRect, 4, 4, selectedPaint);
                } else {
                    // Normal cell - draw dark blue
                    canvas.drawRoundRect(cellRect, 4, 4, cellPaint);
                }

                // Draw the cell border on top of the background
                canvas.drawRoundRect(cellRect, 4, 4, borderPaint);

                // Draw the letter centered in the cell
                float textX = left + cellSize / 2; // Horizontal center of cell
                // Vertical center adjusted using font metrics so text is perfectly centered
                // descent and ascent describe how tall letters are above and below the baseline
                float textY = top + cellSize / 2 - (textPaint.descent() + textPaint.ascent()) / 2;
                // Draw the letter in uppercase so the grid looks consistent
                canvas.drawText(String.valueOf(grid[row][col]).toUpperCase(), textX, textY, textPaint);
            }
        }
    }

    // ==================== SELECTION CHECK ====================

    private boolean isCellSelected(int row, int col) {
        // Returns true if this cell is on the line from startRow,startCol to endRow,endCol
        // Only horizontal and vertical selections are supported - diagonals are ignored
        if (startRow == -1 || endRow == -1) return false; // No touch active - nothing selected

        // If both row AND column changed it means the user is dragging diagonally
        // We don't support diagonal selection so return false immediately
        if (startRow != endRow && startCol != endCol) return false;

        // Calculate direction of the selection
        int rowDir = Integer.compare(endRow, startRow); // -1=up, 0=horizontal, 1=down
        int colDir = Integer.compare(endCol, startCol); // -1=left, 0=vertical, 1=right

        // If start and end are the same cell just check if this cell matches
        if (rowDir == 0 && colDir == 0) {
            return row == startRow && col == startCol;
        }

        int r = startRow;
        int c = startCol;

        // Walk along the path step by step and check if this cell is on it
        // Since we already checked it's horizontal or vertical this will always terminate
        while (true) {
            if (r == row && c == col) return true; // Cell is on the path
            if (r == endRow && c == endCol) break; // Reached the end without finding cell
            r += rowDir; // Move one step
            c += colDir;

            // Safety check - stop if we somehow go out of bounds
            if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) break;
        }
        return false; // Cell is not on the selection path
    }

    // ==================== TOUCH HANDLING ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Called every time user touches, moves, or lifts finger on this view
        if (grid == null) return false; // Grid not ready - ignore touches

        // Convert pixel X and Y coordinates to grid column and row numbers
        // e.g. if user touches at pixel 130 and cellSize is 40, they're in column 130/40 = 3
        int col = (int) (event.getX() / cellSize);
        int row = (int) (event.getY() / cellSize);

        // Clamp to valid grid range so dragging outside the grid doesn't crash
        // Math.max(0, ...) prevents negative values
        // Math.min(..., gridSize-1) prevents values >= gridSize
        col = Math.max(0, Math.min(col, gridSize - 1));
        row = Math.max(0, Math.min(row, gridSize - 1));

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: // User just touched the screen - finger went down
                startRow = row; // Remember where the selection started
                startCol = col;
                endRow = row; // End starts same as start until they drag
                endCol = col;
                isTouching = true; // Mark touch as active
                invalidate(); // Redraw to show the single highlighted cell
                break;

            case MotionEvent.ACTION_MOVE: // User is dragging their finger
                endRow = row; // Update end position as finger moves
                endCol = col;
                invalidate(); // Redraw to update the gold highlight path
                break;

            case MotionEvent.ACTION_UP: // User lifted their finger - selection is done
                isTouching = false; // Touch is no longer active

                // Only process the selection if it is horizontal or vertical
                // If both row AND column changed it is a diagonal drag - we ignore it
                // This prevents the crash that happened with diagonal dragging
                if (startRow == endRow || startCol == endCol) {
                    String selectedWord = buildSelectedWord(); // Build the word from selected letters
                    if (listener != null && !selectedWord.isEmpty()) {
                        listener.onWordSelected(selectedWord); // Notify WordSearchActivity
                    }
                }

                // Reset selection so nothing stays highlighted
                startRow = -1;
                startCol = -1;
                endRow = -1;
                endCol = -1;
                invalidate(); // Redraw to remove the gold highlight
                break;
        }

        return true; // true = we handled this touch event, don't pass it to other views
    }

    // ==================== BUILD SELECTED WORD ====================

    private String buildSelectedWord() {
        // Collects all letters from startRow,startCol to endRow,endCol and returns them as a string
        // This is called when the user lifts their finger after dragging
        if (startRow == -1 || endRow == -1) return ""; // No selection - return empty

        // Only allow horizontal or vertical - return empty string for diagonals
        // This is the key fix that prevents the infinite loop crash from diagonal dragging
        // Before this fix, a diagonal drag where row distance != column distance
        // would cause the walker to never reach endRow AND endCol at the same time
        if (startRow != endRow && startCol != endCol) return "";

        // Calculate direction of the selection
        int rowDir = Integer.compare(endRow, startRow); // -1=up, 0=horizontal, 1=down
        int colDir = Integer.compare(endCol, startCol); // -1=left, 0=vertical, 1=right

        // If start and end are the same cell return just that one letter
        if (rowDir == 0 && colDir == 0) {
            return String.valueOf(grid[startRow][startCol]).toLowerCase();
        }

        StringBuilder word = new StringBuilder(); // More efficient than String + for building strings
        int r = startRow;
        int c = startCol;

        // Walk from start to end collecting each letter
        // Since we checked it's horizontal or vertical this loop always terminates cleanly
        while (true) {
            // Safety check - stop if we somehow go out of bounds
            if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) break;

            word.append(grid[r][c]); // Add this cell's letter to the word

            if (r == endRow && c == endCol) break; // Reached the last letter - stop

            r += rowDir; // Move one step in row direction
            c += colDir; // Move one step in column direction
        }

        return word.toString().toLowerCase(); // Return as lowercase so comparison with word list works
    }

    // ==================== GETTERS ====================

    // These are used by WordSearchActivity to get start and end positions
    // after a word is found so it can call markWordAsFound()
    public int getStartRow() { return startRow; }
    public int getStartCol() { return startCol; }
    public int getEndRow() { return endRow; }
    public int getEndCol() { return endCol; }
}