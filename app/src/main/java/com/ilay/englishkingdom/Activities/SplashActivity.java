package com.ilay.englishkingdom.Activities;

import android.content.Context; // Used to check network connectivity
import android.content.Intent; // Used to navigate to LoginActivity
import android.net.ConnectivityManager; // Used to get network connection info
import android.net.NetworkInfo; // Used to check if network is connected
import android.os.Bundle; // Used when creating the activity
import android.os.Handler; // Used to delay navigation after splash
import android.os.Looper; // Used with Handler to run on main thread

import androidx.appcompat.app.AlertDialog; // Used to show the no internet dialog
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.ilay.englishkingdom.R; // Used to reference XML resources

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Check internet as soon as splash screen appears
        checkInternetAndProceed();
    }

    private void checkInternetAndProceed() {
        // Check if the device currently has an active internet connection
        if (isInternetAvailable()) {
            // Internet is available - wait 3 seconds then go to LoginActivity
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish(); // Close splash so user can't go back to it
            }, 3000); // 3000 milliseconds = 3 seconds
        } else {
            // No internet - show a non-dismissible dialog
            // The user cannot enter the app until they have internet
            showNoInternetDialog();
        }
    }

    private boolean isInternetAvailable() {
        // ConnectivityManager is Android's system service for network info
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false; // Service not available - assume no internet

        // getActiveNetworkInfo() returns info about the currently active network
        // Returns null if there is no active network at all
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        // isConnected() returns true only if the network is connected and available
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void showNoInternetDialog() {
        // Shows a non-dismissible dialog telling the user there is no internet
        // The only option is "Try Again" which re-runs the internet check
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("English Kingdom requires an internet connection to work.\n\nPlease connect to the internet and try again.")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // User tapped Try Again - check internet again
                    checkInternetAndProceed();
                })
                .setCancelable(false) // Cannot dismiss by tapping outside or pressing back
                .show();
    }
}