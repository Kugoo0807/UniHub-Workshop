import React from 'react';

const LoadingState = () => {
    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
            <div className="flex flex-col items-center gap-4">
                <div className="h-12 w-12 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
                <p className="text-gray-600">Loading your dashboard...</p>
            </div>
        </div>
    );
};

export default LoadingState;
