import React from 'react';

const HomeStudentHeader = ({ userName, onViewRegistrations }) => {
    return (
        <div className="mb-8 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
            <div>
                <h1 className="text-3xl font-bold text-gray-900">Student Dashboard</h1>
                <p className="mt-1 text-sm text-gray-500">
                    Welcome back, {userName || 'Student'}
                </p>
            </div>
        </div>
    );
};

export default HomeStudentHeader;
