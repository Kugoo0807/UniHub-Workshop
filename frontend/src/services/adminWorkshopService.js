import axiosClient from '../api/axiosClient';

const adminWorkshopUrl = '/admin/workshops';

const adminWorkshopService = {
    /**
     * Get paginated list of all workshops (Admin only).
     * @param {number} page - 0-indexed page number (default 0)
     * @param {number} size - page size (default 12)
     * @returns {Promise<PageResponse>} - { content, page, size, totalElements, totalPages, last }
     */
    getAll(page = 0, size = 12) {
        return axiosClient.get(adminWorkshopUrl, { params: { page, size } });
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

    /**
     * Cancel a workshop (Admin only).
     */
    cancel(id) {
        return axiosClient.put(`${adminWorkshopUrl}/${id}/cancel`);
    },

    /**
     * Publish a DRAFT workshop (Admin only).
     */
    publish(id) {
        return axiosClient.put(`${adminWorkshopUrl}/${id}/publish`);
    },
};

export default adminWorkshopService;
