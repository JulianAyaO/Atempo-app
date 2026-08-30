import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export default function ConnectionIndicator({ connected, style }) {
  return (
    <View style={[styles.container, style]}>
      <View style={[styles.dot, { backgroundColor: connected ? '#22c55e' : '#ef4444' }]} />
      <Text style={styles.text}>{connected ? 'En línea' : 'Reconectando'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  text: {
    color: '#94a3b8',
    fontSize: 12,
    fontWeight: '600',
  },
});
