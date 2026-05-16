package com.example.mobile;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobile.data.local.AppDatabase;
import com.example.mobile.data.local.AttendeeDao;
import com.example.mobile.data.local.AttendeeEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendeeListActivity extends AppCompatActivity {

    private AttendeeDao attendeeDao;
    private long workshopId;
    private String workshopTitle;

    private RecyclerView recyclerView;
    private AttendeeAdapter adapter;
    private View emptyState;

    // For time formatting
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
            Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_FORMAT = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendee_list);

        workshopId = getIntent().getLongExtra("WORKSHOP_ID", -1);
        workshopTitle = getIntent().getStringExtra("WORKSHOP_TITLE");

        attendeeDao = AppDatabase.getDatabase(this).attendeeDao();

        // Toolbar
        TextView tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvToolbarTitle.setText(workshopTitle != null ? workshopTitle : "Attendee List");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendeeAdapter();
        recyclerView.setAdapter(adapter);

        emptyState = findViewById(R.id.emptyState);

        loadAttendees();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when coming back from ScanActivity
        loadAttendees();
    }

    private void loadAttendees() {
        new Thread(() -> {
            List<AttendeeEntity> attendees = attendeeDao.getAttendeesByWorkshop(workshopId);

            int total = attendees.size();
            int checkedIn = 0;
            for (AttendeeEntity a : attendees) {
                if (a.isCheckedIn())
                    checkedIn++;
            }
            int remaining = total - checkedIn;

            final int ci = checkedIn;
            final int rem = remaining;

            runOnUiThread(() -> {
                // Update summary bar
                ((TextView) findViewById(R.id.tvCheckedInCount)).setText(String.valueOf(ci));
                ((TextView) findViewById(R.id.tvNotCheckedInCount)).setText(String.valueOf(rem));
                ((TextView) findViewById(R.id.tvTotalCount)).setText(String.valueOf(total));
                ((TextView) findViewById(R.id.tvCount)).setText(ci + " / " + total);

                // Show/hide empty state
                if (attendees.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setAttendees(attendees);
                }
            });
        }).start();
    }

    private String formatScannedAt(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty())
            return "";
        try {
            Date date = ISO_FORMAT.parse(isoDateTime);
            return date != null ? DISPLAY_FORMAT.format(date) : isoDateTime;
        } catch (ParseException e) {
            return isoDateTime;
        }
    }

    // ── Adapter ──────────────────────────────────────────────

    private class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.ViewHolder> {
        private List<AttendeeEntity> attendees = new ArrayList<>();

        public void setAttendees(List<AttendeeEntity> attendees) {
            this.attendees = attendees;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_attendee, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AttendeeEntity a = attendees.get(position);

            holder.tvStudentName.setText(a.getStudentName());
            holder.tvStudentCode.setText(a.getStudentCode());

            if (a.isCheckedIn()) {
                holder.tvCheckinStatus.setText("✓ Checked in");
                holder.tvCheckinStatus.setTextColor(
                        ContextCompat.getColor(AttendeeListActivity.this, R.color.emerald_600));

                // Status dot → emerald
                android.graphics.drawable.Drawable bg = holder.statusDot.getBackground().mutate();
                DrawableCompat.setTint(bg,
                        ContextCompat.getColor(AttendeeListActivity.this, R.color.emerald_500));

                // Show scanned time
                if (a.getScannedAt() != null && !a.getScannedAt().isEmpty()) {
                    holder.tvScannedAt.setVisibility(View.VISIBLE);
                    holder.tvScannedAt.setText(formatScannedAt(a.getScannedAt()));
                } else {
                    holder.tvScannedAt.setVisibility(View.GONE);
                }
            } else {
                holder.tvCheckinStatus.setText("Not checked in");
                holder.tvCheckinStatus.setTextColor(
                        ContextCompat.getColor(AttendeeListActivity.this, R.color.gray_400));

                // Status dot → gray
                android.graphics.drawable.Drawable bg = holder.statusDot.getBackground().mutate();
                DrawableCompat.setTint(bg,
                        ContextCompat.getColor(AttendeeListActivity.this, R.color.gray_300));

                holder.tvScannedAt.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return attendees.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStudentName, tvStudentCode, tvCheckinStatus, tvScannedAt;
            View statusDot;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStudentName = itemView.findViewById(R.id.tvStudentName);
                tvStudentCode = itemView.findViewById(R.id.tvStudentCode);
                tvCheckinStatus = itemView.findViewById(R.id.tvCheckinStatus);
                tvScannedAt = itemView.findViewById(R.id.tvScannedAt);
                statusDot = itemView.findViewById(R.id.statusDot);
            }
        }
    }
}
