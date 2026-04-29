import React from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const Layout = () => {
    const { user, isAuthenticated, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-gray-50">
            <nav className="bg-white border-b border-gray-200">
                <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
                    <Link to="/" className="text-xl font-bold text-indigo-600">
                        UniHub
                    </Link>

                    <div className="flex items-center gap-4">
                        {isAuthenticated ? (
                            <>
                                <span className="text-sm text-gray-600">
                                    {user.role} | ID: {user.id}
                                </span>
                                <button
                                    onClick={handleLogout}
                                    className="px-4 py-1.5 text-sm text-red-600 hover:text-red-700 
                                               border border-red-300 hover:border-red-400 rounded-md 
                                               transition-colors"
                                >
                                    Logout
                                </button>
                            </>
                        ) : (
                            <>
                                <Link
                                    to="/login"
                                    className="text-sm text-gray-600 hover:text-gray-900 transition-colors"
                                >
                                    Sign in
                                </Link>
                                <Link
                                    to="/register"
                                    className="px-4 py-1.5 text-sm bg-indigo-600 hover:bg-indigo-700 
                                               text-white rounded-md transition-colors"
                                >
                                    Register
                                </Link>
                            </>
                        )}
                    </div>
                </div>
            </nav>

            <main>
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;
