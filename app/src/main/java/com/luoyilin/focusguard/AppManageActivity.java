package com.luoyilin.focusguard;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AppManageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manage);

        Button btnAddLimitedApp = findViewById(R.id.btnAddLimitedApp);

        btnAddLimitedApp.setOnClickListener(v ->
                Toast.makeText(this, "应用列表功能即将开发", Toast.LENGTH_SHORT).show()
        );
    }
}