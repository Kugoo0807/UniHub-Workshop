import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProfilePage = () => {
    const { user } = useAuth();
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-gray-50 p-4 sm:p-6">
            <div className="max-w-2xl mx-auto">
                {/* Header */}
                <div className="flex items-center justify-between mb-8">
                    <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">My Profile</h1>
                    <button
                        onClick={() => navigate(-1)}
                        className="rounded-lg px-4 py-2 text-gray-600 hover:bg-gray-100 transition"
                    >
                        ← Back
                    </button>
                </div>

                {/* Profile Card */}
                <div className="rounded-2xl bg-white p-6 sm:p-8 shadow-sm border border-gray-100">
                    {/* Profile Header */}
                    <div className="flex items-center gap-6 mb-8 pb-8 border-b border-gray-200">
                        <div className="h-20 w-20 rounded-full bg-linear-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white text-2xl font-bold">
                            {user?.fullName?.charAt(0).toUpperCase()}
                        </div>
                        <div className="flex-1">
                            <h2 className="text-xl sm:text-2xl font-bold text-gray-900">{user?.fullName}</h2>
                            <p className="text-gray-500 text-sm sm:text-base">{user?.email}</p>
                            <span className="inline-block mt-2 px-3 py-1 bg-indigo-100 text-indigo-700 rounded-full text-xs font-semibold">
                                {user?.role || 'Student'}
                            </span>
                        </div>
                    </div>

                    {/* Info Fields */}
                    <div className="space-y-5">
                        {/* Full Name */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Full Name
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.fullName}</p>
                        </div>

                        {/* Email */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Email Address
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.email}</p>
                        </div>

                        {/* Student Code */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Student Code (MSSV)
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.studentCode || 'N/A'}</p>
                        </div>

                        {/* Phone Number */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Phone Number
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.phoneNumber || 'Not provided'}</p>
                        </div>

                        {/* Account Status */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Account Status
                            </label>
                            <p className="px-4 py-2 bg-green-50 rounded-lg text-green-700 font-medium">
                                Active
                            </p>
                        </div>
                    </div>
                </div>

                {/* Additional Info */}
                <div className="mt-6 rounded-2xl bg-blue-50 border border-blue-200 p-4 sm:p-6">
                    <h3 className="font-semibold text-blue-900 mb-2">Tip</h3>
                    <p className="text-sm text-blue-800">
                        Your account is fully linked with your HCMUS student profile. Some information like Email and Student Code cannot be changed for security reasons.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;
