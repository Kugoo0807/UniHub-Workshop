/**
 * Parses Axios errors and maps them to the backend's ErrorResponse structure.
 * @param {Error} error - The error object caught from an Axios request.
 * @returns {string} A user-friendly error message.
 */
export const handleApiError = (error) => {
    if (error.response) {
        const { message, error: errorType, status } = error.response.data;

        if (message) return message;
        if (errorType) return `Error ${status}: ${errorType}`;
        
        return "An unexpected error occurred on the server.";
    } else if (error.request) {
        return "Network error. Please check your internet connection and try again.";
    } else {
        return error.message || "An unknown error occurred.";
    }
};