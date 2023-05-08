import * as React from 'react';
import {Dialog, Modal} from 'react-native-paper';
import {Keyboard, Platform} from 'react-native';

const KeyboardAvoidingDialog = props => {
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

    // if (Platform.OS === 'ios') {
    //   const subscription = Keyboard.addListener(
    //     'keyboardWillChangeFrame',
    //     onKeyboardChange,
    //   );
    //   return () => subscription.remove();
    // }

    const subscriptions = [
      Keyboard.addListener('keyboardWillChangeFrame', onKeyboardChange),
      Keyboard.addListener('keyboardDidHide', onHide),
      Keyboard.addListener('keyboardDidShow', onKeyboardChange),
      Keyboard.addListener('keyboardWillHide', onHide),
    ];
    return () => subscriptions.forEach(subscription => subscription.remove());
  }, []);

  return (
    <Dialog
      style={{bottom: bottom}}
      visible={props.visible}
      onDismiss={props.onDismiss}>
      {props.children}
    </Dialog>
  );
};

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

    const subscriptions = [
      Keyboard.addListener('keyboardWillChangeFrame', onKeyboardChange),
      Keyboard.addListener('keyboardDidHide', onHide),
      Keyboard.addListener('keyboardDidShow', onKeyboardChange),
      Keyboard.addListener('keyboardWillHide', onHide),
    ];
    return () => subscriptions.forEach(subscription => subscription.remove());
  }, []);

  return (
    <Modal
      style={{bottom: bottom}}
      visible={props.visible}
      onDismiss={props.onDismiss}
      contentContainerStyle={props.contentContainerStyle}>
      {props.children}
    </Modal>
  );
};

export default KeyboardAvoidingDialog;

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
