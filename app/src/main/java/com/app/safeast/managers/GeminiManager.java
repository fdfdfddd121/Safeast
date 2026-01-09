package com.app.safeast.managers;

import androidx.annotation.NonNull;

import com.app.safeast.BuildConfig;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class GeminiManager {
    private static GeminiManager instance;
    private GenerativeModel gemini;

    public interface GeminiCallback {
        void onSuccess(String result);
        void onFailure(Throwable error);
    }

    private GeminiManager()
    {
        gemini = new GenerativeModel("gemini-2.0-flash", BuildConfig.GEMINI_API_KEY);
    }

    public static GeminiManager getInstance()
    {
        if (instance == null)
        {
            instance = new GeminiManager();

        }
        return instance;
    }

    public void sendTextPrompt(String prompt, GeminiCallback callback)
    {
        gemini.generateContent(prompt, new Continuation<GenerateContentResponse>(){
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object result) {
                if (result instanceof Result.Failure)
                {
                    callback.onFailure(((Result.Failure) result).exception);
                }
                else {
                    callback.onSuccess(((GenerateContentResponse) result).getText());
                }
            }
        });
    }
}
