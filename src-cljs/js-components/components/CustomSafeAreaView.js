// import {SafeAreaView} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

// A custom SafeAreaView that defaults to the right, bottom, and left edges. Individual
// layouts can override `edges` when a child (for example a bottom bar) owns an inset.
export const RNPSafeAreaView = props => {
  const {edges = ['right', 'bottom', 'left'], children, ...safeAreaProps} = props;

  return (
    <SafeAreaView {...safeAreaProps} edges={edges}>
      {children}
    </SafeAreaView>
  );
}
