import React, { useEffect, useState } from 'react';
import {
  View, Text, TouchableOpacity, TextInput, StyleSheet, Alert, ActivityIndicator,
  KeyboardAvoidingView, Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../hooks/useAuth';
import { STAFF_ROLE_SCREEN, TABLE_NUMBERS, DEMO_STAFF_ACCOUNTS } from '../constants/appConstants';

const TABLES = TABLE_NUMBERS;

export default function LoginScreen({ navigation }) {
  const { user, isAuthenticated, isBootstrapping, signIn, signOut, createTableSession } = useAuth();
  const [mode, setMode] = useState(null);
  const [selectedTable, setSelectedTable] = useState(null);
  const [staffName, setStaffName] = useState('');
  const [pin, setPin] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (mode === 'client') return;
    if (!isBootstrapping && isAuthenticated && user?.role) {
      navigation.replace(STAFF_ROLE_SCREEN[user.role] || 'Waiter');
    }
  }, [isAuthenticated, isBootstrapping, navigation, user, mode]);

  const handleChooseClient = async () => {
    if (isAuthenticated) {
      await signOut();
    }
    setMode('client');
  };

  const handleClientAccess = async () => {
    if (!selectedTable) return Alert.alert('Selecciona una mesa');
    setLoading(true);
    try {
      const session = await createTableSession(selectedTable);
      if (!session?.sessionId) {
        throw new Error('El servidor no devolvió una sesión válida.');
      }
      navigation.replace('ClientChat', {
        tableId: selectedTable,
        sessionId: session.sessionId,
      });
    } catch (e) {
      Alert.alert('Error', 'No se pudo conectar al servidor. Verifica la conexión.\n' + e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleStaffLogin = async (name = staffName, pinValue = pin) => {
    if (!name || !pinValue) return Alert.alert('Completa todos los campos');
    setLoading(true);
    try {
      const result = await signIn(name, pinValue);
      const screen = STAFF_ROLE_SCREEN[result.role] || 'Waiter';
      navigation.replace(screen);
    } catch (e) {
      Alert.alert('Error', 'Credenciales inválidas');
    } finally {
      setLoading(false);
    }
  };

  if (!mode) {
    return (
      <View style={s.container}>
        <View style={s.heroSection}>
          <Text style={s.logo}>🍽️</Text>
          <Text style={s.title}>RestauranteChat</Text>
          <Text style={s.subtitle}>Sistema de pedidos inteligente</Text>
        </View>
        <View style={s.buttonGroup}>
          <TouchableOpacity style={[s.roleBtn, s.clientBtn]} onPress={handleChooseClient}>
            <Ionicons name="chatbubbles" size={32} color="#fff" />
            <Text style={s.roleBtnText}>Soy Cliente</Text>
            <Text style={s.roleBtnSub}>Hacer pedido desde mi mesa</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[s.roleBtn, s.staffBtn]} onPress={() => setMode('staff')}>
            <Ionicons name="people" size={32} color="#fff" />
            <Text style={s.roleBtnText}>Soy Personal</Text>
            <Text style={s.roleBtnSub}>Cocina, Mesero o Admin</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  if (mode === 'client') {
    return (
      <View style={s.container}>
        <TouchableOpacity style={s.backBtn} onPress={() => setMode(null)}>
          <Ionicons name="arrow-back" size={24} color="#f59e0b" />
          <Text style={s.backText}>Volver</Text>
        </TouchableOpacity>
        <Text style={s.sectionTitle}>Selecciona tu mesa</Text>
        <View style={s.tableGrid}>
          {TABLES.map(t => (
            <TouchableOpacity key={t} style={[s.tableBtn, selectedTable === t && s.tableSelected]}
              onPress={() => setSelectedTable(t)}>
              <Ionicons name="restaurant" size={24} color={selectedTable === t ? '#0f0f1a' : '#f59e0b'} />
              <Text style={[s.tableNum, selectedTable === t && s.tableNumSel]}>Mesa {t}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <TouchableOpacity style={[s.continueBtn, !selectedTable && s.disabledBtn]}
          onPress={handleClientAccess} disabled={loading || !selectedTable}>
          {loading ? <ActivityIndicator color="#0f0f1a" /> :
            <Text style={s.continueBtnText}>Iniciar Chat →</Text>}
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView style={s.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <TouchableOpacity style={s.backBtn} onPress={() => setMode(null)}>
        <Ionicons name="arrow-back" size={24} color="#f59e0b" />
        <Text style={s.backText}>Volver</Text>
      </TouchableOpacity>
      <Text style={s.sectionTitle}>Acceso Personal</Text>
      <View style={s.form}>
        <TextInput style={s.input} placeholder="Nombre" placeholderTextColor="#666"
          value={staffName} onChangeText={setStaffName} />
        <TextInput style={s.input} placeholder="PIN" placeholderTextColor="#666"
          value={pin} onChangeText={setPin} keyboardType="numeric" secureTextEntry />
        <TouchableOpacity style={s.continueBtn} onPress={() => handleStaffLogin()} disabled={loading}>
          {loading ? <ActivityIndicator color="#0f0f1a" /> :
            <Text style={s.continueBtnText}>Iniciar Sesión</Text>}
        </TouchableOpacity>
        <Text style={s.demoTitle}>Cuentas demostrativas</Text>
        {DEMO_STAFF_ACCOUNTS.map((account) => (
          <TouchableOpacity
            key={account.name}
            style={s.demoBtn}
            onPress={() => handleStaffLogin(account.name, account.pin)}
            disabled={loading}
          >
            <Ionicons name={account.icon} size={22} color="#f59e0b" />
            <View style={s.demoBtnTextWrap}>
              <Text style={s.demoBtnTitle}>{account.label}</Text>
              <Text style={s.demoBtnSub}>{account.subtitle}</Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color="#64748b" />
          </TouchableOpacity>
        ))}
      </View>
    </KeyboardAvoidingView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f1a', paddingHorizontal: 24, justifyContent: 'center' },
  heroSection: { alignItems: 'center', marginBottom: 48 },
  logo: { fontSize: 64, marginBottom: 12 },
  title: { fontSize: 32, fontWeight: '800', color: '#f59e0b', letterSpacing: 1 },
  subtitle: { fontSize: 16, color: '#94a3b8', marginTop: 8 },
  buttonGroup: { gap: 16 },
  roleBtn: { borderRadius: 16, padding: 24, alignItems: 'center', gap: 8 },
  clientBtn: { backgroundColor: '#1e3a5f' },
  staffBtn: { backgroundColor: '#3d1f56' },
  roleBtnText: { fontSize: 20, fontWeight: '700', color: '#fff' },
  roleBtnSub: { fontSize: 14, color: '#cbd5e1' },
  backBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, position: 'absolute', top: 60, left: 24, zIndex: 10 },
  backText: { color: '#f59e0b', fontSize: 16, fontWeight: '600' },
  sectionTitle: { fontSize: 24, fontWeight: '700', color: '#e2e8f0', textAlign: 'center', marginBottom: 24 },
  tableGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 12 },
  tableBtn: { width: '28%', aspectRatio: 1, borderRadius: 16, borderWidth: 2, borderColor: '#2d2d44',
    alignItems: 'center', justifyContent: 'center', backgroundColor: '#1a1a2e' },
  tableSelected: { borderColor: '#f59e0b', backgroundColor: '#f59e0b' },
  tableNum: { fontSize: 13, fontWeight: '600', color: '#94a3b8', marginTop: 4 },
  tableNumSel: { color: '#0f0f1a' },
  continueBtn: { backgroundColor: '#f59e0b', borderRadius: 14, padding: 18, alignItems: 'center', marginTop: 16 },
  continueBtnText: { fontSize: 18, fontWeight: '700', color: '#0f0f1a' },
  disabledBtn: { opacity: 0.5 },
  form: { gap: 16 },
  input: { backgroundColor: '#1a1a2e', borderRadius: 12, padding: 16, fontSize: 16, color: '#e2e8f0',
    borderWidth: 1, borderColor: '#2d2d44' },
  demoTitle: { textAlign: 'center', color: '#94a3b8', fontSize: 13, fontWeight: '600', marginTop: 8 },
  demoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: '#1a1a2e',
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: '#2d2d44',
  },
  demoBtnTextWrap: { flex: 1 },
  demoBtnTitle: { color: '#e2e8f0', fontSize: 16, fontWeight: '700' },
  demoBtnSub: { color: '#94a3b8', fontSize: 12, marginTop: 2 },
});
