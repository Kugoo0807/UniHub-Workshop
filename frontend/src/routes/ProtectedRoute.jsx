import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Spinner from '../components/common/Spinner';

/**
 * A wrapper component to protect routes based on authentication and RBAC.
 * @param {Array<string>} allowedRoles - Array of roles allowed to access the route (e.g., ['ADMIN', 'STUDENT']).
 */
const ProtectedRoute = ({ allowedRoles }) => {
    const { user, isAuthenticated, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) {
        return <Spinner isFullScreen={true} label="Checking authentication..." />;
    }

    if (!isAuthenticated) {
        // Redirect to login, but save the location they were trying to access
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    if (allowedRoles && !allowedRoles.includes(user.role)) {
        // User is logged in but does not have the required role
        return <Navigate to="/unauthorized" replace />;
    }

    // Render the child routes if all checks pass
    return <Outlet />;
};

export default ProtectedRoute;