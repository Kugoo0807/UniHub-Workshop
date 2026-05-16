import axiosClient from '../api/axiosClient';

const adminRoomUrl = '/admin/rooms';

const adminRoomService = {
    /**
     * Get all rooms (Admin only).
     * @returns {Promise<RoomResponse[]>}
     */
    getAll() {
        return axiosClient.get(adminRoomUrl);
    },

    /**
     * Get a single room by ID (Admin only).
     * @param {number} id
     */
    getById(id) {
        return axiosClient.get(`${adminRoomUrl}/${id}`);
    },

    /**
     * Create a new room (Admin only).
     * @param {{ name: string, capacity: number }} data
     */
    create(data) {
        return axiosClient.post(adminRoomUrl, data);
    },

    /**
     * Update room name/capacity (Admin only).
     * Does NOT update the map image.
     * @param {number} id
     * @param {{ name: string, capacity: number }} data
     */
    update(id, data) {
        return axiosClient.put(`${adminRoomUrl}/${id}`, data);
    },

    /**
     * Upload a floor map image to Cloudinary (Admin only).
     * @param {number} id
     * @param {File} file - JPEG / PNG / WEBP, max 5MB
     */
    uploadMapImage(id, file) {
        const formData = new FormData();
        formData.append('file', file);
        return axiosClient.post(`${adminRoomUrl}/${id}/map-image`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },

    /**
     * Delete a room (Admin only).
     * Fails if the room has active (DRAFT/PUBLISHED) workshops.
     * @param {number} id
     */
    delete(id) {
        return axiosClient.delete(`${adminRoomUrl}/${id}`);
    },
};

export default adminRoomService;
