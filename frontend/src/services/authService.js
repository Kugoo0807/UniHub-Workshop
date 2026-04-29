import axiosClient from '../api/axiosClient';

const authService = {
    studentRegister(data) {
        return axiosClient.post('/auth/register/student', data);
    },

    adminStaffRegister(data) {
        return axiosClient.post('/auth/register/admin-staff', data);
    },

    webLogin(data) {
        return axiosClient.post('/auth/login/web', data);
    },

    appLogin(data) {
        return axiosClient.post('/auth/login/app', data);
    },

    refreshToken(refreshToken) {
        return axiosClient.post('/auth/refresh', { refreshToken });
    },
};

export default authService;
