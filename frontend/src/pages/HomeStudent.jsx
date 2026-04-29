import React from 'react';
import { useAuth } from '../context/AuthContext';

const HomeStudent = () => {
    const { user } = useAuth();

    return (
        <div className="min-h-screen bg-gray-50 p-6">
            {/* Welcome Banner */}
            <div className="mb-8 rounded-2xl bg-indigo-600 px-8 py-10 text-white shadow-md">
                <h1 className="text-3xl font-bold">
                    Welcome back, {user?.fullName ?? 'Student'} 👋
                </h1>
                <p className="mt-2 text-indigo-100">
                    Explore and register for upcoming workshops at UniHub.
                </p>
            </div>

            <div className="grid gap-6 md:grid-cols-2">
                {/* Upcoming Workshops */}
                <section className="rounded-2xl bg-white p-6 shadow-sm">
                    <h2 className="mb-4 text-xl font-semibold text-gray-800">
                        Your Upcoming Workshops
                    </h2>
                    <ul className="space-y-3">
                        {[
                            'Introduction to Machine Learning',
                            'Web Development with React',
                            'Data Structures & Algorithms',
                        ].map((name) => (
                            <li
                                key={name}
                                className="flex items-center gap-3 rounded-lg border border-gray-100 p-3 text-gray-700 hover:bg-indigo-50 transition"
                            >
                                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 font-bold text-sm">
                                    W
                                </span>
                                <span>{name}</span>
                            </li>
                        ))}
                    </ul>
                    <p className="mt-4 text-sm text-gray-400 italic">
                        * Placeholder data — real workshops will load from the API.
                    </p>
                </section>

                {/* My Registrations */}
                <section className="rounded-2xl bg-white p-6 shadow-sm">
                    <h2 className="mb-4 text-xl font-semibold text-gray-800">
                        My Registrations
                    </h2>
                    <div className="space-y-3">
                        {[
                            { name: 'UI/UX Bootcamp', status: 'Confirmed', color: 'green' },
                            { name: 'Cloud Computing 101', status: 'Pending', color: 'yellow' },
                        ].map(({ name, status, color }) => (
                            <div
                                key={name}
                                className="flex items-center justify-between rounded-lg border border-gray-100 p-3"
                            >
                                <span className="text-gray-700">{name}</span>
                                <span
                                    className={`rounded-full px-3 py-0.5 text-xs font-semibold ${
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
                    <p className="mt-4 text-sm text-gray-400 italic">
                        * Placeholder data — real registrations will load from the API.
                    </p>
                </section>

                {/* Quick Actions */}
                <section className="rounded-2xl bg-white p-6 shadow-sm md:col-span-2">
                    <h2 className="mb-4 text-xl font-semibold text-gray-800">Quick Actions</h2>
                    <div className="flex flex-wrap gap-3">
                        <button className="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition">
                            Browse All Workshops
                        </button>
                        <button className="rounded-lg border border-indigo-600 px-5 py-2.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition">
                            View My Profile
                        </button>
                        <button className="rounded-lg border border-gray-300 px-5 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition">
                            Check Attendance
                        </button>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default HomeStudent;
