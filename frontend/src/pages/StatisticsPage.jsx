import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    PieChart, Pie, Cell, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar,
    AreaChart, Area,
} from 'recharts';
import adminWorkshopService from '../services/adminWorkshopService';

/* ─── Helpers ───────────────────────────────────────────────── */
const PALETTE = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316', '#ec4899'];
const round1 = (v) => (v != null ? Number(v).toFixed(1) : '0.0');
const fmtMoney = (v) => !v ? '₫0' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v);

/* ─── Metric Card: Modernized with subtle glass effect ───────── */
const MetricCard = ({ label, value, sub, color, icon }) => (
    <div className="group relative overflow-hidden rounded-2xl bg-white p-5 shadow-[0_2px_15px_-3px_rgba(0,0,0,0.07),0_10px_20px_-2px_rgba(0,0,0,0.04)] transition-all duration-300 hover:-translate-y-1 hover:shadow-xl border border-gray-100">
        <div className="flex items-start justify-between">
            <div className="space-y-2">
                <p className="text-[13px] font-medium text-gray-500">{label}</p>
                <h3 className="text-3xl font-black tracking-tight text-gray-900">{value}</h3>
                <div className="flex items-center gap-1.5">
                    <span className="flex h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }} />
                    <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wider">{sub}</p>
                </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl transition-transform group-hover:scale-110"
                style={{ backgroundColor: `${color}15`, color: color }}>
                {icon}
            </div>
        </div>
        {/* Abstract background shape */}
        <div className="absolute -right-4 -bottom-4 h-16 w-16 opacity-[0.03] transition-opacity group-hover:opacity-[0.08]"
            style={{ color: color }}>
            <svg fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" /></svg>
        </div>
    </div>
);

/* ─── Chart Card: Better padding and typography ──────────────── */
const ChartCard = ({ title, subtitle, children, className = '' }) => (
    <div className={`rounded-2xl bg-white border border-gray-100 shadow-sm p-6 hover:border-indigo-100 transition-colors ${className}`}>
        <div className="mb-6">
            <h2 className="text-base font-bold text-gray-800 tracking-tight">{title}</h2>
            {subtitle && <p className="mt-1 text-[13px] text-gray-400 font-medium">{subtitle}</p>}
        </div>
        <div className="w-full">
            {children}
        </div>
    </div>
);

