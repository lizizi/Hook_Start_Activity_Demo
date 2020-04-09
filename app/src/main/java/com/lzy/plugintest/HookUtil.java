package com.lzy.plugintest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class HookUtil {
    private static final String TAG = "HookUtil";
    private Context context;

    //这个方法的目的是替换系统IActivityManager,逃避清单文件的activity注册检查
    public void hookStartActivity(Context context) {
        this.context = context;
        try {

            Class<?> ActivityManagerClass;
            Field gDefault;
            if (Build.VERSION.SDK_INT >= 26) {//如果是8.0以上
                Log.d(TAG, "hookStartActivity: sdk为8.0及以上");
                ActivityManagerClass = Class.forName("android.app.ActivityManager");
                gDefault = ActivityManagerClass.getDeclaredField("IActivityManagerSingleton");
            } else {
                Log.d(TAG, "hookStartActivity: sdk为8.0以下");
                ActivityManagerClass = Class.forName("android.app.ActivityManagerNative");
                gDefault = ActivityManagerClass.getDeclaredField("gDefault");
            }
            gDefault.setAccessible(true);
            Object defaultValue = gDefault.get(null);

            Class<?> SingletonClass = Class.forName("android.util.Singleton");
            Field mInstance = SingletonClass.getDeclaredField("mInstance");
            mInstance.setAccessible(true);
            Object iActivityManagerObject = mInstance.get(defaultValue);
            Class<?> IActivityManagerClass = Class.forName("android.app.IActivityManager");
            MyInvocationHandler handler = new MyInvocationHandler(iActivityManagerObject);
            Object oldAm = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{IActivityManagerClass}, handler);
            mInstance.set(defaultValue, oldAm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hookHandlerCallback(Context context) {
        this.context = context;
        try {
            //通过反射获取到ActivityThread对象threadObj
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThread = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThread.setAccessible(true);
            Object threadObj = sCurrentActivityThread.get(null);
            //通过ActivityThread对象threadObj把系统mCallback替换为自己的callback
            Field handlerField = ActivityThreadClass.getDeclaredField("mH");
            handlerField.setAccessible(true);
            Handler mH = (Handler) handlerField.get(threadObj);
            Field callbackField = Handler.class.getDeclaredField("mCallback");
            callbackField.setAccessible(true);
            HandlerCallBack handlerCallBack = new HandlerCallBack(mH);
            callbackField.set(mH, handlerCallBack);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class MyInvocationHandler implements InvocationHandler {
        private Object iActivityManager;

        public MyInvocationHandler(Object iActivityManager) {
            this.iActivityManager = iActivityManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("startActivity".equals(method.getName())) {
                Log.d(TAG, "hook成功,执行了自己的startActivity方法");
                Intent intent = null;
                int index = 0;
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof Intent) {
                        intent = (Intent) args[i];
                        index = i;
                    }
                }
                Intent newIntent = new Intent();
                ComponentName componentName = new ComponentName(context, ProxyActivity.class);
                newIntent.setComponent(componentName);
                newIntent.putExtra("oldIntent", intent);
                args[index] = newIntent;

            }
            return method.invoke(iActivityManager, args);
        }
    }

    class HandlerCallBack implements Handler.Callback {
        private Handler mH;

        public HandlerCallBack(Handler mH) {
            this.mH = mH;
        }

        @Override
        public boolean handleMessage(Message msg) {
            handleLaunchActivity(msg);
            mH.handleMessage(msg);
            return true;
        }

        private void handleLaunchActivity(Message msg) {
            Object obj = msg.obj;
            try {
                if (Build.VERSION.SDK_INT >= 26) { //如果是8.0以上
                    if (msg.what == 159) {
                        Field mActivityCallbacksField = obj.getClass().getDeclaredField("mActivityCallbacks");
                        mActivityCallbacksField.setAccessible(true);
                        List mActivityCallbacks = (List) mActivityCallbacksField.get(msg.obj);
                        for (int i = 0; i < mActivityCallbacks.size(); i++) {
                            if (mActivityCallbacks.get(i).getClass().getName()
                                    .equals("android.app.servertransaction.LaunchActivityItem")) {
                                Log.d(TAG, "捕捉到启动activity消息");
                                Object launchActivityItem = mActivityCallbacks.get(i);

                                Field mIntentField = launchActivityItem.getClass().getDeclaredField("mIntent");
                                mIntentField.setAccessible(true);
                                Intent realIntent = (Intent) mIntentField.get(launchActivityItem);
                                Intent oldIntent = realIntent.getParcelableExtra("oldIntent");
                                if (oldIntent != null) {
                                    SharedPreferences sp = context.getSharedPreferences("lzy", Context.MODE_PRIVATE);
                                    if (sp.getBoolean("login", false)) {
                                        realIntent.setComponent(oldIntent.getComponent());
                                    } else {
                                        Log.d(TAG, "handleLaunchActivity: 跳转到登录页面");
                                        ComponentName componentName = new ComponentName(context, LoginActivity.class);
                                        realIntent.putExtra("extraIntent", oldIntent.getComponent().getClassName());
                                        realIntent.setComponent(componentName);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (msg.what == 100) {
                        Log.d(TAG, "捕捉到启动activity消息");
                        Field intentField = obj.getClass().getDeclaredField("intent");
                        intentField.setAccessible(true);
                        Intent realIntent = (Intent) intentField.get(obj);
                        Intent oldIntent = realIntent.getParcelableExtra("oldIntent");
                        if (oldIntent != null) {
                            SharedPreferences sp = context.getSharedPreferences("lzy", Context.MODE_PRIVATE);
                            if (sp.getBoolean("login", false)) {
                                realIntent.setComponent(oldIntent.getComponent());
                            } else {
                                Log.d(TAG, "handleLaunchActivity: 跳转到登录页面");
                                ComponentName componentName = new ComponentName(context, LoginActivity.class);
                                realIntent.putExtra("extraIntent", oldIntent.getComponent().getClassName());
                                realIntent.setComponent(componentName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
