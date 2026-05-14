import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import workshopService from '../services/workshopService';
import HomeStudentHeader from '../components/homeStudent/HomeStudentHeader';
import UserInfoCard from '../components/homeStudent/UserInfoCard';
import UpcomingWorkshops from '../components/homeStudent/UpcomingWorkshops';
import LoadingState from '../components/homeStudent/LoadingState';
import ErrorBanner from '../components/homeStudent/ErrorBanner';

const HomeStudent = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    
    const [upcomingWorkshops, setUpcomingWorkshops] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');

            const data = await workshopService.getAll(0, 3);
            setUpcomingWorkshops(data.content || []);
        } catch (err) {
            setError('Failed to load workshops. Please try again.');
            console.error('Error fetching data:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const handleBrowseWorkshops = () => {
        navigate('/workshops');
    };

    const handleViewProfile = () => {
        navigate('/profile');
    };

    if (loading) {
        return <LoadingState />;
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            <HomeStudentHeader
                userName={user?.fullName}
                onViewRegistrations={() => navigate('/registration-history')}
            />

            <ErrorBanner message={error} />

            <section className="md:col-span-1 lg:col-span-2">
                <UserInfoCard user={user} onViewProfile={handleViewProfile} />
            </section>

            <section className="mt-4 md:col-span-1 lg:col-span-2">
                <UpcomingWorkshops
                    upcomingWorkshops={upcomingWorkshops}
                    onWorkshopClick={handleBrowseWorkshops}
                />
            </section>
        </div>
    );
};

export default HomeStudent;
