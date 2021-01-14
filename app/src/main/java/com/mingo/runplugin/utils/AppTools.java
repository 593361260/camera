package com.mingo.runplugin.utils;

import android.content.Context;

public class AppTools {
    public static int getWindowWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getWindowHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

}
