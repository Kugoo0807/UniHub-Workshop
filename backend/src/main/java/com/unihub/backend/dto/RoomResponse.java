package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Response payload representing a room.")
public class RoomResponse {

    @Schema(description = "Room ID", example = "1")
    private Long id;

    @Schema(description = "Room name", example = "Hall A")
    private String name;

    @Schema(description = "Maximum capacity", example = "120")
    private Integer capacity;

    @Schema(description = "Cloudinary URL of the room's floor map image", example = "https://res.cloudinary.com/demo/image/upload/unihub/rooms/hall-a.jpg")
    private String layoutMapUrl;

    @Schema(description = "Number of DRAFT or PUBLISHED workshops currently using this room", example = "2")
    private Long activeWorkshopCount;
}
