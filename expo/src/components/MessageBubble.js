import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useThemeContext } from '../context/ThemeContext';

export default function MessageBubble({ role, content }) {
  const theme = useThemeContext();
  const isUser = role === 'user';

  return (
    <View style={[styles.bubble, { alignSelf: isUser ? 'flex-end' : 'flex-start', backgroundColor: isUser ? theme.colors.primary : theme.colors.card }]}>
      <Text style={[styles.text, { color: isUser ? '#fff' : theme.colors.text }]}>
        {content}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    maxWidth: '80%',
    padding: 12,
    borderRadius: 16,
    marginVertical: 4,
    marginHorizontal: 8,
  },
  text: {
    fontSize: 15,
  },
});
