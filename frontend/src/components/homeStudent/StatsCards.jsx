import React from 'react';

const StatsCards = ({ pendingCount, historyCount, successCount }) => {
    return (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                <p className="text-sm font-medium text-gray-500">Pending registrations</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{pendingCount}</p>
            </div>
            <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                <p className="text-sm font-medium text-gray-500">History items</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{historyCount}</p>
            </div>
            <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                <p className="text-sm font-medium text-gray-500">Successful check-ins</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{successCount}</p>
            </div>
        </div>
    );
};

export default StatsCards;
