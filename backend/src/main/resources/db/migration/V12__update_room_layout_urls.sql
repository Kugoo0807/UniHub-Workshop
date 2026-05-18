-- V12: Update alternating layout map URLs for rooms

WITH numbered_rooms AS (
    SELECT 
        id,
        ROW_NUMBER() OVER (ORDER BY id) AS row_num
    FROM 
        rooms
    WHERE 
        layout_map_url IS NULL
)
UPDATE rooms
SET layout_map_url = CASE 
    WHEN nr.row_num % 2 = 1 THEN 'https://res.cloudinary.com/dhctxuupz/image/upload/v1779096141/layout-1_imz25m.jpg'
    ELSE 'https://res.cloudinary.com/dhctxuupz/image/upload/v1779096140/layout-2_ololib.jpg'
END
FROM numbered_rooms nr
WHERE rooms.id = nr.id;