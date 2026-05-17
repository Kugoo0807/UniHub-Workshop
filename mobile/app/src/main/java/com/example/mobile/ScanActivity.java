package com.example.mobile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobile.data.local.AppDatabase;
import com.example.mobile.data.local.AttendeeDao;
import com.example.mobile.data.local.AttendeeEntity;
import com.example.mobile.data.repository.CheckinRepository;
import com.example.mobile.util.NetworkUtil;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanActivity extends AppCompatActivity {

    // ISO 8601 format for storage and server sync
    private static final SimpleDateFormat ISO_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    // Human-readable format for display only
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

    private long workshopId;
    private AttendeeDao attendeeDao;
    private CheckinRepository repository;

    private CaptureManager capture;
    private DecoratedBarcodeView barcodeScannerView;
    private Button btnPickImage;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        workshopId = getIntent().getLongExtra("WORKSHOP_ID", -1);
        if (workshopId == -1) {
            Toast.makeText(this, "Invalid workshop", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        attendeeDao = AppDatabase.getDatabase(this).attendeeDao();
        repository = new CheckinRepository(this);

        barcodeScannerView = findViewById(R.id.barcode_scanner);
        btnPickImage = findViewById(R.id.btnPickImage);
        Button btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();

        barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null && !isProcessing) {
                    isProcessing = true;
                    barcodeScannerView.pause();
                    processQrCode(result.getText());
                }
            }
        });

        btnPickImage.setOnClickListener(v -> {
            barcodeScannerView.pause();
            pickImageLauncher.launch("image/*");
        });
    }

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    decodeImageFromUri(uri);
                } else {
                    barcodeScannerView.resume();
                }
            });

    private void decodeImageFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

            LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(binaryBitmap);

            processQrCode(result.getText());

        } catch (Exception e) {
            Toast.makeText(this, "No QR code found in image or read error", Toast.LENGTH_SHORT).show();
            barcodeScannerView.resume();
        }
    }

    private void processQrCode(String qrCode) {
        new Thread(() -> {
            AttendeeEntity attendee = attendeeDao.getAttendeeByQrCode(qrCode, workshopId);

            String statusMessage;
            String status = "ERROR"; // ERROR, DUPLICATE, or SUCCESS

            if (attendee == null) {
                statusMessage = "❌ Invalid ticket or not for this workshop";
                status = "ERROR";
            } else if (attendee.isCheckedIn()) {
                statusMessage = "⚠️ This ticket has already been checked in at " + formatForDisplay(attendee.getScannedAt());
                status = "DUPLICATE";
            } else {
                String now = ISO_FORMAT.format(new Date());
                
                attendee.setCheckedIn(true);
                attendee.setScannedAt(now);
                attendee.setSynced(false);
                attendeeDao.update(attendee);

                statusMessage = "✅ Check-in successful:\n" + attendee.getStudentName() + " - " + attendee.getStudentCode();
                status = "SUCCESS";

                if (NetworkUtil.isNetworkAvailable(this)) {
                    repository.triggerBackgroundSync();
                }
            }

            final String msg = statusMessage;
            final String finalStatus = status;
            runOnUiThread(() -> {
                Intent intent = new Intent(ScanActivity.this, ScanResultActivity.class);
                intent.putExtra("MESSAGE", msg);
                intent.putExtra("STATUS", finalStatus);
                intent.putExtra("WORKSHOP_ID", workshopId);
                startActivity(intent);
                finish();
            });

        }).start();
    }

    /** Convert ISO string to user-friendly display string. Falls back to raw if parse fails. */
    private String formatForDisplay(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) return "unknown time";
        try {
            Date date = ISO_FORMAT.parse(isoDateTime);
            return date != null ? DISPLAY_FORMAT.format(date) : isoDateTime;
        } catch (Exception e) {
            return isoDateTime; // fallback
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
        if (!isProcessing) {
            barcodeScannerView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }
}
