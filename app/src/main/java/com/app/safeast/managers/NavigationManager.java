package com.app.safeast.managers;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.app.safeast.BuildConfig;
import com.app.safeast.helperFiles.MapHelper;
import com.app.safeast.objects.Shelter;
import com.app.safeast.objects.UserMarker;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.maps.routing.v2.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NavigationManager {
    private Context context;
    private GoogleMap googleMap;
    private OkHttpClient client;
    private RoutesClient routingClient;
    private HashMap<Integer, Shelter> shelterMap;
    private UserMarker user;
    private FusedLocationProviderClient fusedLocationClient;

    private volatile boolean alertTimeFetched = false;
    private final Object alertTimeLock = new Object();


    public NavigationManager(Context context, GoogleMap googleMap) {
        this.context = context;
        this.googleMap = googleMap;
        this.client = new OkHttpClient();
        this.shelterMap = new HashMap<>();
        this.user = null;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void giveDirections(Marker destMarker) {
        if (destMarker == null || googleMap == null || user == null) {
            Log.e("NavigationManager", "Cannot give directions: missing data");
            return;
        }

        LatLng origin = user.getLocation();
        LatLng destination = destMarker.getPosition();

        String apiKey = com.app.safeast.BuildConfig.DIRECTIONS_API_KEY;
        String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&mode=walking" +
                "&key=" + apiKey;

        Request request = new Request.Builder().url(urlString).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NavigationManager", "Failed to fetch directions", e);
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Failed to load directions", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    String status = json.getString("status");

                    if (!status.equals("OK")) {
                        Log.e("NavigationManager", "Directions API Error: " + status);
                        return;
                    }

                    // 4. Extract the Polyline String
                    JSONArray routes = json.getJSONArray("routes");
                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                        String encodedString = overviewPolyline.getString("points");

                        // 5. Decode and Draw on Main Thread
                        List<LatLng> points = MapHelper.decodePoly(encodedString);

                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(() -> {
                               MapHelper.drawRouteOnMap(points, origin, destination,googleMap);
                            });
                        }
                    }

                } catch (JSONException e) {
                    Log.e("NavigationManager", "JSON Error", e);
                }
            }
        });
    }


    public void sheltersNearGPS(ActivityResultLauncher<String> locationPermissionRequest, MapView mapView, Activity activity) {
        // First, check if we have permission to access location
        clearMarkers();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // You have permission. Get the location.
            fusedLocationClient.getLastLocation().addOnSuccessListener(activity, location -> {
                if (location != null) {
                    // Location found. Create a LatLng object.
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    // Clear previous markers, add a new one, and move the camera
                    if (user == null) {
                        user = new UserMarker(currentLatLng, googleMap);
                    } else {
                        user.setLocation(currentLatLng, googleMap);
                    }
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f)); // Zoom in closer
                    getShelters(mapView, activity);
                } else {
                    Toast.makeText(context, "Could not get location. Make sure location is enabled on the device.", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // You do not have permission. Request it from the user.
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }


    public void sheltersNearSearch(SearchView searchView, MapView mapView, Activity activity) {
        String locationName = searchView.getQuery().toString();
        if (locationName.isEmpty()) {
            Toast.makeText(context, "Please enter a location to search", Toast.LENGTH_SHORT).show();
            return;
        }
        clearMarkers();
        // Geocoder can be slow and should ideally be run in a background thread,
        // but for simplicity, we'll do it on the main thread here.
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            // getFromLocationName() returns a list of possible addresses. We take the first one.
            List<Address> addressList = geocoder.getFromLocationName(locationName, 1);
            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                LatLng searchedLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                // Clear previous markers, add a new one, and move the camera
                if (user == null) {
                    user = new UserMarker(searchedLatLng, googleMap);
                } else {
                    user.setLocation(searchedLatLng, googleMap);
                }
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 16f));
                getShelters(mapView, activity);
            } else {
                // No address found
                Toast.makeText(context, "Location not found. Try being more specific.", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e("MapFragment", "Geocoder service not available", e);
            Toast.makeText(context, "Could not connect to Geocoding service", Toast.LENGTH_LONG).show();
        }
    }

    private void clearMarkers() {
        if (!shelterMap.isEmpty()) {
            for (Shelter shelter : shelterMap.values()) {
                shelter.remove();
            }
            shelterMap.clear();
            user.getMarker().remove();
        }
        Shelter.resetCount();
    }


    private void getShelters(MapView mapView, Activity activity) {
        LatLng center = user.getLocation();
        MapHelper.Coord centerWPS = MapHelper.convertEPSG(center.longitude, center.latitude, MapHelper.EPSG.GPS.getLabel(), MapHelper.EPSG.WPS.getLabel());
        double centerY = centerWPS.mapY;
        double centerX = centerWPS.mapX;

        int width = mapView.getWidth();
        int height = mapView.getHeight();

        VisibleRegion vRegion = googleMap.getProjection().getVisibleRegion();
        LatLng ne = vRegion.farRight;
        LatLng sw = vRegion.nearLeft;

        MapHelper.Coord neMeters = MapHelper.convertEPSG(ne.longitude, ne.latitude, MapHelper.EPSG.GPS.getLabel(), MapHelper.EPSG.WPS.getLabel());
        MapHelper.Coord swMeters = MapHelper.convertEPSG(sw.longitude, sw.latitude, MapHelper.EPSG.GPS.getLabel(), MapHelper.EPSG.WPS.getLabel());

        double metersPerPixelX = (neMeters.mapX - swMeters.mapX) / width;
        double metersPerPixelY = (neMeters.mapY - swMeters.mapY) / height;

        double[] bbox = computeBbox(centerX, centerY, width, height, metersPerPixelX, metersPerPixelY);

        fetchAlertTime(centerX, centerY, activity, new AlertTimeCallback() {
            @Override
            public void onTimeFetched(int seconds) {
                synchronized (alertTimeLock) {
                    alertTimeFetched = true;
                    alertTimeLock.notifyAll();
                }
                Log.d("NavigationManager", "Alert time fetched: " + seconds);
            }
            @Override
            public void onError(String message) {
                synchronized (alertTimeLock) {
                    alertTimeFetched = true;
                    alertTimeLock.notifyAll();
                }
                Log.e("NavigationManager", "Error fetching alert time: " + message);
            }
        });

        fetchWmsImage(bbox, mapView, new DotResultCallback() {
            @Override
            public void onDotsReady(List<MapHelper.Coord> dots) {
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    for (MapHelper.Coord dot : dots) {
                        LatLng dotLatLng = new LatLng(dot.mapY, dot.mapX);
                        Shelter shelter = new Shelter(dotLatLng, googleMap);
                        shelterMap.put(shelter.getId(), shelter);
                    }
                    Log.d("NavigationManager", "Added " + shelterMap.size() + " shelters. Filtering...");
                    filterShelters(activity, new ShelterFilterCallback() {
                        @Override
                        public void onSheltersFiltered(List<ShelterResult> reachableShelters) {
                            if (!reachableShelters.isEmpty()) {
                                ShelterResult nearest = reachableShelters.get(0);
                                Set<Integer> reachableIds = reachableShelters.stream().map(s -> s.shelter.getId()).collect(Collectors.toSet());

                                // Color all reachable shelters blue
                                for (ShelterResult result : reachableShelters) {
                                    result.shelter.getMarker().setIcon(
                                            BitmapDescriptorFactory.defaultMarker(
                                                    200));
                                    result.shelter.getMarker().setTitle(
                                            result.shelter.getName() + " - " +
                                                    result.getFormattedTime());
                                }

                                for (Shelter shelter : shelterMap.values()) {
                                    if(!reachableIds.contains(shelter.getId()))
                                    {
                                        shelter.remove();
                                    }
                                }

                                // Color nearest green and show info
                                nearest.shelter.getMarker().setIcon(
                                        BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_GREEN));
                                nearest.shelter.getMarker().showInfoWindow();

                                Toast.makeText(context,
                                        "Nearest: " + nearest.getFormattedTime() +
                                                " (" + nearest.getFormattedDistance() + ")",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(context,
                                        "⚠️ No shelters reachable within " +
                                                user.getReactionTimeSec() + "s!",
                                        Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(context,
                                    "Error filtering: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            Log.e("NavigationManager", "Filter error: " + e.getMessage());
                        }
                    });
                });
            }
            @Override
            public void onError(Exception e) {
                Log.e("MapFragment", "Error fetching WMS image", e);
            }
        });
    }


public interface DotResultCallback {
    void onDotsReady(List<MapHelper.Coord> dots);

    void onError(Exception e);
}

    public interface ShelterFilterCallback {
        void onSheltersFiltered(List<ShelterResult> reachableShelters);
        void onError(Exception e);
    }

    // Make ShelterResult public so callback can use it
    public static class ShelterResult implements Comparable<ShelterResult> {
        public Shelter shelter;
        public int travelTime;
        public double distance;

        public ShelterResult(Shelter shelter, int travelTime, double distance) {
            this.shelter = shelter;
            this.travelTime = travelTime;
            this.distance = distance;
        }

        public Shelter getShelter() {
            return shelter;
        }

        public int getTravelTime() {
            return travelTime;
        }

        public double getDistance() {
            return distance;
        }

        public String getFormattedTime() {
            int minutes = travelTime / 60;
            int seconds = travelTime % 60;
            return minutes + ":" + String.format("%02d", seconds);
        }

        public String getFormattedDistance() {
            if (distance < 1000) {
                return (int)distance + "m";
            } else {
                return String.format("%.1f", distance / 1000.0) + "km";
            }
        }

        @Override
        public int compareTo(ShelterResult shelterResult) {
            return Integer.compare(this.travelTime, shelterResult.travelTime);
        }
    }


    public void filterShelters(Activity activity, ShelterFilterCallback callback) {
        new Thread(() -> {
            // Wait for alert time to complete
            synchronized (alertTimeLock) {
                long startTime = System.currentTimeMillis();
                while (!alertTimeFetched && (System.currentTimeMillis() - startTime) < 10000) {
                    try {
                        alertTimeLock.wait(10000);
                    } catch (InterruptedException e) {
                        activity.runOnUiThread(() ->
                                callback.onError(new Exception("Interrupted")));
                        return;
                    }
                }
            }

            if (!alertTimeFetched) {
                activity.runOnUiThread(() ->
                        callback.onError(new Exception("Alert time timeout")));
                return;
            }

            if (user == null || user.getReactionTimeSec() <= 0) {
                activity.runOnUiThread(() ->
                        callback.onError(new Exception("Alert time not available")));
                return;
            }
            if (shelterMap.isEmpty()) {
                activity.runOnUiThread(() ->
                        callback.onError(new Exception("No shelters loaded")));
                return;
            }

            Log.d("NavigationManager", "Filtering " + shelterMap.size() +
                    " shelters with " + user.getReactionTimeSec() + "s limit");

            List<ShelterResult> results = Collections.synchronizedList(new ArrayList<>());
            List<Thread> threads = new ArrayList<>();

            // Process shelters in parallel (6 threads)
            List<Shelter> shelterList = new ArrayList<>(shelterMap.values());
            int threadsCount = 6;
            int sheltersPerThread = (int) Math.ceil((double) shelterList.size() / threadsCount);

            for (int i = 0; i < threadsCount; i++) {
                int start = i * sheltersPerThread;
                int end = Math.min(start + sheltersPerThread, shelterList.size());

                if (start >= shelterList.size()) break;

                List<Shelter> threadShelters = shelterList.subList(start, end);

                Thread thread = new Thread(() -> {
                    for (Shelter shelter : threadShelters) {
                        try {
                            ShelterResult result = calculateRoute(user.getLocation(), shelter);

                            if (result != null && result.getTravelTime() <= user.getReactionTimeSec()*2) {
                                results.add(result);
                                Log.d("ShelterFinder", "Reachable: " +
                                        shelter.getName() + " - " + result.getFormattedTime());
                            }
                        } catch (Exception e) {
                            Log.e("ShelterFinder", "Error processing " +
                                    shelter.getName() + ": " + e.getMessage());
                        }
                    }
                });

                threads.add(thread);
                thread.start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                try {
                    thread.join(10000); // 10 second timeout per thread
                } catch (InterruptedException e) {
                    Log.e("ShelterFinder", "Thread interrupted: " + e.getMessage());
                }
            }

            // Sort results by travel time
            Collections.sort(results);

            Log.d("ShelterFinder", "Found " + results.size() + "/" +
                    shelterMap.size() + " reachable shelters");

            activity.runOnUiThread(() -> callback.onSheltersFiltered(results));

        }).start();
    }



    /**
     * Calculate route using Google Directions API
     */
    private ShelterResult calculateRoute(LatLng origin, Shelter shelter) {
        try {
            LatLng destination = shelter.getLocation();

            // Build API URL
            String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + origin.latitude + "," + origin.longitude +
                    "&destination=" + destination.latitude + "," + destination.longitude +
                    "&mode=walking" +
                    "&key=" + BuildConfig.DIRECTIONS_API_KEY;

            Request request = new Request.Builder()
                    .url(urlString)
                    .build();

            // Synchronous call (we're already in a background thread)
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w("ShelterFinder", "API returned HTTP " + response.code());
                    return null;
                }

                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                String status = json.getString("status");

                if (!status.equals("OK")) {
                    String errorMessage = json.optString("error_message", "Unknown error");
                    Log.e("ShelterFinder", "Directions API failed with status: " + status + ". Message: " + errorMessage);
                    return null;
                }

                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() == 0) {
                    return null;
                }

                JSONObject route = routes.getJSONObject(0);
                JSONArray legs = route.getJSONArray("legs");
                JSONObject leg = legs.getJSONObject(0);

                // Extract duration and distance
                int travelTime = leg.getJSONObject("duration").getInt("value");
                double distance = leg.getJSONObject("distance").getDouble("value");

                return new ShelterResult(shelter, travelTime, distance);
            }

        } catch (Exception e) {
            Log.e("ShelterFinder", "Error calculating route for " + shelter.getName() + ": " + e.getMessage());
            return null;
        }
    }

    public interface AlertTimeCallback {
        void onTimeFetched(int seconds);
        void onError(String message);
    }


    private void fetchAlertTime(double centerX, double centerY, Activity activity, AlertTimeCallback callback) {
        alertTimeFetched = false; // Reset flag
        String url = "https://www.govmap.gov.il/api/layers-catalog/entitiesByPoint";
        String jsonBody = "{" + "\"point\":[" + centerX + "," + centerY + "]," + "\"layers\":[{\"layerId\":\"427\"},{\"layerId\":\"417\"}]," + "\"tolerance\":277.8130556261113" + "}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).addHeader("accept", "application/json, text/plain, */*").addHeader("content-type", "application/json").addHeader("accept-language", "he,he-IL;q=0.9,en-US;q=0.8,en;q=0.7").addHeader("referer", "https://www.govmap.gov.il/?z=6&c=180726.75,573949.65&lay=427,417&b=7&bs=427,417%7C179775.17,577426.46").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("MapFragment", "Error fetching alert time", e);
                if (activity != null) {
                    activity.runOnUiThread(() -> {Toast.makeText(context, "Could not connect to alert time service", Toast.LENGTH_LONG).show();
                    if(callback != null) callback.onError(e.getMessage());
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (activity == null) {
                    return;
                }
                final String responseBody = response.body().string();
                Log.d("MapFragment", "Alert time response: " + responseBody);

                if (!response.isSuccessful()) {
                    Log.e("MapFragment", "HTTP " + response.code());
                    activity.runOnUiThread(() -> Toast.makeText(context, "Could not connect to alert time service. Code: " + response.code(), Toast.LENGTH_LONG).show());
                    return;
                }

                activity.runOnUiThread(() -> {
                    try {
                        int seconds = formatTime(responseBody);
                        if (seconds > -1) {
                            user.setReactionTimeSec(seconds);
                            Toast.makeText(context, "Alert time: " + seconds + " seconds", Toast.LENGTH_LONG).show();
                            if(callback != null) callback.onTimeFetched(seconds);
                        } else {
                            Toast.makeText(context, "Could not determine alert time.", Toast.LENGTH_SHORT).show();
                            if(callback != null) callback.onError("Could not determine alert time.");
                        }
                    } catch (JSONException e) {
                        if (callback != null) callback.onError("JSON Error");
                        Log.e("MapFragment", "Error parsing alert time JSON", e);
                        Toast.makeText(context, "Error reading alert time data.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private int formatTime(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        JSONArray dataArray = object.getJSONArray("data");

        if (dataArray.length() > 0) {
            // Assuming 'defensetimezones' is always the first object in the 'data' array
            JSONObject defenseTimeZones = dataArray.getJSONObject(0);
            JSONArray entitiesArray = defenseTimeZones.getJSONArray("entities");

            if (entitiesArray.length() > 0) {
                JSONObject firstEntity = entitiesArray.getJSONObject(0);
                JSONArray fieldsArray = firstEntity.getJSONArray("fields");

                if (fieldsArray.length() > 0) {
                    // Assuming the time information is always the first field
                    JSONObject timeField = fieldsArray.getJSONObject(0);
                    String time = timeField.getString("fieldValue");

                    if (time.contains("דקה וחצי")) {
                        return 90;
                    }
                    if (time.contains("שלוש דקות")) {
                        return 180;
                    }
                    if (time.contains("דקה")) {
                        return 60;
                    }
                    if (time.contains("שניות")) {
                        Pattern pattern = Pattern.compile("(\\d+)");
                        Matcher matcher = pattern.matcher(time);
                        if (matcher.find() && matcher.group(1) != null) {
                            try {
                                return Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                            } catch (NumberFormatException e) {
                                // Failed to parse, return 0
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    private double[] computeBbox(double centerX, double centerY, int width, int height, double metersPerPixelX, double metersPerPixelY) {
        double halfWidth = width * metersPerPixelX / 2.0;
        double halfHeight = height * metersPerPixelY / 2.0;
        return new double[]{centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight};
    }


    private void fetchWmsImage(double[] bbox, MapView mapView, DotResultCallback callback) {
        double minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];

        String url = "https://www.govmap.gov.il/api/geoserver/ows/public/?" + "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&FORMAT=image/png&TRANSPARENT=true" + "&LAYERS=govmap:layer_bombshelters&TILED=false&CRS=EPSG:3857" + "&STYLES=govmap:layer_bombshelters&FEATUREVERSION=1" + "&WIDTH=" + mapView.getWidth() + "&HEIGHT=" + mapView.getHeight() + "&BBOX=" + minX + "," + minY + "," + maxX + "," + maxY;

        Request request = new Request.Builder().url(url).addHeader("accept", "image/png,*/*;q=0.8").build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }

                Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                if (bitmap == null) {
                    callback.onError(new IOException("Decode error"));
                    return;
                }

                List<MapHelper.Coord> dots = MapHelper.findDotCenters(bitmap, minX, minY, maxX, maxY);

                callback.onDotsReady(dots);
            }
        });
    }


}
