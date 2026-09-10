import React from 'react';
import {ScrollView, Text, View} from 'react-native';

// Without a boundary anywhere in the tree, a throw while rendering is passed on to the
// react native fatal handler and the process is killed straight away. The user loses
// whatever was not saved and nothing of the error reaches us, as the message is dropped
// from the crash reports that App Store Connect shows.
//
// This boundary keeps the app alive and puts the message on the screen so that a user
// who reports the problem can tell us what it said. 'onError' is passed from the cljs
// side and is where the error is recorded.
//
// The fallback deliberately uses plain react native views with hard coded colors. The
// paper provider and our own theme atoms are part of what may have just failed, so
// nothing from them is used here.

const styles = {
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    padding: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#1C1B1F',
    marginBottom: 12,
  },
  explain: {
    fontSize: 14,
    color: '#49454F',
    marginBottom: 20,
  },
  detailBox: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#CAC4D0',
    borderRadius: 8,
    padding: 12,
  },
  detail: {
    fontSize: 12,
    color: '#49454F',
  },
};

export class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {hasError: false, error: null};
  }

  static getDerivedStateFromError(error) {
    // JavaScript can throw falsy values such as null, false, 0, or "".
    // Track failure separately so these also show the fallback instead of retrying children.
    return {hasError: true, error: error};
  }

  componentDidCatch(error, errorInfo) {
    const {onError} = this.props;
    if (typeof onError === 'function') {
      try {
        onError(
          error && error.message ? error.message : String(error),
          error && error.stack ? error.stack : '',
          errorInfo && errorInfo.componentStack ? errorInfo.componentStack : '',
        );
      } catch (e) {
        // The reporting itself must never replace the error being reported
        console.error('AppErrorBoundary onError failed', e);
      }
    }
  }

  render() {
    const {hasError, error} = this.state;
    const {children, title, explain} = this.props;

    if (!hasError) {
      return children;
    }

    const message = error && error.message ? error.message : String(error);
    const stack = error && error.stack ? error.stack : '';

    return (
      <View style={styles.container}>
        <Text style={styles.title}>{title || 'Something went wrong'}</Text>
        <Text style={styles.explain}>
          {explain ||
            'Please close and reopen the app. Sending the details below to us will help in fixing this.'}
        </Text>
        <ScrollView style={styles.detailBox}>
          <Text selectable={true} style={styles.detail}>
            {message}
            {stack ? '\n\n' + stack : ''}
          </Text>
        </ScrollView>
      </View>
    );
  }
}
