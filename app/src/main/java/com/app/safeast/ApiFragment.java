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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ApiFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ApiFragment extends Fragment {

    private final String selfterURL = "https://www.govmap.gov.il/?z=10&c=180233.01,573089.6&lay=417&b=7";
    private TextView shelterData;
    private Button shelterButton;
    private RequestQueue RQ;

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
        RQ = Volley.newRequestQueue(requireActivity());
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
            StringRequest request = new StringRequest(Request.Method.GET, selfterURL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    shelterData.setText(getString(R.string.data_from_govmap) + response);
                }
                }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    shelterData.setText("Failed to get data");
                    Toast.makeText(requireActivity(), "Failed to get data", Toast.LENGTH_SHORT).show();
                }
            });
            RQ.add(request);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        shelterButton = null;
        shelterData = null;
    }
}