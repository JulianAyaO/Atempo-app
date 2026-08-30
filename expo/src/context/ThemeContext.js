import React, { createContext, useContext } from 'react';

export const Theme = {
  dark: true,
  colors: {
    primary: '#f59e0b',
    background: '#1a1a2e',
    card: '#16213e',
    text: '#FFFFFF',
    textSecondary: '#B0B0B0',
    border: '#0f3460',
    notification: '#e94560',
    success: '#2ecc71',
    warning: '#f1c40f',
  },
};

const ThemeContext = createContext(Theme);

export function ThemeProvider({ children }) {
  return (
    <ThemeContext.Provider value={Theme}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useThemeContext() {
  return useContext(ThemeContext);
}
