import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const GuestRoute = () => {
    const { isAuthenticated, user } = useAuth();

    if (isAuthenticated) {
        // Redirect authenticated users to their specific dashboard
        const redirectPath = user?.role === 'ADMIN' ? '/admin' : '/student';
        return <Navigate to={redirectPath} replace />;
    }

    // Render the child routes (Login, Register) if not authenticated
    return <Outlet />;
};

export default GuestRoute;