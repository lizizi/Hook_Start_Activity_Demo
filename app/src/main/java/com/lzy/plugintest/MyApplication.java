package com.lzy.plugintest;

import android.app.Application;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HookUtil hookUtil = new HookUtil();
        hookUtil.hookStartActivity(this);
        hookUtil.hookHandlerCallback(this);
    }
}
