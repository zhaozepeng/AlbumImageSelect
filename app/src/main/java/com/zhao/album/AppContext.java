package com.zhao.album;

import android.app.Application;

/**
 * @author: zzp(zhao_zepeng@hotmail.com)
 * @since: 2015-06-22
 * Description: #TODO
 */
public class AppContext extends Application{
    private static AppContext instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static AppContext getInstance(){
        return instance;
    }
}
