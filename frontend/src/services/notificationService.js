import axiosClient from '../api/axiosClient';

const NOTIFICATION_API = '/notifications';
const DEFAULT_PAGE_SIZE = 10;

const notificationService = {
    getMyNotifications: async (page = 0, size = DEFAULT_PAGE_SIZE) => {
        const response = await axiosClient.get(NOTIFICATION_API, {
            params: { page, size },
        });
        return response;
    },
};

export default notificationService;
