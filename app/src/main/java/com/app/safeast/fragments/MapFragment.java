package com.app.safeast.fragments;

import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import com.app.safeast.R;
import com.app.safeast.managers.NavigationManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.HashMap;
import java.util.Objects;

import okhttp3.OkHttpClient;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
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

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This handles the result of the permission request
        locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission is granted. Try to get the location again.
                    navManager.sheltersNearGPS(locationPermissionRequest, mapView, getActivity());
            } else {
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

        //must initialize variables in view
        mapView = view.findViewById(R.id.mapView);
        currLoc = view.findViewById(R.id.currLoc);
        getDir = view.findViewById(R.id.getDir);
        searchView = view.findViewById(R.id.searchView);
        markers = new HashMap<>();
        if (mapView != null) {
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
                navManager.sheltersNearSearch(searchView,mapView,getActivity());
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }

        });

        //current location function link
        currLoc.setOnClickListener(v -> {
                navManager.sheltersNearGPS(locationPermissionRequest, mapView, getActivity());
        });

        //get directions function link
        getDir.setOnClickListener(view1 -> {
            navManager.giveDirections(selectedMarker);
        });
    }

    /**
     * This method is called when the map is fully loaded and ready to be used.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        googleMap.getUiSettings().setZoomControlsEnabled(true); // Show zoom buttons
        googleMap.getUiSettings().setAllGesturesEnabled(true);


        googleMap.setOnMarkerClickListener(this);
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull LatLng latLng) {
                selectedMarker = null;
                currLoc.setVisibility(View.VISIBLE);
                getDir.setVisibility(View.GONE);
            }
        });

        // set default location in Beer Sheva
        LatLng beerSheva = new LatLng(31.2530, 34.7915);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(beerSheva, 12));

        //initialize navigationManager
        navManager = new NavigationManager(requireActivity(), googleMap);

    }


    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        if (Objects.equals(marker.getTitle(), "USER"))
        {
            return false;
        }
        selectedMarker = marker;
        currLoc.setVisibility(View.GONE);
        getDir.setVisibility(View.VISIBLE);
        return false;
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
