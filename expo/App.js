import React from 'react';
import { ActivityIndicator, TouchableOpacity, Text, View, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { AuthProvider, useAuthContext } from './src/context/AuthContext';
import { ThemeProvider } from './src/context/ThemeContext';
import LoginScreen from './src/screens/LoginScreen';
import ClientChatScreen from './src/screens/ClientChatScreen';
import KitchenScreen from './src/screens/KitchenScreen';
import WaiterScreen from './src/screens/WaiterScreen';
import AdminScreen from './src/screens/AdminScreen';

const Stack = createStackNavigator();

const theme = {
  dark: true,
  colors: {
    primary: '#f59e0b',
    background: '#0f0f1a',
    card: '#1a1a2e',
    text: '#e2e8f0',
    border: '#2d2d44',
    notification: '#f59e0b',
  },
  fonts: {
    regular: { fontFamily: 'System', fontWeight: 'normal' },
    medium: { fontFamily: 'System', fontWeight: '500' },
    bold: { fontFamily: 'System', fontWeight: '700' },
    heavy: { fontFamily: 'System', fontWeight: '900' },
  },
};

function HeaderLogoutButton({ navigation }) {
  const { logout } = useAuthContext();

  const handleLogout = async () => {
    await logout();
    navigation.replace('Login');
  };

  return (
    <TouchableOpacity onPress={handleLogout} style={{ marginLeft: 16 }}>
      <Ionicons name="log-out-outline" size={26} color="#f59e0b" />
    </TouchableOpacity>
  );
}

function ProtectedScreen({ component: Component, allowedRoles, navigation, route }) {
  const { user, token, isBootstrapping } = useAuthContext();
  const allowed = !!token && allowedRoles.includes(user?.role);

  React.useEffect(() => {
    if (!isBootstrapping && !allowed) {
      navigation.replace('Login');
    }
  }, [allowed, isBootstrapping, navigation]);

  if (isBootstrapping) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#0f0f1a' }}>
        <ActivityIndicator color="#f59e0b" />
      </View>
    );
  }

  if (!allowed) return null;

  return <Component navigation={navigation} route={route} />;
}

function protectedScreen(component, allowedRoles) {
  return (props) => <ProtectedScreen {...props} component={component} allowedRoles={allowedRoles} />;
}

function AppNavigator() {
  const insets = useSafeAreaInsets();
  const bottomPad = Math.max(insets.bottom, Platform.OS === 'android' ? 12 : 0);

  return (
    <View style={{ flex: 1, backgroundColor: '#0f0f1a', paddingBottom: bottomPad }}>
    <NavigationContainer theme={theme}>
      <StatusBar style="light" />
      <Stack.Navigator
        initialRouteName="Login"
        screenOptions={{
          headerTintColor: '#f59e0b',
          cardStyle: { backgroundColor: '#0f0f1a' },
        }}
      >
        <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
        <Stack.Screen name="ClientChat" component={ClientChatScreen}
          options={({ route, navigation }) => ({
            headerTitleAlign: 'center',
            headerTitle: () => (
              <Text style={{ color: '#f59e0b', fontSize: 18, fontWeight: '700' }}>Mesa {route.params?.tableId || ''}</Text>
            ),
            headerLeft: () => <HeaderLogoutButton navigation={navigation} />,
          })} />
        <Stack.Screen name="Kitchen" component={protectedScreen(KitchenScreen, ['KITCHEN', 'ADMIN'])}
          options={({ navigation }) => ({
            headerTitleAlign: 'center',
            headerTitle: () => (
              <Text style={{ color: '#f59e0b', fontSize: 18, fontWeight: '700' }}>Módulo Cocina</Text>
            ),
            headerLeft: () => <HeaderLogoutButton navigation={navigation} />,
          })} />
        <Stack.Screen name="Waiter" component={protectedScreen(WaiterScreen, ['WAITER', 'ADMIN'])}
          options={({ navigation }) => ({
            headerTitleAlign: 'center',
            headerTitle: () => (
              <Text style={{ color: '#f59e0b', fontSize: 18, fontWeight: '700' }}>Módulo Mesero</Text>
            ),
            headerLeft: () => <HeaderLogoutButton navigation={navigation} />,
          })} />
        <Stack.Screen name="Admin" component={protectedScreen(AdminScreen, ['ADMIN'])}
          options={({ navigation }) => ({
            headerTitleAlign: 'center',
            headerTitle: () => (
              <Text style={{ color: '#f59e0b', fontSize: 18, fontWeight: '700' }}>Panel Admin</Text>
            ),
            headerLeft: () => <HeaderLogoutButton navigation={navigation} />,
          })} />
      </Stack.Navigator>
    </NavigationContainer>
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <AuthProvider>
          <AppNavigator />
        </AuthProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
