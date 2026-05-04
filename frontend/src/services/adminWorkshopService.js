import axiosClient from '../api/axiosClient';

const adminWorkshopUrl = '/admin/workshops';

const adminWorkshopService = {
    /**
     * Get all workshops (Admin only).
     */
    getAll() {
        return axiosClient.get(adminWorkshopUrl);
    },

    /**
     * Get workshop details by ID (Admin only).
     */
    getById(id) {
        return axiosClient.get(`${adminWorkshopUrl}/${id}`);
    },

    /**
     * Create a new workshop (Admin only).
     */
    create(data) {
        return axiosClient.post(adminWorkshopUrl, data);
    },

    /**
     * Update an existing workshop (Admin only).
     */
    update(id, data) {
        return axiosClient.put(`${adminWorkshopUrl}/${id}`, data);
    },

    /**
     * Delete a workshop (Admin only).
     */
    delete(id) {
        return axiosClient.delete(`${adminWorkshopUrl}/${id}`);
    },

    /**
     * Get workshop statistics (Admin only).
     */
    getStats(id) {
        return axiosClient.get(`${adminWorkshopUrl}/${id}/stats`);
    },

    /**
     * Upload PDF to generate AI Summary (Admin only).
     */
    uploadAiSummary(id, file) {
        const formData = new FormData();
        formData.append('file', file);
        return axiosClient.post(`${adminWorkshopUrl}/${id}/ai-summary`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    },
};

export default adminWorkshopService;
