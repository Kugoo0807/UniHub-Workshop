import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import workshopService from '../services/workshopService';

const HomeStudent = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    
    const [upcomingWorkshops, setUpcomingWorkshops] = useState([]);
    const [myRegistrations, setMyRegistrations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchData = async () => {
            try {
                setLoading(true);
                setError('');
                
                // Fetch all workshops (in real app would filter for upcoming)
                const workshops = await workshopService.getAll();
                setUpcomingWorkshops(workshops.slice(0, 3) || []);
                
                const registrations = await workshopService.getUserWorkshops();
                setMyRegistrations(
                    registrations.map((item) => ({
                        name: item.title,
                        status:
                            item.status === 'SUCCESS'
                                ? 'Registered'
                                : item.status === 'PENDING'
                                    ? 'Pending payment'
                                    : 'Cancelled',
                        color:
                            item.status === 'SUCCESS'
                                ? 'green'
                                : item.status === 'PENDING'
                                    ? 'yellow'
                                    : 'red',
                    }))
                );
            } catch (err) {
                setError('Failed to load workshops. Please try again.');
                console.error('Error fetching data:', err);
            } finally {
                setLoading(false);
            }
        };
        
        fetchData();
    }, []);

    const handleBrowseWorkshops = () => {
        navigate('/workshops');
    };

    const handleViewProfile = () => {
        navigate('/profile');
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="flex flex-col items-center gap-4">
                    <div className="h-12 w-12 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
                    <p className="text-gray-600">Loading your dashboard...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            {/* Header */}
            <div className="mb-8 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Student Dashboard</h1>
                    <p className="mt-1 text-sm text-gray-500">
                        Welcome back, {user?.fullName ?? 'Student'}
                    </p>
                </div>
            
            </div>

            {error && (
                <div className="mb-4 rounded-lg bg-red-50 border border-red-200 p-3 sm:p-4 text-red-700 text-sm">
                    {error}
                </div>
            )}

            <div className="grid gap-4 sm:gap-6 md:grid-cols-2 lg:grid-cols-3">
                {/* User Info Card */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition">
                    <h3 className="text-sm font-semibold text-gray-500 uppercase mb-3">Your Info</h3>
                    <div className="space-y-2">
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">Tên:</span> {user?.fullName}
                        </p>
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">Email:</span> {user?.email}
                        </p>
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">MSSV:</span> {user?.studentCode || 'N/A'}
                        </p>
                    </div>
                    <button
                        onClick={handleViewProfile}
                        className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition"
                    >
                        Edit Profile
                    </button>
                </section>

                {/* Upcoming Workshops */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-1 lg:col-span-2">
                    <h2 className="mb-4 text-base sm:text-lg font-semibold text-gray-800">
                        Upcoming Workshops
                    </h2>
                    {upcomingWorkshops.length === 0 ? (
                        <p className="text-sm text-gray-500 py-4">No workshops available yet. Check back soon!</p>
                    ) : (
                        <ul className="space-y-2">
                            {upcomingWorkshops.map((workshop) => (
                                <li
                                    key={workshop.id}
                                    className="flex items-center justify-between rounded-lg border border-gray-100 p-3 hover:bg-gray-50 transition cursor-pointer"
                                    onClick={() => navigate('/workshops')}
                                >
                                    <div className="flex items-center gap-3 flex-1 min-w-0">
                                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 font-bold text-sm shrink-0">
                                            {workshop.title?.charAt(0).toUpperCase()}
                                        </span>
                                        <span className="text-sm text-gray-700 truncate">{workshop.title}</span>
                                    </div>
                                    <span className="text-xs text-gray-500 ml-2 shrink-0">
                                        {workshop.remainingSlots === 0 ? 'Full' : `${workshop.remainingSlots} slots`}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                {/* My Registrations */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-1 lg:col-span-1">
                    <h2 className="mb-4 text-base sm:text-lg font-semibold text-gray-800">
                        My Registrations
                    </h2>
                    {myRegistrations.length === 0 ? (
                        <div className="py-4 text-center">
                            <p className="text-sm text-gray-500 mb-3">No registrations yet</p>
                            <button
                                onClick={handleBrowseWorkshops}
                                className="text-xs sm:text-sm font-medium text-indigo-600 hover:text-indigo-700"
                            >
                                Register now →
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-2">
                            {myRegistrations.map(({ name, status, color }) => (
                                <div
                                    key={name}
                                    className="flex items-center justify-between rounded-lg border border-gray-100 p-3 hover:bg-gray-50 transition"
                                >
                                    <span className="text-sm text-gray-700">{name}</span>
                                    <span
                                        className={`rounded-full px-2 py-1 text-xs font-semibold ${
                                            color === 'green'
                                                ? 'bg-green-100 text-green-700'
                                                : 'bg-yellow-100 text-yellow-700'
                                        }`}
                                    >
                                        {status}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </section>

                {/* Quick Actions */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-2 lg:col-span-3">
                    <h2 className="mb-4 text-base sm:text-lg font-semibold text-gray-800">
                        Quick Actions
                    </h2>
                    <div className="flex flex-col sm:flex-row gap-3">
                        <button
                            onClick={handleBrowseWorkshops}
                            className="flex-1 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition"
                        >
                            Browse All Workshops
                        </button>
                        <button
                            onClick={handleViewProfile}
                            className="flex-1 rounded-lg border border-indigo-600 px-4 py-2.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                        >
                            View My Profile
                        </button>
                        <button
                            onClick={() => navigate('/workshops')}
                            className="flex-1 rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition"
                        >
                            Check Attendance
                        </button>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default HomeStudent;
