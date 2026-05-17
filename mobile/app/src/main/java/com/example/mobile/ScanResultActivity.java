package com.example.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ScanResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        String message = getIntent().getStringExtra("MESSAGE");
        String status = getIntent().getStringExtra("STATUS");
        if (status == null) status = "ERROR";
        long workshopId = getIntent().getLongExtra("WORKSHOP_ID", -1);

        TextView tvResult = findViewById(R.id.tvResult);
        TextView tvResultIcon = findViewById(R.id.tvResultIcon);
        tvResult.setText(message);

        if ("SUCCESS".equals(status)) {
            // Green - successful check-in
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.emerald_700));
            tvResultIcon.setText("✓");
            tvResultIcon.setTextColor(ContextCompat.getColor(this, R.color.emerald_600));
        } else if ("DUPLICATE".equals(status)) {
            // Amber/Yellow - duplicate/warning
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.amber_600));
            tvResultIcon.setText("⚠");
            tvResultIcon.setTextColor(ContextCompat.getColor(this, R.color.amber_500));
        } else {
            // Red - error
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.red_700));
            tvResultIcon.setText("✗");
            tvResultIcon.setTextColor(ContextCompat.getColor(this, R.color.red_600));
        }

        Button btnScanAgain = findViewById(R.id.btnScanAgain);
        btnScanAgain.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra("WORKSHOP_ID", workshopId);
            startActivity(intent);
            finish();
        });

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }
}
