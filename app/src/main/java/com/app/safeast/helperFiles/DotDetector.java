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
        CRSFactory crsFactory = new CRSFactory();
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
        CRSFactory crsFactory = new CRSFactory();
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

        int width=bitmap.getWidth();
        int height=bitmap.getHeight();
        boolean[][] visited=new boolean[width][height];
        List<Coord> foundDots=new ArrayList<>();

        double dx=(maxX-minX)/width;
        double dy=(maxY-minY)/height;

        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                if (visited[x][y])
                    continue;

                int pixel=bitmap.getPixel(x, y);
                int r=Color.red(pixel);
                int g=Color.green(pixel);
                int b=Color.blue(pixel);

                boolean isDotPixel=(r>180 && g<150 && b<150) ||  // red
                        (r>220 && g>220 && b>220);   // white highlight

                if (isDotPixel) {
                    // --- flood-fill cluster ---
                    ArrayDeque<int[]> queue=new ArrayDeque<>();
                    queue.add(new int[]{x, y});

                    int sumX=0, sumY=0, count=0;

                    while (!queue.isEmpty()) {
                        int[] p=queue.poll();
                        int px=p[0], py=p[1];
                        if (px<0 || px>=width || py<0 || py>=height)
                            continue;
                        if (visited[px][py])
                            continue;

                        int pix=bitmap.getPixel(px, py);
                        int rr=Color.red(pix);
                        int gg=Color.green(pix);
                        int bb=Color.blue(pix);
                        boolean dotPix=(rr>180 && gg<150 && bb<150) || (rr>220 && gg>220 && bb>220);
                        if (!dotPix)
                            continue;

                        visited[px][py]=true;
                        sumX+=px;
                        sumY+=py;
                        count++;

                        // add neighbors
                        for (int dyN=-1; dyN<=1; dyN++) {
                            for (int dxN=-1; dxN<=1; dxN++) {
                                if (dxN!=0 || dyN!=0)
                                    queue.add(new int[]{px+dxN, py+dyN});
                            }
                        }
                    }

                    // ignore small noise clusters
                    if (count>20) {
                        double cx=(double) sumX/count;
                        double cy=(double) sumY/count;

                        double mapX=minX+cx*dx;
                        double mapY=maxY-cy*dy;  // Y inverted

                        foundDots.add(new Coord(mapX, mapY, EPSG.GOOGLEMAPS.label));
                        Coord cord = convertEPSG(mapX, mapY, EPSG.GOOGLEMAPS.label, EPSG.ISRAEL.label);
                        Log.d("DotDetector", "Dot as ISRAEL → ("+cord.mapX+", "+cord.mapY+")");
                    }
                }
            }
        }

        return foundDots;
    }
}

