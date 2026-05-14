import React from 'react';

const UserInfoCard = ({ user, onViewProfile }) => {
    return (
        <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition">
            <h3 className="text-sm font-semibold text-gray-500 uppercase mb-3">Your Info</h3>
            <div className="space-y-2">
                <p className="text-sm text-gray-700">
                    <span className="font-medium">Name:</span> {user?.fullName}
                </p>
                <p className="text-sm text-gray-700">
                    <span className="font-medium">Email:</span> {user?.email}
                </p>
                <p className="text-sm text-gray-700">
                    <span className="font-medium">Student ID:</span> {user?.studentCode || 'N/A'}
                </p>
            </div>
            <button
                onClick={onViewProfile}
                className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition"
            >
                View Profile
            </button>
        </section>
    );
};

export default UserInfoCard;
