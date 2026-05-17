package com.unihub.backend.service;

import com.unihub.backend.dto.RoomRequest;
import com.unihub.backend.dto.RoomResponse;
import com.unihub.backend.entity.Room;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.RoomRepository;
import io.micrometer.observation.annotation.ObservationKeyValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final String CLOUDINARY_FOLDER = "unihub/rooms";

    private final RoomRepository roomRepository;
    private final CloudinaryService cloudinaryService;

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        List<Room> rooms = roomRepository.findAll();

        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        Map<Long, Long> activeCounts = getSuccessfulCountActiveWorkshopByRooms(roomIds);

        return rooms.stream()
                .map(room -> toResponse(room, activeCounts.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {
        Room room = findOrThrow(id);
        Long activeCount = roomRepository.countActiveWorkshopsByRoomId(id);
        return toResponse(room, activeCount);
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        if (roomRepository.existsByName(request.name())) {
            throw new ConflictException("Room name already exists: " + request.name());
        }
        Room room = Room.builder()
                .name(request.name().trim())
                .capacity(request.capacity())
                .build();
        room = roomRepository.save(room);
        log.info("Created room id={} name={}", room.getId(), room.getName());

        Long activeCount = roomRepository.countActiveWorkshopsByRoomId(room.getId());
        return toResponse(room, activeCount);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        Room room = findOrThrow(id);
        // Check unique name constraint (exclude self)
        if (roomRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Room name already exists: " + request.name());
        }
        // Block capacity change when active workshops are using this room
        if (!request.capacity().equals(room.getCapacity())) {
            long activeCount = roomRepository.countActiveWorkshopsByRoomId(id);
            if (activeCount > 0) {
                throw new ConflictException(
                        "Cannot change capacity while " + activeCount +
                        " active workshop(s) are using this room. " +
                        "Cancel or complete those workshops first."
                );
            }
        }
        room.setName(request.name().trim());
        room.setCapacity(request.capacity());
        room = roomRepository.save(room);
        log.info("Updated room id={}", id);

        Long activeCount = roomRepository.countActiveWorkshopsByRoomId(room.getId());
        return toResponse(room, activeCount);
    }

    // ─── Upload map image ─────────────────────────────────────────────────────

    @Transactional
    public RoomResponse uploadMapImage(Long id, MultipartFile file) {
        Room room = findOrThrow(id);
        validateImageFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
        }

        String url = cloudinaryService.uploadImage(bytes, CLOUDINARY_FOLDER);
        room.setLayoutMapUrl(url);
        room = roomRepository.save(room);
        log.info("Updated layoutMapUrl for room id={}: {}", id, url);

        Long activeCount = roomRepository.countActiveWorkshopsByRoomId(room.getId());
        return toResponse(room, activeCount);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void deleteRoom(Long id) {
        Room room = findOrThrow(id);
        
        long totalWorkshops = roomRepository.countAllWorkshopsByRoomId(id);
        if (totalWorkshops > 0) {
            long activeWorkshops = roomRepository.countActiveWorkshopsByRoomId(id);
            if (activeWorkshops > 0) {
                throw new ConflictException(
                        "Room is currently used by " + activeWorkshops + " active workshop(s). " +
                        "Cancel or delete those workshops first."
                );
            } else {
                throw new ConflictException(
                        "Cannot delete room. It is referenced by " + totalWorkshops + " historical workshop(s)."
                );
            }
        }
        
        roomRepository.delete(room);
        log.info("Deleted room id={}", id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Room findOrThrow(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Only image files are accepted (jpeg, png, webp). Received: " + contentType
            );
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }
    }

    private RoomResponse toResponse(Room room, Long activeCount) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .layoutMapUrl(room.getLayoutMapUrl())
                .activeWorkshopCount(activeCount)
                .build();
    }

    private Map<Long, Long> getSuccessfulCountActiveWorkshopByRooms(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> counts = roomRepository.countActiveWorkshopsByRoomIds(roomIds);

        return counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0], // roomId
                        row -> (Long) row[1]  // activeCount
                ));
    }
}
