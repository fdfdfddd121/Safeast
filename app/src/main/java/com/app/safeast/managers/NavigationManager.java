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

import com.app.safeast.R;
import com.app.safeast.helperFiles.DotDetector;
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
import com.google.maps.routing.v2.ComputeRoutesRequest;
import com.google.maps.routing.v2.ComputeRoutesResponse;
import com.google.maps.routing.v2.RouteTravelMode;
import com.google.maps.routing.v2.RoutesClient;

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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


    public NavigationManager(Context context, GoogleMap googleMap) {
        this.context = context;
        this.googleMap = googleMap;
        this.client = new OkHttpClient();
        this.shelterMap = new HashMap<>();
        this.user = null;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
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
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)); // Zoom in closer
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
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 15f));
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
    }


    private void getShelters(MapView mapView, Activity activity) {
        LatLng center = user.getLocation();
        DotDetector.Coord centerWPS = DotDetector.convertEPSG(center.longitude, center.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.WPS.getLabel());
        double centerY = centerWPS.mapY;
        double centerX = centerWPS.mapX;

        int width = mapView.getWidth();
        int height = mapView.getHeight();

        VisibleRegion vRegion = googleMap.getProjection().getVisibleRegion();
        LatLng ne = vRegion.farRight;
        LatLng sw = vRegion.nearLeft;

        DotDetector.Coord neMeters = DotDetector.convertEPSG(ne.longitude, ne.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.WPS.getLabel());
        DotDetector.Coord swMeters = DotDetector.convertEPSG(sw.longitude, sw.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.WPS.getLabel());

        double metersPerPixelX = (neMeters.mapX - swMeters.mapX) / width;
        double metersPerPixelY = (neMeters.mapY - swMeters.mapY) / height;

        double[] bbox = computeBbox(centerX, centerY, width, height, metersPerPixelX, metersPerPixelY);

        fetchAlertTime(centerX, centerY, activity);

        fetchWmsImage(bbox, mapView, new DotResultCallback() {
            @Override
            public void onDotsReady(List<DotDetector.Coord> dots) {
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    for (DotDetector.Coord dot : dots) {
                        LatLng dotLatLng = new LatLng(dot.mapY, dot.mapX);
                        Shelter shelter = new Shelter(dotLatLng, googleMap);
                        shelterMap.put(shelter.getId(), shelter);
                    }
                    Log.d("NavigationManager", "Added " + shelterMap.size() + " shelters. Filtering...");

                });
            }
            @Override
            public void onError(Exception e) {
                Log.e("MapFragment", "Error fetching WMS image", e);
            }
        });
    }


public interface DotResultCallback {
    void onDotsReady(List<DotDetector.Coord> dots);

    void onError(Exception e);
}
    private static class ShelterResult implements Comparable<ShelterResult> {
        Shelter shelter;
        int travelTime;
        double distance;

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

        @Override
        public int compareTo(ShelterResult shelterResult) {
            return Integer.compare(this.travelTime, shelterResult.travelTime);
        }
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
                    "&key=" + context.getPackageManager().getApplicationInfo(context.getPackageName(),PackageManager.GET_META_DATA).metaData.getString("MAPS_API_KEY");

            Request request = new Request.Builder()
                    .url(urlString)
                    .addHeader("accept", "application/json")
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
                    Log.w("ShelterFinder", "API returned status: " + status);
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

    private void fetchAlertTime(double centerX, double centerY, Activity activity) {
        String url = "https://www.govmap.gov.il/api/layers-catalog/entitiesByPoint";
        String jsonBody = "{" + "\"point\":[" + centerX + "," + centerY + "]," + "\"layers\":[{\"layerId\":\"427\"},{\"layerId\":\"417\"}]," + "\"tolerance\":277.8130556261113" + "}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).addHeader("accept", "application/json, text/plain, */*").addHeader("content-type", "application/json").addHeader("accept-language", "he,he-IL;q=0.9,en-US;q=0.8,en;q=0.7").addHeader("referer", "https://www.govmap.gov.il/?z=6&c=180726.75,573949.65&lay=427,417&b=7&bs=427,417%7C179775.17,577426.46").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("MapFragment", "Error fetching alert time", e);
                if (activity != null) {
                    activity.runOnUiThread(() -> Toast.makeText(context, "Could not connect to alert time service", Toast.LENGTH_LONG).show());
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
                        } else {
                            Toast.makeText(context, "Could not determine alert time.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
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

                List<DotDetector.Coord> dots = DotDetector.findDotCenters(bitmap, minX, minY, maxX, maxY);

                callback.onDotsReady(dots);
            }
        });
    }


}
