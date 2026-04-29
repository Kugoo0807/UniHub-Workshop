import React from 'react';
import { useAuth } from '../context/AuthContext';

const StatCard = ({ label, value, color }) => (
    <div className={`rounded-2xl p-6 shadow-sm text-white ${color}`}>
        <p className="text-sm font-medium opacity-80">{label}</p>
        <p className="mt-2 text-4xl font-bold">{value}</p>
    </div>
);

const SectionCard = ({ title, children }) => (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-xl font-semibold text-gray-800">{title}</h2>
        {children}
    </section>
);

const Dashboard = () => {
    const { user } = useAuth();

    const stats = [
        { label: 'Total Users', value: '—', color: 'bg-indigo-600' },
        { label: 'Active Workshops', value: '—', color: 'bg-emerald-500' },
        { label: 'Total Registrations', value: '—', color: 'bg-violet-500' },
        { label: 'Pending Payments', value: '—', color: 'bg-amber-500' },
    ];

    const workshops = [
        { name: 'React Fundamentals', date: '2026-05-10', capacity: 30, registered: 18 },
        { name: 'Intro to Machine Learning', date: '2026-05-15', capacity: 25, registered: 25 },
        { name: 'Agile & Scrum Basics', date: '2026-05-22', capacity: 20, registered: 7 },
    ];

    const users = [
        { name: 'Nguyen Van A', role: 'STUDENT', status: 'Active' },
        { name: 'Tran Thi B', role: 'STUDENT', status: 'Inactive' },
        { name: 'Le Van C', role: 'ADMIN', status: 'Active' },
    ];

    return (
        <div className="min-h-screen bg-gray-50 p-6">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900">Admin Dashboard</h1>
                <p className="mt-1 text-gray-500">
                    Logged in as <span className="font-medium text-indigo-600">{user?.fullName ?? 'Admin'}</span>
                </p>
            </div>

            {/* Overview Stats */}
            <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {stats.map((s) => (
                    <StatCard key={s.label} {...s} />
                ))}
            </div>

            <p className="mb-6 text-sm text-gray-400 italic">
                * Statistics are placeholders — real data will load from the API.
            </p>

            <div className="grid gap-6 lg:grid-cols-2">
                {/* Workshop Management */}
                <SectionCard title="Workshop Management">
                    <table className="w-full text-sm text-left text-gray-600">
                        <thead>
                            <tr className="border-b text-gray-500 uppercase text-xs tracking-wider">
                                <th className="pb-2 pr-4">Workshop</th>
                                <th className="pb-2 pr-4">Date</th>
                                <th className="pb-2">Seats</th>
                            </tr>
                        </thead>
                        <tbody>
                            {workshops.map((w) => (
                                <tr key={w.name} className="border-b last:border-0">
                                    <td className="py-3 pr-4 font-medium text-gray-800">{w.name}</td>
                                    <td className="py-3 pr-4">{w.date}</td>
                                    <td className="py-3">
                                        <span
                                            className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                                                w.registered >= w.capacity
                                                    ? 'bg-red-100 text-red-700'
                                                    : 'bg-green-100 text-green-700'
                                            }`}
                                        >
                                            {w.registered}/{w.capacity}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <button className="mt-4 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition">
                        + Create Workshop
                    </button>
                </SectionCard>

                {/* User Management */}
                <SectionCard title="User Management">
                    <table className="w-full text-sm text-left text-gray-600">
                        <thead>
                            <tr className="border-b text-gray-500 uppercase text-xs tracking-wider">
                                <th className="pb-2 pr-4">Name</th>
                                <th className="pb-2 pr-4">Role</th>
                                <th className="pb-2">Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {users.map((u) => (
                                <tr key={u.name} className="border-b last:border-0">
                                    <td className="py-3 pr-4 font-medium text-gray-800">{u.name}</td>
                                    <td className="py-3 pr-4">
                                        <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-semibold text-indigo-700">
                                            {u.role}
                                        </span>
                                    </td>
                                    <td className="py-3">
                                        <span
                                            className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                                                u.status === 'Active'
                                                    ? 'bg-green-100 text-green-700'
                                                    : 'bg-gray-100 text-gray-500'
                                            }`}
                                        >
                                            {u.status}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <button className="mt-4 rounded-lg border border-indigo-600 px-4 py-2 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition">
                        Manage All Users
                    </button>
                </SectionCard>
            </div>
        </div>
    );
};

export default Dashboard;
