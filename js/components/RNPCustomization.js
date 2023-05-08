import * as React from 'react';
import {StyleSheet} from 'react-native';
import {MD3LightTheme, Menu, TextInput} from 'react-native-paper';

export const theme1 = {
  ...MD3LightTheme, // or MD3DarkTheme
  roundness: 2,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#3498db',
    secondary: '#f1c40f',
    tertiary: '#a1b2c3',
  },
};

// This is the same as mui
export const theme2 = {
  ...MD3LightTheme, // or MD3DarkTheme
  roundness: 2,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#1976d2',
    secondary: '#9c27b0',
  },
};
//#9c27b0
export const theme3 = {
  ...MD3LightTheme, // or MD3DarkTheme
  roundness: 2,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#3141AD',
    secondary: '#9c27b0',
  },
};

const custColors = {
  colors: {
    primary: 'rgb(68, 83, 191)',
    onPrimary: 'rgb(255, 255, 255)',
    primaryContainer: 'rgb(223, 224, 255)',
    onPrimaryContainer: 'rgb(0, 13, 96)',
    secondary: 'rgb(91, 93, 114)',
    onSecondary: 'rgb(255, 255, 255)',
    secondaryContainer: 'rgb(224, 225, 249)',
    onSecondaryContainer: 'rgb(24, 26, 44)',
    tertiary: 'rgb(119, 83, 108)',
    onTertiary: 'rgb(255, 255, 255)',
    tertiaryContainer: 'rgb(255, 215, 240)',
    onTertiaryContainer: 'rgb(45, 17, 39)',
    error: 'rgb(186, 26, 26)',
    onError: 'rgb(255, 255, 255)',
    errorContainer: 'rgb(255, 218, 214)',
    onErrorContainer: 'rgb(65, 0, 2)',
    background: 'rgb(255, 251, 255)',
    onBackground: 'rgb(27, 27, 31)',
    surface: 'rgb(255, 251, 255)',
    onSurface: 'rgb(27, 27, 31)',
    surfaceVariant: 'rgb(227, 225, 236)',
    onSurfaceVariant: 'rgb(70, 70, 79)',
    outline: 'rgb(119, 118, 128)',
    outlineVariant: 'rgb(199, 197, 208)',
    shadow: 'rgb(0, 0, 0)',
    scrim: 'rgb(0, 0, 0)',
    inverseSurface: 'rgb(48, 48, 52)',
    inverseOnSurface: 'rgb(243, 240, 244)',
    inversePrimary: 'rgb(188, 195, 255)',
    elevation: {
      level0: 'transparent',
      level1: 'rgb(246, 243, 252)',
      level2: 'rgb(240, 238, 250)',
      level3: 'rgb(234, 233, 248)',
      level4: 'rgb(233, 231, 247)',
      level5: 'rgb(229, 228, 246)',
    },
    surfaceDisabled: 'rgba(27, 27, 31, 0.12)',
    onSurfaceDisabled: 'rgba(27, 27, 31, 0.38)',
    backdrop: 'rgba(47, 48, 56, 0.4)',
  },
};

// See Creating dynamic theme colors
// https://callstack.github.io/react-native-paper/theming.html
export const theme4 = {
  ...MD3LightTheme,
  // Copied it from the color codes scheme and then used it here. The color used to generate this is #3141AD
  colors: custColors.colors,
};

export const RNPMenu = props => {
  // Following is another way of overriding props
  //   return (
  //     <Menu contentStyle={{backgroundColor: theme4.colors.onPrimary}} {...props}>
  //       {props.children}
  //     </Menu>
  //   );
  return (
    <Menu {...props} contentStyle={[styles.contentStyle, props.style]}>
      {props.children}
    </Menu>
  );
};

// export const RNPTextInput = props => {
//   return (
//     <TextInput {...props} style={[styles.textInput, props.style]}>
//       {props.children}
//     </TextInput>
//   );
// };

// Now our custom textinput can accept ref prop if used
export const RNPTextInput = React.forwardRef((props, ref) => {
  return (
    <TextInput {...props} ref={ref} style={[styles.textInput, props.style]}>
      {props.children}
    </TextInput>
  );
});

const styles = StyleSheet.create({
  contentStyle: {
    backgroundColor: theme4.colors.onPrimary,
  },
  textInput: {
    backgroundColor: 'white',
  },
});
