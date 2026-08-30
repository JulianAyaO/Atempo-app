import React from 'react';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { useThemeContext } from '../context/ThemeContext';

export default function LoadingSpinner() {
  const theme = useThemeContext();
  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={theme.colors.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
