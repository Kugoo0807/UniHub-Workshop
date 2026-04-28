import axios from 'axios';
import { handleApiError } from '../utils/errorHandler';

const axiosClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request Interceptor: Inject Access Token
axiosClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('access_token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response Interceptor: Handle 401 and global errors
axiosClient.interceptors.response.use(
    (response) => {
        // Simply return the data to clean up the response format
        return response.data;
    },
    async (error) => {
        const originalRequest = error.config;

        // Prevent infinite loops by checking if we already tried to refresh
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            
            try {
                const refreshToken = localStorage.getItem('refresh_token');
                if (!refreshToken) throw new Error("No refresh token available");

                // Assuming you have an endpoint for refreshing the token
                const refreshResponse = await axios.post(
                    `${axiosClient.defaults.baseURL}/auth/refresh`, 
                    { token: refreshToken }
                );

                const newAccessToken = refreshResponse.data.accessToken;
                localStorage.setItem('access_token', newAccessToken);

                // Update the original request with the new token and retry
                originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`;
                return axiosClient(originalRequest);
                
            } catch (refreshError) {
                // If refresh fails, log the user out (force clear local storage)
                localStorage.clear();
                window.location.href = '/login';
                return Promise.reject(refreshError);
            }
        }

        // Format error message using the custom utility
        const formattedError = handleApiError(error);
        return Promise.reject(new Error(formattedError));
    }
);

export default axiosClient;