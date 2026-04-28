import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        // Check for stored authentication data on application load
        const storedToken = localStorage.getItem('access_token');
        const storedRole = localStorage.getItem('role');
        const storedUserId = localStorage.getItem('user_id');

        if (storedToken && storedRole) {
            setUser({ id: storedUserId, role: storedRole });
        }
        setIsLoading(false);
    }, []);

    const login = (authData) => {
        const { accessToken, refreshToken, userId, role } = authData;
        
        localStorage.setItem('access_token', accessToken);
        localStorage.setItem('refresh_token', refreshToken);
        localStorage.setItem('role', role);
        localStorage.setItem('user_id', userId);

        setUser({ id: userId, role: role });
    };

    const logout = () => {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('role');
        localStorage.removeItem('user_id');
        
        setUser(null);
    };

    const value = {
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout
    };

    return (
        <AuthContext.Provider value={value}>
            {!isLoading && children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
};