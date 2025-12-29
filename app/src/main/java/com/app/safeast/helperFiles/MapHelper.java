package com.app.safeast.helperFiles;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MapHelper {

    //EPSG types for app and logs
    public enum EPSG {ISRAEL("EPSG:2039"), WPS("EPSG:3857"), GPS("EPSG:4326");
   private final String label;
   EPSG(String label)
   {
       this.label = label;
   }
   public String getLabel()
   {
       return label;
   }
   }

   //Coord is static since its mostly in conversions
    public static class Coord {
        public final double mapX;
       public final double mapY;
        public final String epsgType;

        public Coord(double x, double y, String epsg) {
            mapX=x;
            mapY=y;
            epsgType = epsg;
        }

    }

    //for faster transforms save the conversion
    private static final CRSFactory crsFactory = new CRSFactory();
    private static final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    private static final Map<String, CoordinateReferenceSystem> crsCache = new HashMap<>();
    private static final Map<String, CoordinateTransform> transformCache = new HashMap<>();

    //returns  any cached CRS or creates a new one
    private static CoordinateReferenceSystem getCachedCRS(String epsgType) {
        CoordinateReferenceSystem crs = crsCache.get(epsgType);
        if (crs == null) {
            crs = crsFactory.createFromName(epsgType);
            crsCache.put(epsgType, crs);
        }
        return crs;
    }

    //conversion method for number coordinates
    public static Coord convertEPSG(double X, double Y, String fromEPSG, String toEPSG) {
        String transformKey = fromEPSG + "->" + toEPSG;

        // Get or create cached transform
        CoordinateTransform transform = transformCache.get(transformKey);
        if (transform == null) {
            CoordinateReferenceSystem src = getCachedCRS(fromEPSG);
            CoordinateReferenceSystem dst = getCachedCRS(toEPSG);
            transform = ctFactory.createTransform(src, dst);
            transformCache.put(transformKey, transform);
        }

        // Reuse ProjCoordinate objects if possible
        ProjCoordinate srcCoord = new ProjCoordinate(X, Y);
        ProjCoordinate dstCoord = new ProjCoordinate();
        transform.transform(srcCoord, dstCoord);

        return new Coord(dstCoord.x, dstCoord.y, toEPSG);
    }


    //shelter dot detection method, returns list of their coordinates
    //gets a bitmap and the bounds of the bounding box
    public static List<Coord> findDotCenters(Bitmap bitmap, double minX, double minY, double maxX, double maxY) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        //flatten the bitmap to an array
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        boolean[] visited = new boolean[width * height]; //visited array to stop revisits
        List<Coord> foundDots = new ArrayList<>();

        //calculate bbox to bitmap ratio
        double pixelRatioX = (maxX - minX) / width;
        double pixelRatioY = (maxY - minY) / height;

        // Sample every N pixels instead of every single pixel (adjust stepSize for speed/accuracy balance)
        int stepSize = 3; // Start checking every 3rd pixel - shelter icons are about this size

        //go through pixels
        for (int y = 0; y < height; y += stepSize) {
            for (int x = 0; x < width; x += stepSize) {
                int index = y * width + x;

                if (visited[index]) //skip if already visited
                    continue;

                int pixel = pixels[index];

                if (isNotFromIcon(pixel)) //skip if not part of the icon
                    continue;

                // Flood-fill cluster - like bucket fill but to search for the icon
                //"queues" for the coordinates
                int[] queueX = new int[5000];
                int[] queueY = new int[5000];
                int qHead = 0, qTail = 0;

                queueX[qTail] = x;
                queueY[qTail] = y;
                qTail++;

                int sumX = 0, sumY = 0, count = 0; //variables to calculate average(center pixel)

                while (qHead < qTail) { //go through till queue is empty
                    int pixelX = queueX[qHead];
                    int pixelY = queueY[qHead];
                    qHead++;

                    if (pixelX < 0 || pixelX >= width || pixelY < 0 || pixelY >= height) //if out of bounds
                        continue;

                    int idx = pixelY * width + pixelX;
                    if (visited[idx]) //if already visited
                        continue;

                    if (isNotFromIcon(pixels[idx])) //if not part of the icon
                        continue;

                    visited[idx] = true;
                    sumX += pixelX;
                    sumY += pixelY;
                    count++;

                    // Add adjacent neighbors
                    if (qTail + 4 < queueX.length) { //fail safe to avoid overflow
                        queueX[qTail] = pixelX + 1; queueY[qTail++] = pixelY;
                        queueX[qTail] = pixelX - 1; queueY[qTail++] = pixelY;
                        queueX[qTail] = pixelX; queueY[qTail++] = pixelY + 1;
                        queueX[qTail] = pixelX; queueY[qTail++] = pixelY - 1;
                    }
                }

                if (count > 20) { //if there are enough pixels for it to be an icon
                    double centerX = (double) sumX / count;
                    double centerY = (double) sumY / count;

                    //calculate the map coordinates from bbox
                    double mapX = minX + centerX * pixelRatioX;
                    double mapY = maxY - centerY * pixelRatioY;

                    foundDots.add(convertEPSG(mapX,mapY,EPSG.WPS.label, EPSG.GPS.label));
                    //create a conversion to website coordinates
                    Coord cord = convertEPSG(mapX, mapY, EPSG.WPS.label, EPSG.ISRAEL.label);
                    Log.d("DotDetector", "Dot as ISRAEL → (" + cord.mapX + ", " + cord.mapY + ")");
                }
            }
        }

        return foundDots;
    }

    //check if a pixel is part of the shelter icon
    private static boolean isNotFromIcon(int pixel) {
        //convert int of pixel into its components
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        return (r <= 180 || g >= 150 || b >= 150) &&  // dark red
                (r <= 220 || g <= 220 || b <= 220);    // white
    }


    //-- for creating the route on the map --//
    private static com.google.android.gms.maps.model.Polyline currentPolyline;

    //draw the route on the map from coordinates
    public static void drawRouteOnMap(List<LatLng> points, LatLng origin, LatLng dest, GoogleMap googleMap) {
        // Remove old line if exists
        if (currentPolyline != null) {
            currentPolyline.remove();
        }

        // Draw new blue line
        com.google.android.gms.maps.model.PolylineOptions lineOptions =
                new com.google.android.gms.maps.model.PolylineOptions();
        lineOptions.addAll(points);
        lineOptions.width(15);
        lineOptions.color(android.graphics.Color.BLUE);
        lineOptions.geodesic(true);

        currentPolyline = googleMap.addPolyline(lineOptions);

        // Zoom camera to fit route
        com.google.android.gms.maps.model.LatLngBounds.Builder builder =
                new com.google.android.gms.maps.model.LatLngBounds.Builder();
        builder.include(origin);
        builder.include(dest);

        // Add a few points from the route to ensure curve fits
        for(LatLng p : points) builder.include(p);

        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
        } catch (Exception e) {
            Log.e("NavigationManager", "Map layout not ready");
        }
    }

    //decodes directionsAPI route into a list of LatLngs
    public static List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) { //go through the string
            int ch, shift = 0, result = 0;
            do { //extract the latitude and convert it from the chars
                ch = encoded.charAt(index++) - 63;
                result |= (ch & 0x1f) << shift;
                shift += 5;
            } while (ch >= 0x20);
            //zigzag decoding if it's a negative coordinate
            int deltalat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltalat;

            shift = 0;
            result = 0;
            do { //extract the longitude
                ch = encoded.charAt(index++) - 63;
                result |= (ch & 0x1f) << shift;
                shift += 5;
            } while (ch >= 0x20);
            int deltalng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltalng;

            //turn back into float coordinates
            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }


}

