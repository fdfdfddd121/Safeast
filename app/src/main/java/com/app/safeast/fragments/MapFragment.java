package com.app.safeast.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Toast;

import com.app.safeast.R;
import com.app.safeast.helperFiles.DotDetector;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    MapView mapView;
    GoogleMap googleMap;
    SearchView searchView;
    Button currLoc;
    HashMap<String, Marker> markers;
    private final OkHttpClient client=new OkHttpClient();

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> locationPermissionRequest;

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    // TODO: Rename and change types of parameters
    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MapFragment newInstance() {
        MapFragment fragment=new MapFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ADD THIS INITIALIZATION BLOCK
        fusedLocationClient=LocationServices.getFusedLocationProviderClient(requireActivity());

        // This handles the result of the permission request
        locationPermissionRequest=registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted->{
            if (isGranted) {
                // Permission is granted. Try to get the location again.
                getCurrentLocationAndPin();
            }
            else {
                // Permission is denied. Show a message to the user.
                Toast.makeText(getContext(), "Location permission denied. Cannot get current location.", Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView=view.findViewById(R.id.mapView);
        currLoc=view.findViewById(R.id.currLoc);
        searchView=view.findViewById(R.id.searchView);
        markers=new HashMap<>();
        if (mapView!=null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this); // Register the callback
        }
        searchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setQuery("", false);
                searchView.onActionViewExpanded();
                searchView.setIconified(false);
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                pinSearchedLocation();
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }

        });

        // --- ADD THE ONCLICK LISTENERS FOR YOUR BUTTONS ---

        // 1. For the "Current Location" button
        currLoc.setOnClickListener(v->{
            getCurrentLocationAndPin();
        });
    }

    /**
     * This method is called when the map is fully loaded and ready to be used.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap=map;

        // You can customize the map here
        googleMap.getUiSettings().setZoomControlsEnabled(true); // Show zoom buttons

        // Let's place a default marker on Beer Sheva and move the camera
        LatLng beerSheva=new LatLng(31.2530, 34.7915);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(beerSheva, 12));
    }

    /**
     * Handles the logic for the "Current Location" button.
     * It checks for permission and then gets the last known location.
     */
    private void getCurrentLocationAndPin() {
        // First, check if we have permission to access location
        if (markers.size()>1) {
            for (Marker marker : markers.values()) {
                marker.remove();
            }
            markers.clear();
        }
        if(markers.containsKey("USER")){
            getShelters();
        }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
                // You have permission. Get the location.
                fusedLocationClient.getLastLocation().addOnSuccessListener((Executor) requireActivity(), location->{
                    if (location!=null) {
                        // Location found. Create a LatLng object.
                        LatLng currentLatLng=new LatLng(location.getLatitude(), location.getLongitude());
                        // Clear previous markers, add a new one, and move the camera
                        googleMap.clear();
                        markers.put("USER", googleMap.addMarker(new MarkerOptions().position(currentLatLng).title("USER")));
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)); // Zoom in closer
                        getShelters();
                    }
                    else {
                        Toast.makeText(getContext(), "Could not get location. Make sure location is enabled on the device.", Toast.LENGTH_LONG).show();
                    }
                });
            }
            else {
                // You do not have permission. Request it from the user.
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
    }

    private void getShelters() {
        LatLng center=markers.get("USER").getPosition();
        DotDetector.Coord centerWPS=DotDetector.convertEPSG(center.longitude, center.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.GOOGLEMAPS.getLabel());
        double centerY=centerWPS.mapY;
        double centerX=centerWPS.mapX;

        int width=mapView.getWidth();
        int height=mapView.getHeight();

        double earthRadius=6378137; // meters
        VisibleRegion vRegion=googleMap.getProjection().getVisibleRegion();
        LatLng ne=vRegion.farRight;
        LatLng sw=vRegion.nearLeft;

        // Convert both corners to EPSG:3857
        DotDetector.Coord neMeters=DotDetector.convertEPSG(ne.longitude, ne.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.GOOGLEMAPS.getLabel());
        DotDetector.Coord swMeters=DotDetector.convertEPSG(sw.longitude, sw.latitude, DotDetector.EPSG.GPS.getLabel(), DotDetector.EPSG.GOOGLEMAPS.getLabel());

        // Compute meters per pixel
        double metersPerPixelX=(neMeters.mapX-swMeters.mapX)/width;
        double metersPerPixelY=(neMeters.mapY-swMeters.mapY)/height;

        // Compute bbox
        double[] bbox=computeBbox(centerX, centerY, width, height, metersPerPixelX, metersPerPixelY);

        fetchWmsImage(bbox, new DotResultCallback() {
            @Override
            public void onDotsReady(List<DotDetector.Coord> dots) {
                int i=0;
                for (DotDetector.Coord dot : dots) {
                    LatLng dotLatLng=new LatLng(dot.mapX, dot.mapY);
                    markers.put("shelter"+i, googleMap.addMarker(new MarkerOptions().position(dotLatLng).title("shelter"+i).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))));
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e("MapFragment", "Error fetching WMS image", e);
            }
        });
    }

    /**
     * Handles the logic for the "Pin Searched Location" button.
     * It uses Geocoder to convert the text address into coordinates.
     */
    private void pinSearchedLocation() {
        String locationName=searchView.getQuery().toString();
        if (locationName.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a location to search", Toast.LENGTH_SHORT).show();
            return;
        }

        // Geocoder can be slow and should ideally be run in a background thread,
        // but for simplicity, we'll do it on the main thread here.
        Geocoder geocoder=new Geocoder(getContext(), Locale.getDefault());
        try {
            // getFromLocationName() returns a list of possible addresses. We take the first one.
            List<Address> addressList=geocoder.getFromLocationName(locationName, 1);
            if (addressList!=null && !addressList.isEmpty()) {
                Address address=addressList.get(0);
                LatLng searchedLatLng=new LatLng(address.getLatitude(), address.getLongitude());

                // Clear previous markers, add a new one, and move the camera
                googleMap.clear();
                markers.put("USER", googleMap.addMarker(new MarkerOptions().position(searchedLatLng).title("USER")));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 15f));
            }
            else {
                // No address found
                Toast.makeText(getContext(), "Location not found. Try being more specific.", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e("MapFragment", "Geocoder service not available", e);
            Toast.makeText(getContext(), "Could not connect to Geocoding service", Toast.LENGTH_LONG).show();
        }
    }

    private double[] computeBbox(double centerX, double centerY, int width, int height, double metersPerPixelX, double metersPerPixelY) {
        double halfWidth = width * metersPerPixelX / 2.0;
        double halfHeight = height * metersPerPixelY / 2.0;
        return new double[]{centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight};
    }

    public interface DotResultCallback {
        void onDotsReady(List<DotDetector.Coord> dots);

        void onError(Exception e);
    }


    private void fetchWmsImage(double[] bbox, DotResultCallback callback) {
        double minX=bbox[0], minY=bbox[1], maxX=bbox[2], maxY=bbox[3];

        String url="https://www.govmap.gov.il/api/geoserver/ows/public/?"+"SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&FORMAT=image/png&TRANSPARENT=true"+"&LAYERS=govmap:layer_bombshelters&TILED=false&CRS=EPSG:3857"+"&STYLES=govmap:layer_bombshelters&FEATUREVERSION=1"+"&WIDTH="+mapView.getWidth()+"&HEIGHT="+mapView.getHeight()+"&BBOX="+minX+","+minY+","+maxX+","+maxY;

        Request request=new Request.Builder().url(url).addHeader("accept", "image/png,*/*;q=0.8").build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("HTTP "+response.code()));
                    return;
                }

                Bitmap bitmap=BitmapFactory.decodeStream(response.body().byteStream());
                if (bitmap==null) {
                    callback.onError(new IOException("Decode error"));
                    return;
                }

                List<DotDetector.Coord> dots=DotDetector.findDotCenters(bitmap, minX, minY, maxX, maxY);

                callback.onDotsReady(dots);
            }
        });
    }


    // --- ADD ALL OF THE FOLLOWING METHODS FOR MAP LIFECYCLE MANAGEMENT ---

    @Override
    public void onResume() {
        super.onResume();
        if (mapView!=null) {
            mapView.onResume();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView!=null) {
            mapView.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView!=null) {
            mapView.onStop();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView!=null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView!=null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView!=null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView!=null) {
            mapView.onSaveInstanceState(outState);
        }
    }


}