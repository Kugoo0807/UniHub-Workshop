import React from 'react';

const ErrorBanner = ({ message }) => {
    if (!message) return null;

    return (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 p-3 sm:p-4 text-red-700 text-sm">
            {message}
        </div>
    );
};

export default ErrorBanner;
