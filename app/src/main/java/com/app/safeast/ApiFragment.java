package com.app.safeast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import android.util.Log;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ApiFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ApiFragment extends Fragment {

    private final OkHttpClient client = new OkHttpClient();
    private TextView shelterData;
    private Button shelterButton;

    public ApiFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     */
    // TODO: Rename and change types and number of parameters
    public static ApiFragment newInstance() {
        ApiFragment fragment = new ApiFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_api, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        double[] bbox = computeBbox(3869274.9172,3767288.2151, 412, 520, 0.5); // sample center + zoom size
        fetchWmsImage(bbox);
        shelterButton = view.findViewById(R.id.shelterButton);
        shelterData = view.findViewById(R.id.shelterData);
        shelterData.setMovementMethod(new ScrollingMovementMethod());
        shelterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getShelterData();
            }
        });
    }

    private void getShelterData() {
    /**
     * Compute a generic BBOX for testing (centerX, centerY in EPSG:3857)
     */
    private double[] computeBbox(double centerX, double centerY, int width, int height, double metersPerPixel) {
        double halfWidth = width * metersPerPixel / 2.0;
        double halfHeight = height * metersPerPixel / 2.0;
        return new double[]{centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight};
    }

    private void fetchWmsImage(double[] bbox) {
        double minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];

        String url = "https://www.govmap.gov.il/api/geoserver/ows/public/?" +
                "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&FORMAT=image/png&TRANSPARENT=true" +
                "&LAYERS=govmap:layer_bombshelters&TILED=false&CRS=EPSG:3857" +
                "&STYLES=govmap:layer_bombshelters&FEATUREVERSION=1" +
                "&WIDTH=412&HEIGHT=520" +
                "&BBOX=" + minX + "," + minY + "," + maxX + "," + maxY;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("accept", "image/png,*/*;q=0.8")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("WMS", "Failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("WMS", "Error: " + response.code());
                    return;
                }

                Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                if (bitmap == null) {
                    Log.e("WMS", "Decode error");
                    return;
                }

                List<DotDetector.Coord> dots = DotDetector.findDotCenters(bitmap, minX, minY, maxX, maxY);

                getActivity().runOnUiThread(() -> {
                    for (DotDetector.Coord c : dots) {
                        Log.d("DotCoord", "googlemaps coords: X=" + c.mapX + " Y=" + c.mapY);
                    }
                    Log.i("WMS", "Total dots: " + dots.size());
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        shelterButton = null;
        shelterData = null;
    }
}