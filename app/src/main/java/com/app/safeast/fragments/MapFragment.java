package com.app.safeast.fragments;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import com.app.safeast.R;
import com.app.safeast.managers.NavigationManager;
import com.app.safeast.objects.GPSShare;
import com.app.safeast.objects.SharedLocationViewModel;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    MapView mapView;
    GoogleMap googleMap;
    SearchView searchView;
    Button currLoc;
    Button getDir;
    HashMap<String, Marker> markers;
    private NavigationManager navManager;
    private ActivityResultLauncher<String> locationPermissionRequest;
    private Marker selectedMarker = null;

    // ADD THESE
    private SharedLocationViewModel sharedViewModel;
    private Map<String, Marker> friendMarkers = new HashMap<>();

    public MapFragment() {
        // Required empty public constructor
    }

    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                navManager.sheltersNearGPS(locationPermissionRequest, mapView, getActivity());
            } else {
                Toast.makeText(getContext(), "Location permission denied. Cannot get current location.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel - SHARED with FriendsFragment
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedLocationViewModel.class);

        mapView = view.findViewById(R.id.mapView);
        currLoc = view.findViewById(R.id.currLoc);
        getDir = view.findViewById(R.id.getDir);
        searchView = view.findViewById(R.id.searchView);
        markers = new HashMap<>();

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        searchView.setOnClickListener(v -> {
            searchView.setQuery("", false);
            searchView.onActionViewExpanded();
            searchView.setIconified(false);
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                navManager.sheltersNearSearch(searchView, mapView, getActivity());
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        currLoc.setOnClickListener(v -> navManager.sheltersNearGPS(locationPermissionRequest, mapView, getActivity()));
        getDir.setOnClickListener(view1 -> navManager.giveDirections(selectedMarker));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setAllGesturesEnabled(true);

        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnMapClickListener(latLng -> {
            selectedMarker = null;
            currLoc.setVisibility(View.VISIBLE);
            getDir.setVisibility(View.GONE);
        });

        LatLng beerSheva = new LatLng(31.2530, 34.7915);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(beerSheva, 12));

        navManager = new NavigationManager(requireActivity(), googleMap);

        // START OBSERVING FRIEND LOCATIONS
        observeFriendLocations();
    }

    // OBSERVE FRIEND GPS SHARES
    private void observeFriendLocations() {
        sharedViewModel.getFriendLocations().observe(getViewLifecycleOwner(), friendLocations -> {
            Log.d("MapFragment", "📍 Friend locations updated! Count: " + friendLocations.size());

            // Remove markers for friends no longer sharing
            for (String uid : new HashMap<>(friendMarkers).keySet()) {
                if (!friendLocations.containsKey(uid)) {
                    Marker marker = friendMarkers.get(uid);
                    if (marker != null) {
                        marker.remove();
                        Log.d("MapFragment", "🗑️ Removed marker for: " + uid);
                    }
                    friendMarkers.remove(uid);
                }
            }

            // Add or update markers for friends sharing location
            for (Map.Entry<String, GPSShare> entry : friendLocations.entrySet()) {
                String uid = entry.getKey();
                GPSShare share = entry.getValue();

                LatLng position = new LatLng(share.latitude, share.longitude);

                String title = (share.fromUsername != null ? share.fromUsername : "Friend") + "'s Location";
                String snippet = "Expires in " + share.getRemainingSeconds() + " seconds";

                // Update existing marker or create new one
                Marker existingMarker = friendMarkers.get(uid);
                if (existingMarker != null) {
                    existingMarker.setPosition(position);
                    existingMarker.setTitle(title);
                    existingMarker.setSnippet(snippet);
                    Log.d("MapFragment", "🔄 Updated marker for: " + title);
                } else {
                    // Create new marker with ORANGE color
                    Marker marker = googleMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title(title)
                            .snippet(snippet)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

                    if (marker != null) {
                        friendMarkers.put(uid, marker);
                        marker.showInfoWindow();

                        // Move camera to show friend's location
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15));

                        Toast.makeText(requireContext(),
                                "📍 " + title + " visible for " + share.getRemainingSeconds() + "s",
                                Toast.LENGTH_LONG).show();

                        Log.d("MapFragment", "✅ Added marker for: " + title + " at " + position);
                    }
                }
            }
        });
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        if (Objects.equals(marker.getTitle(), "USER")) {
            return false;
        }
        selectedMarker = marker;

        // Check if this is a friend marker (don't show directions for friend markers)
        boolean isFriendMarker = false;
        for (Marker friendMarker : friendMarkers.values()) {
            if (friendMarker.equals(marker)) {
                isFriendMarker = true;
                break;
            }
        }

        if (isFriendMarker) {
            // Just show info window, no directions button
            currLoc.setVisibility(View.VISIBLE);
            getDir.setVisibility(View.GONE);
        } else {
            // Shelter marker - show directions button
            currLoc.setVisibility(View.GONE);
            getDir.setVisibility(View.VISIBLE);
        }

        return false;
    }

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

        // Clean up friend markers
        for (Marker marker : friendMarkers.values()) {
            marker.remove();
        }
        friendMarkers.clear();
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