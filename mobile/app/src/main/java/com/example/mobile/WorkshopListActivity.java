package com.example.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.mobile.data.remote.ApiService;
import com.example.mobile.data.remote.RetrofitClient;
import com.example.mobile.data.remote.dto.PageResponse;
import com.example.mobile.data.remote.dto.WorkshopResponse;
import com.example.mobile.data.repository.CheckinRepository;
import com.example.mobile.util.TokenManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.android.material.tabs.TabLayout;

import com.bumptech.glide.Glide;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WorkshopListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private WorkshopAdapter adapter;
    private ApiService apiService;
    private CheckinRepository repository;
    private TabLayout tabLayout;
    private List<WorkshopResponse> allWorkshops = new ArrayList<>();
    private Button btnSyncAll;
    private ProgressBar syncProgress;
    private boolean isSyncInProgress = false;

    // Input format from backend (ISO-8601 without timezone)
    private static final SimpleDateFormat ISO_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    // Display format
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workshop_list);

        apiService = RetrofitClient.getApiService(this);
        repository = new CheckinRepository(this);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WorkshopAdapter();
        recyclerView.setAdapter(adapter);

        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterWorkshops();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new TokenManager(this).clearTokens();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnSyncAll = findViewById(R.id.btnSyncAll);
        syncProgress = findViewById(R.id.syncProgress);
        btnSyncAll.setOnClickListener(v -> triggerManualSync());

        loadWorkshops();
    }

    private void triggerManualSync() {
        if (isSyncInProgress) {
            return;
        }

        isSyncInProgress = true;
        btnSyncAll.setEnabled(false);
        syncProgress.setVisibility(View.VISIBLE);

        UUID workId = repository.triggerBackgroundSync();
        LiveData<WorkInfo> workLiveData = WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(workId);
        workLiveData.observe(this, workInfo -> {
            if (workInfo == null || !workInfo.getState().isFinished()) {
                return;
            }

            workLiveData.removeObservers(this);

            boolean isSuccess = workInfo.getState() == WorkInfo.State.SUCCEEDED;
            String message = isSuccess ? "Sync completed" : "Sync failed";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            isSyncInProgress = false;
            btnSyncAll.setEnabled(true);
            syncProgress.setVisibility(View.GONE);
        });
    }

    private void loadWorkshops() {
        apiService.getWorkshops(0, 50).enqueue(new Callback<PageResponse<WorkshopResponse>>() {
            @Override
            public void onResponse(Call<PageResponse<WorkshopResponse>> call, Response<PageResponse<WorkshopResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allWorkshops = response.body().getContent();
                    filterWorkshops();
                } else {
                    Toast.makeText(WorkshopListActivity.this, "Failed to load workshops", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PageResponse<WorkshopResponse>> call, Throwable t) {
                Toast.makeText(WorkshopListActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterWorkshops() {
        if (tabLayout.getSelectedTabPosition() == 1) { // Today
            List<WorkshopResponse> todayWorkshops = new ArrayList<>();
            for (WorkshopResponse w : allWorkshops) {
                if (isToday(w.getStartTime())) {
                    todayWorkshops.add(w);
                }
            }
            adapter.setWorkshops(todayWorkshops);
            
            // Toggle empty state visibility based on filtered list
            findViewById(R.id.emptyState).setVisibility(todayWorkshops.isEmpty() ? View.VISIBLE : View.GONE);
        } else { // All
            adapter.setWorkshops(allWorkshops);
            findViewById(R.id.emptyState).setVisibility(allWorkshops.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isToday(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) return false;
        try {
            Date date = ISO_FORMAT.parse(isoDateTime);
            if (date == null) return false;
            Calendar cal1 = Calendar.getInstance();
            Calendar cal2 = Calendar.getInstance();
            cal1.setTime(new Date());
            cal2.setTime(date);
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Format ISO datetime string into a human-readable display format.
     */
    private String formatDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) return "—";
        try {
            Date date = ISO_FORMAT.parse(isoDateTime);
            return date != null ? DISPLAY_FORMAT.format(date) : isoDateTime;
        } catch (ParseException e) {
            return isoDateTime; // fallback to raw string
        }
    }

    private class WorkshopAdapter extends RecyclerView.Adapter<WorkshopAdapter.ViewHolder> {
        private List<WorkshopResponse> workshops = new ArrayList<>();

        public void setWorkshops(List<WorkshopResponse> workshops) {
            this.workshops = workshops;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_workshop, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WorkshopResponse w = workshops.get(position);
            holder.tvTitle.setText(w.getTitle());
            holder.tvRoom.setText(w.getRoomName());
            holder.tvTime.setText(formatDateTime(w.getStartTime()) + " → " + formatDateTime(w.getEndTime()));

            // Status badge styling
            String status = w.getStatus() != null ? w.getStatus() : "UNKNOWN";
            holder.tvStatus.setText(status);
            switch (status) {
                case "PUBLISHED":
                    holder.tvStatus.setTextColor(ContextCompat.getColor(WorkshopListActivity.this, R.color.emerald_600));
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_status_badge); // Default light indigo background
                    break;
                case "CANCELLED":
                    holder.tvStatus.setTextColor(ContextCompat.getColor(WorkshopListActivity.this, R.color.red_600));
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_status_badge);
                    break;
                default:
                    holder.tvStatus.setTextColor(ContextCompat.getColor(WorkshopListActivity.this, R.color.gray_600));
                    holder.tvStatus.setBackgroundResource(R.drawable.bg_status_badge);
                    break;
            }

            // Seat map handling
            String layoutMapUrl = w.getLayoutMapUrl();
            if (layoutMapUrl != null && !layoutMapUrl.isEmpty()) {
                holder.btnToggleSeatMap.setVisibility(View.VISIBLE);
                // Reset state when recycled
                holder.seatMapContainer.setVisibility(View.GONE);
                holder.btnToggleSeatMap.setText("🪑 View Seat Map");

                holder.btnToggleSeatMap.setOnClickListener(v -> {
                    if (holder.seatMapContainer.getVisibility() == View.GONE) {
                        holder.seatMapContainer.setVisibility(View.VISIBLE);
                        holder.btnToggleSeatMap.setText("🪑 Hide Seat Map");
                        holder.seatMapProgress.setVisibility(View.VISIBLE);

                        Glide.with(WorkshopListActivity.this)
                                .load(layoutMapUrl)
                                .fitCenter()
                                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                                    @Override
                                    public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource,
                                            com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                                        holder.seatMapProgress.setVisibility(View.GONE);
                                        holder.ivSeatMap.setImageDrawable(resource);
                                    }

                                    @Override
                                    public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
                                        holder.seatMapProgress.setVisibility(View.GONE);
                                    }

                                    @Override
                                    public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                                        holder.seatMapProgress.setVisibility(View.GONE);
                                        Toast.makeText(WorkshopListActivity.this, "Failed to load seat map", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        holder.seatMapContainer.setVisibility(View.GONE);
                        holder.btnToggleSeatMap.setText("🪑 View Seat Map");
                    }
                });
            } else {
                holder.btnToggleSeatMap.setVisibility(View.GONE);
                holder.seatMapContainer.setVisibility(View.GONE);
            }

            holder.btnDownload.setOnClickListener(v -> {
                holder.btnDownload.setEnabled(false);
                holder.btnDownload.setText("Downloading...");
                repository.downloadAttendees(w.getId(), new CheckinRepository.DownloadCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            holder.btnDownload.setEnabled(true);
                            holder.btnDownload.setText(getString(R.string.btn_download));
                            Toast.makeText(WorkshopListActivity.this, "Downloaded successfully", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            holder.btnDownload.setEnabled(true);
                            holder.btnDownload.setText(getString(R.string.btn_download));
                            Toast.makeText(WorkshopListActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });

            holder.btnScan.setOnClickListener(v -> {
                Intent intent = new Intent(WorkshopListActivity.this, ScanActivity.class);
                intent.putExtra("WORKSHOP_ID", w.getId());
                startActivity(intent);
            });

            holder.btnViewAttendees.setOnClickListener(v -> {
                Intent intent = new Intent(WorkshopListActivity.this, AttendeeListActivity.class);
                intent.putExtra("WORKSHOP_ID", w.getId());
                intent.putExtra("WORKSHOP_TITLE", w.getTitle());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return workshops.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvRoom, tvTime, tvStatus;
            Button btnDownload, btnScan, btnViewAttendees, btnToggleSeatMap;
            FrameLayout seatMapContainer;
            ImageView ivSeatMap;
            ProgressBar seatMapProgress;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvRoom = itemView.findViewById(R.id.tvRoom);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                btnScan = itemView.findViewById(R.id.btnScan);
                btnViewAttendees = itemView.findViewById(R.id.btnViewAttendees);
                btnToggleSeatMap = itemView.findViewById(R.id.btnToggleSeatMap);
                seatMapContainer = itemView.findViewById(R.id.seatMapContainer);
                ivSeatMap = itemView.findViewById(R.id.ivSeatMap);
                seatMapProgress = itemView.findViewById(R.id.seatMapProgress);
            }
        }
    }
}
