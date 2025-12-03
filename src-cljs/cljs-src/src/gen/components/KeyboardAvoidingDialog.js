import * as React from 'react';
import { Dialog, Modal } from 'react-native-paper';
import { Keyboard, Platform } from 'react-native';

// This is based on the discussion in https://github.com/callstack/react-native-paper/issues/2172
// Initially the custom dialog was used only for iOS (reagent component cust-dialog in rn_components.cljs )
// In Android using android:windowSoftInputMode="adjustResize", the rnp-dialog itself worked 

// However from Android 15 or higher with API level 35 and above, edge-to-edge enforcement was done
// This disables android:windowSoftInputMode="adjustResize"

// See https://github.com/react-native-community/discussions-and-proposals/discussions/827
// As per recommendation, added react-native-edge-to-edge package. Android's android:windowSoftInputMode="adjustResize" is no more effective
// Because of this now both iOS and Android needs to use this "cust-dialog" (rn_components.cljs)

// Alternate solutions, some suggested using "react-native-keyboard-controller" which almost similar to this custom dialog
// https://docs.expo.dev/guides/keyboard-handling/#animating-views-in-sync-with-keyboard-height

const KeyboardAvoidingDialog = props => {
  const [bottom, setBottom] = React.useState(0);
  React.useEffect(() => {
    // function onKeyboardChange(e) {
    //   console.log('onKeyboardChange ', e);
    //   if (e.endCoordinates.screenY < e.startCoordinates.screenY) {
    //     setBottom(e.endCoordinates.height / 2);
    //   }
    //   //else setBottom(0);
    // }

    // This works for both iOS and Android
    function onKeyboardChange(e) {
      // console.log('onKeyboardChange ', e);

      if (Platform.OS === 'ios') {
        if (e?.startCoordinates && e.endCoordinates.screenY < e.startCoordinates.screenY) {
          setBottom(e.endCoordinates.height / 2);
        }
        // else {
        //   setBottom(0);
        // }
      } else {
        if (e?.endCoordinates?.height) {
          setBottom(e.endCoordinates.height / 2);
        } else {
          setBottom(0);
        }
      }
    }
    function onHide(e) {
      // console.log(e);
      setBottom(0);
    }
    const subscriptions = [Keyboard.addListener('keyboardWillChangeFrame', onKeyboardChange), Keyboard.addListener('keyboardDidHide', onHide), Keyboard.addListener('keyboardDidShow', onKeyboardChange), Keyboard.addListener('keyboardWillHide', onHide)];
    return () => subscriptions.forEach(subscription => subscription.remove());
  }, []);
  return /*#__PURE__*/React.createElement(Dialog, {
    style: {
      bottom: bottom
    },
    visible: props.visible,
    onDismiss: props.onDismiss
  }, props.children);
};

// This is not yet used. May need to make changes similar to the one above to work in iOS and Android
export const KeyboardAvoidingModal = props => {
  const [bottom, setBottom] = React.useState(0);
  React.useEffect(() => {
    function onKeyboardChange(e) {
      //console.log('onKeyboardChange ', e);
      if (e.endCoordinates.screenY < e.startCoordinates.screenY) {
        setBottom(e.endCoordinates.height / 2);
      }
      //else setBottom(0);
    }
    function onHide(e) {
      //console.log(e);
      setBottom(0);
    }
    const subscriptions = [Keyboard.addListener('keyboardWillChangeFrame', onKeyboardChange), Keyboard.addListener('keyboardDidHide', onHide), Keyboard.addListener('keyboardDidShow', onKeyboardChange), Keyboard.addListener('keyboardWillHide', onHide)];
    return () => subscriptions.forEach(subscription => subscription.remove());
  }, []);
  return /*#__PURE__*/React.createElement(Modal, {
    style: {
      bottom: bottom
    },
    visible: props.visible,
    onDismiss: props.onDismiss,
    contentContainerStyle: props.contentContainerStyle
  }, props.children);
};
export default KeyboardAvoidingDialog;

// This is another way (old way?) of doing the things done with useEffect
// Leaving it here as an example
/*
export default class KeyboardAvoidingDialog extends React.Component {
  constructor(props) {
    super(props);
    this.subscriptions = [];
    this.state = {
      bottom: 0,
    };
  }

  componentDidMount() {
    if (Platform.OS === 'ios') {
      this.subscriptions = [
        Keyboard.addListener(
          'keyboardWillChangeFrame',
          this.onKeyboardChange.bind(this),
        ),
      ];
    } else {
      this.subscriptions = [
        Keyboard.addListener(
          'keyboardDidHide',
          this.onKeyboardChange.bind(this),
        ),
        Keyboard.addListener(
          'keyboardDidShow',
          this.onKeyboardChange.bind(this),
        ),
      ];
    }
  }

  componentWillUnmount() {
    this.subscriptions.forEach(subscription => {
      subscription.remove();
    });
  }

  onKeyboardChange(e) {
    if (e.endCoordinates.screenY < e.startCoordinates.screenY)
      this.setState({bottom: e.endCoordinates.height / 2});
    else this.setState({bottom: 0});
  }

  render() {
    return (
      <Dialog
        style={{bottom: this.state.bottom}}
        visible={this.props.visible}
        onDismiss={this.props.onDismiss}>
        {this.props.children}
      </Dialog>
    );
  }
}
*/