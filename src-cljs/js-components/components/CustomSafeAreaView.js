// import {SafeAreaView} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

// A custom SafeAreaView that only applies safe area insets to right, bottom, and left edges.
export const RNPSafeAreaView = props => {
  return (
    <SafeAreaView {...props} edges={['right', 'bottom', 'left']} >
      {props.children}
    </SafeAreaView>
  );
}