package com.app.safeast.objects;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

//Shelter object
public class Shelter {
    private static int counter = 0;
    private int id;
    private String name;
    private LatLng location;
    private Marker marker;

    // Constructor for Shelter
    public Shelter(LatLng location, GoogleMap googleMap)
    {
        this.id = counter++;
        this.location = location;
        this.name = "Shelter " + id;
        this.marker = googleMap.addMarker(new MarkerOptions().position(location).title(name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
    }

    //getters for the shelter
    public Marker getMarker()
    {
        return marker;
    }

    public LatLng getLocation()
    {
        return location;
    }

    public String getName()
    {
        return name;
    }

    public int getId()
    {
        return id;
    }

    //remove the shelter from the map
    public void remove() {
        if (marker != null) {
            marker.remove();
        }
    }

    //reset the counter
    public static void resetCount()
    {
        counter = 0;
    }

}
