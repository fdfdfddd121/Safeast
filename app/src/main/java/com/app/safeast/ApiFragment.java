package com.app.safeast;

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
import okhttp3.*;
import java.io.IOException;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ApiFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ApiFragment extends Fragment {

    private final String shelterURL = "https://www.govmap.gov.il/api/layers-catalog/entitiesByPoint";
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
        // JSON body (as in your fetch request)
        String jsonBody = "{"
                + "\"point\":[3872548.6,3670185.6],"
                + "\"layers\":[{\"layerId\":\"427\"},{\"layerId\":\"417\"}],"
                + "\"tolerance\":277.8130556261113"
                + "}";
        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(shelterURL)
                .post(body)
                .addHeader("accept", "application/json, text/plain, */*")
                .addHeader("content-type", "application/json")
                .addHeader("accept-language", "he,he-IL;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("referer", "https://www.govmap.gov.il/?z=6&c=180726.75,573949.65&lay=427,417&b=7&bs=427,417%7C179775.17,577426.46")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() ->
                        shelterData.setText("Request failed: " + e.getMessage())
                );
            }
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    final String responseData = response.body().string();
                    getActivity().runOnUiThread(() ->
                            shelterData.setText(responseData)
                    );
                } else {
                    getActivity().runOnUiThread(() ->
                            shelterData.setText("Error: " + response.code())
                    );
                }
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