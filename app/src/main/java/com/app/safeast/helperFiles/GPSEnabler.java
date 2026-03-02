package com.app.safeast.helperFiles;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

/**
 * Utility class to check if GPS/Location is enabled and prompt user to enable it if not.
 * Shows the native Android system dialog for turning on location.
 */
public class GPSEnabler {

    private static final int GPS_ENABLE_REQUEST_CODE = 9001;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Check if GPS is enabled. If not, show system dialog to enable it.
     * This version polls for GPS status after showing the dialog.
     *
     * @param context Must be an Activity context to show the dialog
     * @param onEnabled Callback that runs if GPS is already enabled OR after user enables it
     * @param onDisabled Callback that runs if user declines to enable GPS
     */
    public static void checkAndEnableGPS(Context context,
                                         Runnable onEnabled,
                                         Runnable onDisabled) {

        if (!(context instanceof Activity)) {
            Log.e("GPSEnabler", "Context must be an Activity to show GPS dialog");
            if (onDisabled != null) onDisabled.run();
            return;
        }

        Activity activity = (Activity) context;

        // Create a location request
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(5000);

        // Build location settings request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true);  // Always show the dialog even if location is on

        // Check location settings
        SettingsClient client = LocationServices.getSettingsClient(context);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(locationSettingsResponse -> {
            // GPS is already enabled
            Log.d("GPSEnabler", "✅ GPS is already enabled");
            if (onEnabled != null) onEnabled.run();
        });

        task.addOnFailureListener(exception -> {
            if (exception instanceof ResolvableApiException) {
                // GPS is disabled, show dialog to enable it
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) exception;

                    Log.d("GPSEnabler", "📍 Showing GPS enable dialog");

                    // Show the system dialog
                    resolvable.startResolutionForResult(activity, GPS_ENABLE_REQUEST_CODE);

                    // Poll for GPS status after a delay
                    // Start polling immediately to detect state change
                    pollForGPSEnabled(context, onEnabled, onDisabled, 0);

                } catch (IntentSender.SendIntentException e) {
                    Log.e("GPSEnabler", "Error showing GPS dialog", e);
                    if (onDisabled != null) onDisabled.run();
                }
            } else {
                // Can't enable GPS automatically
                int statusCode = ((ApiException) exception).getStatusCode();
                Log.e("GPSEnabler", "GPS error, status code: " + statusCode);
                if (onDisabled != null) onDisabled.run();
            }
        });
    }

    /**
     * Poll for GPS enabled status after showing the dialog.
     * Checks every 500ms for up to 30 seconds.
     */
    private static void pollForGPSEnabled(Context context, Runnable onEnabled, Runnable onDisabled, int attemptCount) {
        final int MAX_ATTEMPTS = 60; // 30 seconds (60 * 500ms)

        if (attemptCount >= MAX_ATTEMPTS) {
            Log.d("GPSEnabler", "❌ GPS not enabled after timeout");
            if (onDisabled != null) onDisabled.run();
            return;
        }

        handler.postDelayed(() -> {
            // Use a fresh builder and request to check status
            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
                    .setAlwaysShow(false); // We just want to check, not prompt again

            SettingsClient client = LocationServices.getSettingsClient(context);
            Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

            task.addOnSuccessListener(response -> {
                // GPS is now enabled!
                Log.d("GPSEnabler", "✅ GPS enabled successfully after " + attemptCount + " attempts");
                if (onEnabled != null) onEnabled.run();
            });

            task.addOnFailureListener(exception -> {
                // Still not enabled, keep polling
                // Only continue polling if it's still a resolvable error (meaning GPS is just OFF)
                if (exception instanceof ResolvableApiException) {
                    Log.d("GPSEnabler", "⏳ GPS still disabled, attempt " + (attemptCount + 1));
                    pollForGPSEnabled(context, onEnabled, onDisabled, attemptCount + 1);
                } else {
                    Log.e("GPSEnabler", "Polling failed with non-resolvable error");
                    if (onDisabled != null) onDisabled.run();
                }
            });

        }, 1000); // Check every 1 second (increased from 500ms for stability)
    }

    /**
     * Simpler version - just check and show dialog, run callback regardless
     * Good for cases where you'll check location permission again anyway
     */
    public static void promptEnableGPS(Context context, Runnable callback) {
        checkAndEnableGPS(context, callback, callback);
    }
}