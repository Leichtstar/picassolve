import React, { createContext, useContext, useState, useCallback } from 'react';

const AlertContext = createContext(null);

export const AlertProvider = ({ children }) => {
    const [alert, setAlert] = useState(null);

    const showAlert = useCallback((message, type = 'success', duration = 3000) => {
        setAlert({ message, type });

        // Auto-hide after duration
        setTimeout(() => {
            setAlert(null);
        }, duration);
    }, []);

    const hideAlert = useCallback(() => {
        setAlert(null);
    }, []);

    // Convenience methods
    const success = useCallback((message, duration) => showAlert(message, 'success', duration), [showAlert]);
    const error = useCallback((message, duration) => showAlert(message, 'error', duration), [showAlert]);

    return (
        <AlertContext.Provider value={{ alert, showAlert, hideAlert, success, error }}>
            {children}
        </AlertContext.Provider>
    );
};

export const useAlert = () => useContext(AlertContext);
