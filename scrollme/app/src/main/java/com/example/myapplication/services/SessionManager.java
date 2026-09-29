package com.example.myapplication.services;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME =
            "MyScrollSession";

    private static final String TOKEN =
            "token";

    private static final String USER_ID =
            "userId";

    private static final String NAME =
            "name";

    private static final String EMAIL =
            "email";

    public static void saveSession(
            Context context,
            String token,
            String userId,
            String name,
            String email) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        preferences.edit()
                .putString(TOKEN, token)
                .putString(USER_ID, userId)
                .putString(NAME, name)
                .putString(EMAIL, email)
                .apply();
    }

    public static String getToken(Context context) {

        return context
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .getString(TOKEN, null);
    }

    public static String getUserId(Context context) {

        return context
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .getString(USER_ID, null);
    }

    public static String getName(Context context) {

        return context
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .getString(NAME, null);
    }

    public static String getEmail(Context context) {

        return context
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .getString(EMAIL, null);
    }

    public static boolean isLoggedIn(Context context) {

        String token = getToken(context);

        return token != null && !token.isEmpty();
    }

    public static void clearSession(Context context) {

        context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .apply();
    }
}