import React, { useEffect, useState } from 'react';
import { useAlert } from '../context/AlertContext';
import './GlobalAlert.css';

export default function GlobalAlert() {
    const { alert, hideAlert } = useAlert();
    const [visible, setVisible] = useState(false);

    useEffect(() => {
        if (alert) {
            // Trigger slide-in animation
            setVisible(true);
        } else {
            setVisible(false);
        }
    }, [alert]);

    if (!alert) return null;

    return (
        <div
            className={`global-alert global-alert--${alert.type} ${visible ? 'global-alert--visible' : ''}`}
            onClick={hideAlert}
        >
            <span className="global-alert__icon">
                {alert.type === 'success' ? '✓' : '✕'}
            </span>
            <span className="global-alert__message">{alert.message}</span>
        </div>
    );
}
