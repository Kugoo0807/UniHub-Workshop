import React, { useState } from 'react';
import { Link, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const Layout = () => {
    const { user, isAuthenticated, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const isActive = (path) => location.pathname === path;

    const navLinks = isAuthenticated ? (
        user?.role === 'ADMIN' 
            ? [{ path: '/', label: '🏠 Home' }]
            : [
                { path: '/', label: '🏠 Home' },
                { path: '/workshops', label: '📚 Workshops' },
                { path: '/profile', label: '👤 Profile' },
            ]
    ) : [];

    return (
        <div className="min-h-screen bg-gray-50">
            <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">
                <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
                    <Link to="/" className="text-xl sm:text-2xl font-bold text-indigo-600 flex-shrink-0">
                        🎓 UniHub
                    </Link>

                    {/* Desktop Navigation */}
                    <div className="hidden sm:flex items-center gap-6">
                        {isAuthenticated ? (
                            <>
                                {navLinks.map(link => (
                                    <Link
                                        key={link.path}
                                        to={link.path}
                                        className={`text-sm font-medium px-3 py-2 rounded-md transition-colors ${isActive(link.path)
                                                ? 'bg-indigo-100 text-indigo-700'
                                                : 'text-gray-600 hover:text-indigo-600'
                                            }`}
                                    >
                                        {link.label}
                                    </Link>
                                ))}
                                <div className="flex items-center gap-3 pl-4 border-l border-gray-200">
                                    <div className="text-right">
                                        <p className="text-xs font-semibold text-gray-700">{user?.fullName}</p>
                                        <p className="text-xs text-gray-500">{user?.role}</p>
                                    </div>
                                    <button
                                        onClick={handleLogout}
                                        className="px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 
                                                   border border-red-300 rounded-md transition-colors"
                                    >
                                        Logout
                                    </button>
                                </div>
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

                    {/* Mobile Menu Button */}
                    <button
                        onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                        className="sm:hidden p-2 rounded-md hover:bg-gray-100"
                    >
                        {isMobileMenuOpen ? '✕' : '☰'}
                    </button>
                </div>

                {/* Mobile Navigation */}
                {isMobileMenuOpen && (
                    <div className="sm:hidden border-t border-gray-200 bg-white">
                        <div className="px-4 py-3 space-y-2">
                            {isAuthenticated ? (
                                <>
                                    <div className="mb-3 pb-3 border-b border-gray-200">
                                        <p className="text-sm font-semibold text-gray-900">{user?.fullName}</p>
                                        <p className="text-xs text-gray-500">{user?.email}</p>
                                        <p className="text-xs text-gray-400 mt-1">{user?.role}</p>
                                    </div>
                                    {navLinks.map(link => (
                                        <Link
                                            key={link.path}
                                            to={link.path}
                                            onClick={() => setIsMobileMenuOpen(false)}
                                            className={`block px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive(link.path)
                                                    ? 'bg-indigo-100 text-indigo-700'
                                                    : 'text-gray-600 hover:bg-gray-100'
                                                }`}
                                        >
                                            {link.label}
                                        </Link>
                                    ))}
                                    <button
                                        onClick={() => {
                                            handleLogout();
                                            setIsMobileMenuOpen(false);
                                        }}
                                        className="w-full mt-3 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50 
                                                   border border-red-300 rounded-md transition-colors"
                                    >
                                        Logout
                                    </button>
                                </>
                            ) : (
                                <>
                                    <Link
                                        to="/login"
                                        className="block px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-md"
                                        onClick={() => setIsMobileMenuOpen(false)}
                                    >
                                        Sign in
                                    </Link>
                                    <Link
                                        to="/register"
                                        className="block px-3 py-2 text-sm bg-indigo-600 text-white rounded-md text-center"
                                        onClick={() => setIsMobileMenuOpen(false)}
                                    >
                                        Register
                                    </Link>
                                </>
                            )}
                        </div>
                    </div>
                )}
            </nav>

            <main>
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;
