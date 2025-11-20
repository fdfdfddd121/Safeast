package com.app.safeast.helperFiles;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;



public class DotDetector {

    static CRSFactory crsFactory = new CRSFactory();
    public enum EPSG {ISRAEL("EPSG:2039"), GOOGLEMAPS("EPSG:3857"), GPS("EPSG:4326");
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

    public static class Coord {
        public double mapX, mapY;
        public String epsgType;

        public Coord(double x, double y, String epsg) {
            mapX=x;
            mapY=y;
            epsgType = epsg;
        }

    }

    public static Coord convertEPSG(Coord coordinates, String toEPSG) {

        CoordinateReferenceSystem src = crsFactory.createFromName(coordinates.epsgType);
        CoordinateReferenceSystem dst = crsFactory.createFromName(toEPSG);

        CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        CoordinateTransform transform = ctFactory.createTransform(src, dst);

        ProjCoordinate srcCoord = new ProjCoordinate(coordinates.mapX, coordinates.mapY);
        ProjCoordinate dstCoord = new ProjCoordinate();
        transform.transform(srcCoord, dstCoord);

        return new Coord(dstCoord.x, dstCoord.y, toEPSG);
    }

    public static Coord convertEPSG(double X, double Y, String fromEPSG, String toEPSG) {
        CoordinateReferenceSystem src = crsFactory.createFromName(fromEPSG);
        CoordinateReferenceSystem dst = crsFactory.createFromName(toEPSG);

        CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        CoordinateTransform transform = ctFactory.createTransform(src, dst);

        ProjCoordinate srcCoord = new ProjCoordinate(X, Y);
        ProjCoordinate dstCoord = new ProjCoordinate();
        transform.transform(srcCoord, dstCoord);

        return new Coord(dstCoord.x, dstCoord.y, toEPSG);
    }

    /**
     * Detects red/white pixel clusters and returns one coordinate per cluster.
     */
    public static List<Coord> findDotCenters(Bitmap bitmap, double minX, double minY, double maxX, double maxY) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Pre-extract all pixels at once - MUCH faster than repeated getPixel() calls
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        boolean[] visited = new boolean[width * height]; // 1D array is faster
        List<Coord> foundDots = new ArrayList<>();

        double dx = (maxX - minX) / width;
        double dy = (maxY - minY) / height;

        // Sample every N pixels instead of every single pixel (adjust stepSize for speed/accuracy balance)
        int stepSize = 3; // Start checking every 3rd pixel - dots are large enough

        for (int y = 0; y < height; y += stepSize) {
            for (int x = 0; x < width; x += stepSize) {
                int index = y * width + x;

                if (visited[index])
                    continue;

                int pixel = pixels[index];

                // Optimized color check using bit operations
                if (!isDotPixel(pixel))
                    continue;

                // Flood-fill cluster
                int[] queueX = new int[5000]; // Pre-allocated arrays instead of ArrayDeque
                int[] queueY = new int[5000];
                int qHead = 0, qTail = 0;

                queueX[qTail] = x;
                queueY[qTail] = y;
                qTail++;

                int sumX = 0, sumY = 0, count = 0;

                while (qHead < qTail) {
                    int px = queueX[qHead];
                    int py = queueY[qHead];
                    qHead++;

                    if (px < 0 || px >= width || py < 0 || py >= height)
                        continue;

                    int idx = py * width + px;
                    if (visited[idx])
                        continue;

                    if (!isDotPixel(pixels[idx]))
                        continue;

                    visited[idx] = true;
                    sumX += px;
                    sumY += py;
                    count++;

                    // Add 4-connected neighbors only (not 8) for speed
                    if (qTail + 4 < queueX.length) {
                        queueX[qTail] = px + 1; queueY[qTail++] = py;
                        queueX[qTail] = px - 1; queueY[qTail++] = py;
                        queueX[qTail] = px; queueY[qTail++] = py + 1;
                        queueX[qTail] = px; queueY[qTail++] = py - 1;
                    }
                }

                if (count > 20) {
                    double cx = (double) sumX / count;
                    double cy = (double) sumY / count;

                    double mapX = minX + cx * dx;
                    double mapY = maxY - cy * dy;

                    foundDots.add(new Coord(mapX, mapY, EPSG.GPS.label));
                    Coord cord = convertEPSG(mapX, mapY, EPSG.GPS.label, EPSG.ISRAEL.label);
                    Log.d("DotDetector", "Dot as ISRAEL → (" + cord.mapX + ", " + cord.mapY + ")");
                }
            }
        }

        return foundDots;
    }

    // Optimized color checking using bit shifts
    private static boolean isDotPixel(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        return (r > 180 && g < 150 && b < 150) ||  // red
                (r > 220 && g > 220 && b > 220);    // white
    }
}

