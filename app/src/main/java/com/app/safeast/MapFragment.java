package com.app.safeast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

MapView mapView;
GoogleMap googleMap;
Button currLoc;
EditText editLoc;
Button pinLoc;

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
        MapFragment fragment = new MapFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ADD THIS INITIALIZATION BLOCK
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // This handles the result of the permission request
        locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission is granted. Try to get the location again.
                getCurrentLocationAndPin();
            } else {
                // Permission is denied. Show a message to the user.
                Toast.makeText(getContext(), "Location permission denied. Cannot get current location.", Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.mapView);
        currLoc = view.findViewById(R.id.currLoc);
        editLoc = view.findViewById(R.id.editLoc);
        pinLoc = view.findViewById(R.id.pinLoc);

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this); // Register the callback
        }

        // --- ADD THE ONCLICK LISTENERS FOR YOUR BUTTONS ---

        // 1. For the "Current Location" button
        currLoc.setOnClickListener(v -> {
            getCurrentLocationAndPin();
        });

        // 2. For the "Pin Searched Location" button
        pinLoc.setOnClickListener(v -> {
            pinSearchedLocation();
        });
    }

    /**
     * This method is called when the map is fully loaded and ready to be used.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // You can customize the map here
        googleMap.getUiSettings().setZoomControlsEnabled(true); // Show zoom buttons

        // Let's place a default marker on Beer Sheva and move the camera
        LatLng beerSheva = new LatLng(31.2530, 34.7915);
        googleMap.addMarker(new MarkerOptions().position(beerSheva).title("Marker in Beer Sheva"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(beerSheva, 12));
    }

    /**
     * Handles the logic for the "Current Location" button.
     * It checks for permission and then gets the last known location.
     */
    private void getCurrentLocationAndPin() {
        // First, check if we have permission to access location
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // You have permission. Get the location.
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    // Location found. Create a LatLng object.
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    // Clear previous markers, add a new one, and move the camera
                    googleMap.clear();
                    googleMap.addMarker(new MarkerOptions().position(currentLatLng).title("My Current Location"));
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)); // Zoom in closer
                } else {
                    Toast.makeText(getContext(), "Could not get location. Make sure location is enabled on the device.", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // You do not have permission. Request it from the user.
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the logic for the "Pin Searched Location" button.
     * It uses Geocoder to convert the text address into coordinates.
     */
    private void pinSearchedLocation() {
        String locationName = editLoc.getText().toString();
        if (locationName.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a location to search", Toast.LENGTH_SHORT).show();
            return;
        }

        // Geocoder can be slow and should ideally be run in a background thread,
        // but for simplicity, we'll do it on the main thread here.
        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
        try {
            // getFromLocationName() returns a list of possible addresses. We take the first one.
            List<Address> addressList = geocoder.getFromLocationName(locationName, 1);
            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                LatLng searchedLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                // Clear previous markers, add a new one, and move the camera
                googleMap.clear();
                googleMap.addMarker(new MarkerOptions().position(searchedLatLng).title(locationName));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 15f));
            } else {
                // No address found
                Toast.makeText(getContext(), "Location not found. Try being more specific.", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e("MapFragment", "Geocoder service not available", e);
            Toast.makeText(getContext(), "Could not connect to Geocoding service", Toast.LENGTH_LONG).show();
        }
    }


    // --- ADD ALL OF THE FOLLOWING METHODS FOR MAP LIFECYCLE MANAGEMENT ---

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }


}