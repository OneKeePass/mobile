import * as React from 'react';
import {View, TextInput} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {
  Appbar,
  Text,
  Surface,
  TextInput as RNPTextInput,
  Provider as PaperProvider,
} from 'react-native-paper';

export function CenterView({children}) {
  return <View style={styles.main}>{children}</View>;
}

export const AppbarExample = () => (
  <Appbar.Header>
    <Appbar.BackAction onPress={() => {}} />
    <Appbar.Content title="Title" />
    <Appbar.Action icon="calendar" onPress={() => {}} />
    <Appbar.Action icon="magnify" onPress={() => {}} />
  </Appbar.Header>
);

export const TextInputExample = () => {
  const [text, setText] = React.useState('');

  return (
    <PaperProvider>
      <CenterView>
        <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
          <RNPTextInput
            maxLength={150}
            style={{width: 200}}
            label="Email"
            mode="flat"
            value={text}
            onChangeText={text => setText(text)}
          />
        </View>
      </CenterView>
    </PaperProvider>
  );
};

export const SurfaceExample = () => {
  return (
    <CenterView>
      <Surface style={styles.surface} elevation={1}>
        <Text>Surface</Text>
      </Surface>
    </CenterView>
  );
};

const styles = {
  main: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    //backgroundColor: '#F5FCFF',
  },
  surface: {
    padding: 8,
    height: 80,
    width: 80,
    alignItems: 'center',
    justifyContent: 'center',
  },
};
