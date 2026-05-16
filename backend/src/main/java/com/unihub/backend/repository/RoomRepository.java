package com.unihub.backend.repository;

import com.unihub.backend.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Room> findAllByOrderByIdAsc();

    /**
     * Count DRAFT or PUBLISHED workshops currently assigned to this room.
     * Used to prevent deleting rooms that are still in use.
     */
    @Query("""
            SELECT COUNT(w) FROM Workshop w
            WHERE w.room.id = :roomId
              AND w.status IN ('DRAFT', 'PUBLISHED')
            """)
    long countActiveWorkshopsByRoomId(@Param("roomId") Long roomId);

    /**
     * Batch version of countActiveWorkshopsByRoomId for multiple room IDs at once.
     * Returns a list of Object[] where index 0 is roomId and index 1 is the count.
     */
    @Query("""
            SELECT w.room.id, COUNT(w) FROM Workshop w
            WHERE w.room.id IN :roomIds
              AND w.status IN ('DRAFT', 'PUBLISHED')
            GROUP BY w.room.id
            """)
    List<Object[]> countActiveWorkshopsByRoomIds(@Param("roomIds") List<Long> roomIds);
}
