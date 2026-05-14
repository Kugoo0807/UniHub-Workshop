import React from 'react';

const UpcomingWorkshops = ({ upcomingWorkshops, onWorkshopClick }) => {
    return (
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
                            onClick={onWorkshopClick}
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
    );
};

export default UpcomingWorkshops;
