package com.example.mobile.data.remote.dto;

public class WorkshopResponse {
    private Long id;
    private String title;
    private String roomName;
    private String startTime;
    private String endTime;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    private String layoutMapUrl;

    public String getLayoutMapUrl() { return layoutMapUrl; }
    public void setLayoutMapUrl(String layoutMapUrl) { this.layoutMapUrl = layoutMapUrl; }
}
