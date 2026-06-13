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


    // Interface = a contract - whoever uses this must provide this method
    // WordSearchActivity implements this so it gets notified when user finishes selecting
    public interface OnWordSelectedListener {
        void onWordSelected(String word); // Called when user lifts finger after dragging
    }


    private char[][] grid; // The 2D array holding all letters - grid[row][col]
    private boolean[][] found; // Tracks which cells belong to a found word - true = green
    private int gridSize = 12; // The grid is 12x12 cells
    private float cellSize; // The size of each cell in pixels - calculated when view size is known


    private int startRow = -1; // Row where user started touching - -1 means no touch yet
    private int startCol = -1; // Column where user started touching
    private int endRow = -1; // Row where user's finger currently is while dragging
    private int endCol = -1; // Column where user's finger currently is while dragging
    private boolean isTouching = false; // true = user is currently dragging their finger


    // Paint objects store drawing settings like color and style
    // We create them once here instead of inside onDraw() to avoid
    // creating new objects every frame which would slow down the app
    private Paint cellPaint; // Draws normal cell backgrounds - dark blue
    private Paint selectedPaint; // Draws cells being selected - gold highlight
    private Paint foundPaint; // Draws cells that are part of a found word - green
    private Paint textPaint; // Draws the letters inside each cell - white
    private Paint borderPaint; // Draws the border around each cell


    private OnWordSelectedListener listener; // Reference to WordSearchActivity


    public WordSearchGridView(Context context, AttributeSet attrs) {
        super(context, attrs); // Call the parent View constructor - required by Android
        initPaints(); // Set up all paint objects
    }


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


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        //התאמת הרשת לכל טלפון
        cellSize = Math.min(w, h) / (float) gridSize; //כמה פיקסלים לכל תא
        textPaint.setTextSize(cellSize * 0.5f); // כל אות תתפוס חצי מהשטח של התא
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (grid == null) return; // אם עדיין לא יצרנו רשת

        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {

                // הפיכת הפיקסלים לריבוע הפיזי שמתאר את התא
                float left = col * cellSize;
                float top = row * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                RectF cellRect = new RectF(left, top, right, bottom); // יצירת תא

                // בחירת הצבע לתא הזה
                if (found[row][col]) {
                    // אם זה נמצא כבר נסמן בירוק
                    canvas.drawRoundRect(cellRect, 4, 4, foundPaint);
                } else if (isTouching && isCellSelected(row, col)) {
                    // אם עכשיו לוחצים על התא הזה נצבע אותו בזהב
                    canvas.drawRoundRect(cellRect, 4, 4, selectedPaint);
                } else {
                    // אם הוא תא רגיל נצבע אותו בכחול
                    canvas.drawRoundRect(cellRect, 4, 4, cellPaint);
                }

                // נצבע היקף של תא
                canvas.drawRoundRect(cellRect, 4, 4, borderPaint);

                // ציור האות בדיוק במרכז התא
                float textX = left + cellSize / 2;
                float textY = top + cellSize / 2 - (textPaint.descent() + textPaint.ascent()) / 2;
                // אות גדולה
                canvas.drawText(String.valueOf(grid[row][col]).toUpperCase(), textX, textY, textPaint);
            }
        }
    }


    private boolean isCellSelected(int row, int col) {
        //מחזיר אמת אם התא הוא חלק ממסלול הגרירה של המשתמש
        if (startRow == -1 || endRow == -1) return false; // אם אין לחיצה על תא

        //אם המשתמש גורר באלכסון
        if (startRow != endRow && startCol != endCol) return false;

        // בשורות אם האצבע זזה למטה נקבל 1, אם למעלה נקבל 1-, ואם היא נשארה באותה שורה נקבל 0
        //בעמודות  אם האצבע זזה ימינה נקבל 1, שמאלה נקבל 1-, ואם באותה עמודה נקבל 0
        int rowDir = Integer.compare(endRow, startRow);
        int colDir = Integer.compare(endCol, startCol);

        // תא נחשב מסומן אם הוא לא סימן אלכסוני
        if (rowDir == 0 && colDir == 0) {
            return row == startRow && col == startCol;
        }

        int r = startRow;
        int c = startCol;

        //עוברים לאורך מסלול ובודקים אם התא הוא חלק ממנו
        while (true) {
            if (r == row && c == col) return true; // התא במסלול הגרירה
            if (r == endRow && c == endCol) break; // תעצור ברגע שהגענו לתא שהמשתמש הרים או הניח את האצבע
            r += rowDir; // תתקדם שורה
            c += colDir;// תתקדם עמודה

            //אם יצאנו מגבולות הרשת נעצור
            if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) break;
        }
        return false; // אל תצבע בזהב, הוא לא חלק מהמסלול
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // קריאה בכל פען שהמשתמש נוגע ברשת
        if (grid == null) return false;

        //המרת הפיקסלים לעמודות ושורות
        int col = (int) (event.getX() / cellSize);
        int row = (int) (event.getY() / cellSize);

        //מניעת גרירה מעבר לרשת מה שיגרום לקריסה
        col = Math.max(0, Math.min(col, gridSize - 1));
        row = Math.max(0, Math.min(row, gridSize - 1));

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: // לחיצה על המסך
                startRow = row; // שמירת השורה ההתחלתית
                startCol = col;
                endRow = row; // מתחיל זהה להתחלה עד שגוררים
                endCol = col;
                isTouching = true; // המשתמש גורר
                invalidate(); // ציור המסך מחדש ועדכון כתוצאה מהלחיצה
                break;

            case MotionEvent.ACTION_MOVE: // גרירה של האצבע על המסך
                endRow = row; // עדכון בזמן אמת
                endCol = col;
                invalidate(); //ציור המסך מחדש כתוצאה מהגרירה
                break;

            case MotionEvent.ACTION_UP: // המשתמש הרים את האצבע מהמסך
                isTouching = false; // כבר לא נוגע במסך

                //אם גררנו מילה בכיוונים
                if (startRow == endRow || startCol == endCol) {
                    String selectedWord = buildSelectedWord(); // צור מילה
                    if (listener != null && !selectedWord.isEmpty()) {
                        listener.onWordSelected(selectedWord); // עדכון בהתאם ללחיצה
                    }
                }

                // איפוס מיקומים
                startRow = -1;
                startCol = -1;
                endRow = -1;
                endCol = -1;
                invalidate(); // ציור מחדש למחיקת הצבע הזהב
                break;
        }

        return true; // טיפנו בלחיצה הזאת
    }


    private String buildSelectedWord() {
        //נקרא כשהמשתמש מרים את האצבע וזה יוצר מילה
        if (startRow == -1 || endRow == -1) return ""; // לא בחרנו כלום, תחזיר ריק

        //מאפשר רק גרירה באותו כיוון ולא אלכסון, אלכסון לא ייצור מילה
        if (startRow != endRow && startCol != endCol) return "";

        // בשורות אם האצבע זזה למטה נקבל 1, אם למעלה נקבל 1-, ואם היא נשארה באותה שורה נקבל 0
        //בעמודות  אם האצבע זזה ימינה נקבל 1, שמאלה נקבל 1-, ואם באותה עמודה נקבל 0
        int rowDir = Integer.compare(endRow, startRow);
        int colDir = Integer.compare(endCol, startCol);

        // מחזירים רק את האות אם סומן רק תא אחד
        if (rowDir == 0 && colDir == 0) {
            return String.valueOf(grid[startRow][startCol]).toLowerCase();
        }

        StringBuilder word = new StringBuilder(); // בניית המחרוזת, יעיל יותר משרשור
        int r = startRow;
        int c = startCol;

        while (true) {
            // בדיקה אם אנחנו בגבולות
            if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) break;

            word.append(grid[r][c]); // הוספת האות למילה

            if (r == endRow && c == endCol) break; // הגענו לאות האחרונה, מפסיקים

            r += rowDir; // מתקדמים שורה
            c += colDir; // מתקדמים עמודה
        }

        return word.toString().toLowerCase(); // החזרת המילה
    }



    public int getStartRow() { return startRow; }
    public int getStartCol() { return startCol; }
    public int getEndRow() { return endRow; }
    public int getEndCol() { return endCol; }
}