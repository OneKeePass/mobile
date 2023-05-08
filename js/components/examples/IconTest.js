// Example to Use React Native Vector Icons
// https://aboutreact.com/react-native-vector-icons/

// Import React
import React from 'react';

// Import required component
import {SafeAreaView, StyleSheet, Text, View} from 'react-native';

// Import vector icons
import Icon from 'react-native-vector-icons/FontAwesome';

const TestRNVIApp = () => {
  return (
    <SafeAreaView style={{flex: 1}}>
      <View style={{flex: 1, padding: 16}}>
        <View style={styles.container}>
          <Text style={styles.heading}>
            Example to Use React Native Vector Icons
          </Text>
          <View style={styles.iconContainer}>
            <Text>
              <Icon name="rocket" size={30} color="#900" />
            </Text>
            {/* Icon Component */}
            <Icon name="rocket" size={30} color="#900" />
          </View>
          <View style={{marginTop: 16, marginBottom: 16}}>
            {/* Icon.Button Component */}
            <Icon.Button
              name="facebook"
              backgroundColor="#3b5998"
              onPress={() => alert('Login with Facebook')}>
              Login with Facebook
            </Icon.Button>
          </View>
        </View>
        <Text style={styles.footerTitle}>Vector Icons</Text>
        <Text style={styles.footerText}>www.aboutreact.com</Text>
      </View>
    </SafeAreaView>
  );
};
const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heading: {
    fontSize: 20,
    textAlign: 'center',
    marginBottom: 20,
  },
  iconContainer: {
    marginTop: 16,
    marginBottom: 16,
    justifyContent: 'center',
    alignItems: 'center',
    textAlign: 'center',
  },
  footerTitle: {
    fontSize: 18,
    textAlign: 'center',
    color: 'grey',
  },
  footerText: {
    fontSize: 16,
    textAlign: 'center',
    color: 'grey',
  },
});

export default TestRNVIApp;