/* ─── Custom Tooltip: Deep shadow and sleek design ───────────── */
const ChartTooltip = ({ active, payload, label, fmt = v => v }) => {
    if (!active || !payload?.length) return null;
    return (
        <div className="rounded-xl bg-white/95 backdrop-blur-md border border-gray-100 p-3 shadow-[0_10px_40px_-10px_rgba(0,0,0,0.1)] text-xs min-w-[140px]">
            {label && <p className="text-gray-900 font-bold mb-2 px-1 border-b border-gray-50 pb-2">{label}</p>}
            <div className="space-y-1.5">
                {payload.map((p, i) => (
                    <div key={i} className="flex items-center justify-between gap-4 px-1">
                        <div className="flex items-center gap-2">
                            <span className="h-2 w-2 rounded-full" style={{ background: p.color || p.fill }} />
                            <span className="text-gray-500 font-medium">{p.name}</span>
                        </div>
                        <span className="font-bold text-gray-900">{fmt(p.value)}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};

/* ─── Donut: Updated with better progress bars ────────────────── */
const DonutWithLegend = ({ data, colors }) => (
    <div className="flex flex-col sm:flex-row items-center gap-8">
        <div className="relative shrink-0">
            <ResponsiveContainer width={160} height={160}>
                <PieChart>
                    <Pie data={data} cx="50%" cy="50%" innerRadius={55} outerRadius={75}
                        paddingAngle={5} dataKey="value" startAngle={90} endAngle={-270}>
                        {data.map((_, i) => <Cell key={i} fill={colors[i]} stroke="white" strokeWidth={2} />)}
                    </Pie>
                    <Tooltip content={<ChartTooltip fmt={v => `${v}%`} />} />
                </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-[10px] uppercase font-bold text-gray-400">Total</span>
                <span className="text-lg font-black text-gray-800">100%</span>
            </div>
        </div>
        <div className="flex-1 w-full space-y-4">
            {data.map((item, i) => (
                <div key={i} className="group">
                    <div className="flex items-center justify-between mb-1.5">
                        <div className="flex items-center gap-2">
                            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: colors[i] }} />
                            <span className="text-[13px] font-semibold text-gray-600">{item.name}</span>
                        </div>
                        <span className="text-[13px] font-bold" style={{ color: colors[i] }}>{item.value}%</span>
                    </div>
                    <div className="h-2 w-full rounded-full bg-gray-50 overflow-hidden border border-gray-100">
                        <div className="h-full rounded-full transition-all duration-1000 ease-out"
                            style={{ width: `${item.value}%`, background: colors[i] }} />
                    </div>
                </div>
            ))}
        </div>
    </div>
);

const AX = { fontSize: 11, fill: '#94a3b8', fontWeight: 500 };
const GR = { strokeDasharray: '4 4', stroke: '#f1f5f9' };

const StatisticsPage = () => {
    const navigate = useNavigate();
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        adminWorkshopService.getGlobalStats()
            .then(setData)
            .catch(e => setError(e.message || 'Failed to load statistics'))
            .finally(() => setLoading(false));
    }, []);

    if (loading) return (
        <div className="flex min-h-screen items-center justify-center bg-[#fafafa]">
            <div className="relative">
                <div className="h-16 w-16 animate-spin rounded-full border-[3px] border-indigo-100 border-t-indigo-600" />
                <div className="mt-4 text-center text-sm font-bold text-gray-400 animate-pulse">Analysing...</div>
            </div>
        </div>
    );

    const {
        totalRevenue, totalWorkshops, totalRegistrations,
        paymentSuccessRate, paymentFailureRate, actualParticipationRate, cancellationRate,
        topSpeakers = [], roomUtilization = [], registrationsByHour = [], workshopFillRates = [],
    } = data || {};

    const hourMap = {};
    registrationsByHour.forEach(h => { hourMap[h.hour] = h.count; });
    const hourlyData = Array.from({ length: 24 }, (_, i) => ({
        hour: `${String(i).padStart(2, '0')}:00`,
        Count: hourMap[i] || 0,
    }));

    const ps = +round1(paymentSuccessRate);
    const pf = +round1(paymentFailureRate);
    const po = +(Math.max(0, 100 - ps - pf)).toFixed(1);
    const payPie = [
        { name: 'Successful', value: ps },
        { name: 'Failed', value: pf },
        ...(po > 0 ? [{ name: 'Pending', value: po }] : []),
    ].filter(d => d.value > 0);
    const PAY_CLR = ['#10b981', '#ef4444', '#f1f5f9'];

    const av = +round1(actualParticipationRate);
    const cv = +round1(cancellationRate);
    const ov = +(Math.max(0, 100 - av - cv)).toFixed(1);
    const attPie = [
        { name: 'Attended', value: av },
        { name: 'Cancelled', value: cv },
        ...(ov > 0 ? [{ name: 'Registered', value: ov }] : []),
    ].filter(d => d.value > 0);
    const ATT_CLR = ['#6366f1', '#f59e0b', '#f1f5f9'];

    const top10 = [...workshopFillRates]
        .sort((a, b) => b.fillRate - a.fillRate).slice(0, 10)
        .map(w => ({
            ...w,
            name: w.title.length > 22 ? w.title.slice(0, 22) + '...' : w.title,
            color: w.fillRate >= 90 ? '#ef4444' : w.fillRate >= 60 ? '#f59e0b' : '#10b981',
        }));

    return (
        <div className="min-h-screen bg-[#fafafa] pb-12 font-sans antialiased">
            {/* ── Navbar ── */}
            <nav className="sticky top-0 z-40 border-b border-gray-200/50 bg-white/80 backdrop-blur-xl">
                <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
                    <button onClick={() => navigate('/admin')}
                        className="group flex items-center gap-2 rounded-xl bg-gray-50 px-4 py-2 text-sm font-bold text-gray-600 transition-all hover:bg-gray-100 hover:text-indigo-600">
                        <svg className="h-4 w-4 transition-transform group-hover:-translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7" />
                        </svg>
                        Exit to Dashboard
                    </button>
                    <div className="flex items-center gap-3">
                        <span className="hidden sm:inline-block h-2 w-2 animate-pulse rounded-full bg-emerald-500" />
                        <span className="text-[13px] font-black uppercase tracking-widest text-gray-400">Live Insights</span>
                    </div>
                </div>
            </nav>

            <main className="mx-auto max-w-7xl px-6 pt-10 space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
                {/* Header Section */}
                <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                    <div className="space-y-1">
                        <h1 className="text-4xl font-black tracking-tight text-gray-900">Analytics</h1>
                        <p className="text-[15px] font-medium text-gray-400">Performance overview and workshop metrics.</p>
                    </div>
                    <div className="flex items-center gap-2 rounded-2xl bg-indigo-50 p-1.5">
                        <button className="rounded-xl bg-white px-4 py-2 text-sm font-bold text-indigo-600 shadow-sm">All Time</button>
                        <button className="rounded-xl px-4 py-2 text-sm font-bold text-gray-500 hover:text-gray-700">Recent</button>
                    </div>
                </div>

                {/* KPI Row */}
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                    <MetricCard label="Workshops" value={totalWorkshops ?? '0'} sub="All statuses" color="#6366f1" icon={
                        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21" />
                        </svg>
                    } />
                    <MetricCard label="Registrations" value={totalRegistrations?.toLocaleString() ?? '0'} sub="Status = SUCCESS" color="#10b981" icon={
                        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
                        </svg>
                    } />
                    <MetricCard label="Total Revenue" value={fmtMoney(totalRevenue)} sub="Completed payments" color="#f59e0b" icon={
                        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    } />
                    <MetricCard label="Participation Rate" value={`${round1(actualParticipationRate)}%`} sub="Completed workshops" color="#8b5cf6" icon={
                        <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
                        </svg>
                    } />
                </div>

                <div className="grid gap-6 lg:grid-cols-2">
                    {/* Peak Hours Area Chart */}
                    <ChartCard title="Registration Activity" subtitle="Hourly traffic patterns" className="lg:col-span-2">
                        <div className="h-[280px] w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={hourlyData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                                    <defs>
                                        <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="#6366f1" stopOpacity={0.2} />
                                            <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid vertical={false} {...GR} />
                                    <XAxis dataKey="hour" axisLine={false} tickLine={false} tick={AX} minTickGap={30} />
                                    <YAxis axisLine={false} tickLine={false} tick={AX} />
                                    <Tooltip content={<ChartTooltip />} />
                                    <Area type="monotone" dataKey="Count" stroke="#6366f1" strokeWidth={3} fillOpacity={1} fill="url(#colorCount)" />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </ChartCard>

                    {/* Donut Charts */}
                    <ChartCard title="Payment Flow" subtitle="Conversion of workshop bookings">
                        <DonutWithLegend data={payPie} colors={PAY_CLR} />
                    </ChartCard>

                    <ChartCard title="Engagement Ratio" subtitle="Attendance vs. Cancellations">
                        <DonutWithLegend data={attPie} colors={ATT_CLR} />
                    </ChartCard>

                    {/* Top Workshops Bar Chart */}
                    <ChartCard title="Top 10 High Occupancy" subtitle="Workshops by fill rate %">
                        <div className="h-[350px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={top10} layout="vertical" margin={{ top: 0, right: 30, left: 0, bottom: 0 }}>
                                    <CartesianGrid horizontal={false} {...GR} />
                                    <XAxis type="number" domain={[0, 100]} hide />
                                    <YAxis type="category" dataKey="name" width={140} tick={AX} axisLine={false} tickLine={false} />
                                    <Tooltip content={<ChartTooltip fmt={v => `${v}%`} />} cursor={{ fill: '#f8fafc' }} />
                                    <Bar dataKey="fillRate" radius={[0, 8, 8, 0]} maxBarSize={24}>
                                        {top10.map((e, i) => <Cell key={i} fill={e.color} fillOpacity={0.8} />)}
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </ChartCard>

                    {/* Room Stats Table */}
                    <ChartCard title="Venue Optimization" subtitle="Utilization by room capacity">
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead>
                                    <tr className="text-[11px] font-bold uppercase tracking-widest text-gray-400">
                                        <th className="pb-4 text-left">Room Name</th>
                                        <th className="pb-4 text-center">Events</th>
                                        <th className="pb-4 text-right">Avg Fill</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-50">
                                    {roomUtilization.map((r, i) => (
                                        <tr key={i} className="group hover:bg-gray-50 transition-colors">
                                            <td className="py-4">
                                                <div className="flex items-center gap-3">
                                                    <div className="h-8 w-8 rounded-lg flex items-center justify-center bg-gray-100 text-xs font-bold text-gray-500 group-hover:bg-indigo-100 group-hover:text-indigo-600 transition-colors">
                                                        {r.roomName.charAt(0)}
                                                    </div>
                                                    <span className="text-[14px] font-bold text-gray-700">{r.roomName}</span>
                                                </div>
                                            </td>
                                            <td className="py-4 text-center text-sm font-medium text-gray-500">{r.workshopCount}</td>
                                            <td className="py-4 text-right">
                                                <span className="inline-flex rounded-lg px-2.5 py-1 text-xs font-black"
                                                    style={{ backgroundColor: `${r.avgFillRate >= 80 ? '#ef4444' : '#10b981'}15`, color: r.avgFillRate >= 80 ? '#ef4444' : '#10b981' }}>
                                                    {round1(r.avgFillRate)}%
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </ChartCard>
                </div>

                <footer className="pt-12 text-center">
                    <div className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 border border-gray-100 shadow-sm">
                        <span className="h-2 w-2 rounded-full bg-indigo-500" />
                        <span className="text-[11px] font-bold uppercase tracking-tighter text-gray-400">
                            UniHub Analytics Engine v3.0 • Real-time Data
                        </span>
                    </div>
                </footer>
            </main>
        </div>
    );
};

export default StatisticsPage;