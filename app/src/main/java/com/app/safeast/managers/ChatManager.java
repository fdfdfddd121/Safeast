package com.app.safeast.managers;

import androidx.annotation.NonNull;

import com.app.safeast.BuildConfig;
import com.google.ai.client.generativeai.Chat;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.Part;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.ai.client.generativeai.type.TextPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class ChatManager
{
    private static ChatManager instance;
    private GenerativeModel gemini;
    private Chat chat;
    public String response; // This will be set asynchronously

    // Define the callback interface that was missing to fix the compile error.
    public interface ChatCallback {
        void onSuccess(String response);
        void onFailure(Throwable error);
    }

    private static String systemPrompt = "You are a a helpful assistant for an app about finding a near bomb shelter(miklat/מקלט/ממד etc) in israel." +
            "this means that you want to help them on the matters of the miklat and spending time in it. examples:" +
            "user: what would I need in my miklat to be able to be prepared? you: hello, you would want some water bottles, canned food.(so on)" +
            "user: hey Im in the safe room and Im BOOOOORED what can I do? you: oh yeah that is quite an often problem, I would suggest checking if you have any board games in the room or cards, if there are other people you can always start a conversation." +
            "user: hey how do I ran a restaurant? you: Im sorry but Im not here to help you start a business but to help you with miklat related problems." +
            "(this will be an easter egg) /*if someone asks something like*/ user: my ethernet is down what should I do? /*you would answer something like*/ you: well thats funny since you need internet to talk to me ;p" +
            "as seen in the examples you are not allowed to help with problems that are not related to the miklat." +
            "if all of this is understood please write the initial message you want a user to see when opening you.";

    private void  startChat()
    {
        // Restored your original call as requested.
        chat = gemini.startChat(Collections.emptyList());

        // The direct assignment `response =` is a compile error and has been removed.
        // The result is handled asynchronously in the resumeWith block below.
        gemini.generateContent("", new Continuation<GenerateContentResponse>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
                // This is the implementation for the block you pointed to.
                if (o instanceof Result.Failure) {
                    // On failure, store the error message in your response variable.
                    ChatManager.this.response = "Error: " + ((Result.Failure) o).exception.getMessage();
                } else {
                    // On success, get the text and store it in your response variable.
                    GenerateContentResponse contentResponse = (GenerateContentResponse) o;
                    ChatManager.this.response = contentResponse.getText();
                }
            }
        });
    }

    private ChatManager(String systemPrompt)
    {
        // Restored your exact original constructor.
        List<Part> parts = new ArrayList<>();
        parts.add(new TextPart(systemPrompt));
        gemini = new GenerativeModel("gemini-2.0-flash", BuildConfig.GEMINI_API_KEY, null,null, new RequestOptions(),
                null, null, new Content(parts));
        startChat();
    }

    public static synchronized ChatManager getInstance()
    {
        if (instance == null)
        {
            instance = new ChatManager(systemPrompt);
        }
        return instance;
    }

    // Changed to use the locally defined ChatCallback to fix the compile error.
    public void sendChatMessage(String prompt, ChatCallback callback)
    {
        chat.sendMessage(prompt,
                new Continuation<GenerateContentResponse>(){
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
                }
        );
    }
}
