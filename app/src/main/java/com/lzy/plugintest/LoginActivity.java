package com.lzy.plugintest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private String className;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        className = getIntent().getStringExtra("extraIntent");
    }

    public void login(View view) {
        SharedPreferences sp = getSharedPreferences("lzy", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();
        edit.putBoolean("login",true);
        edit.apply();
        if (className != null){
            ComponentName componentName = new ComponentName(this, className);
            Intent intent = new Intent();
            intent.setComponent(componentName);
            startActivity(intent);
            finish();
        }
    }
}
