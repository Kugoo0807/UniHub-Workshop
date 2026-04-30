import axiosClient from '../api/axiosClient';

const workshopService = {
    /**
     * Get all workshops (Admin only).
     */
    getAll() {
        return axiosClient.get('/workshops');
    },

    /**
     * Get workshop details by ID (Admin only).
     */
    getById(id) {
        return axiosClient.get(`/workshops/${id}`);
    },

    /**
     * Create a new workshop (Admin only).
     */
    create(data) {
        return axiosClient.post('/workshops', data);
    },

    /**
     * Update an existing workshop (Admin only).
     */
    update(id, data) {
        return axiosClient.put(`/workshops/${id}`, data);
    },

    /**
     * Delete a workshop (Admin only).
     */
    delete(id) {
        return axiosClient.delete(`/workshops/${id}`);
    },

    /**
     * Get workshop statistics (Admin only).
     */
    getStats(id) {
        return axiosClient.get(`/workshops/${id}/stats`);
    },

    /**
     * Upload PDF to generate AI Summary (Admin only).
     */
    uploadAiSummary(id, file) {
        const formData = new FormData();
        formData.append('file', file);
        return axiosClient.post(`/workshops/${id}/ai-summary`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    },
};

export default workshopService;
