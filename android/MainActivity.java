package com.example.iotui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;



public class MainActivity extends AppCompatActivity {

    private String NAME;
    private String DEV_NUM;
    private EditText nameText;
    private EditText numberText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nameText = findViewById(R.id.nameText);
        numberText = findViewById(R.id.numberText);
    }

    public void submitName(View view){
       String name = nameText.getText().toString();
       DEV_NUM = numberText.getText().toString();
       int num = Integer.parseInt(DEV_NUM);
       NAME =  name.replaceAll(" ", "_").toLowerCase();
        if (!TextUtils.isEmpty(NAME) && !TextUtils.isEmpty(DEV_NUM) && num <= 10 && num >= 1) {
            Intent intent = new Intent(this, StartExpActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("name", NAME);
            bundle.putString("dev_number", DEV_NUM);
            intent.putExtras(bundle);
            startActivity(intent);
            finish();
        }
    }
}
