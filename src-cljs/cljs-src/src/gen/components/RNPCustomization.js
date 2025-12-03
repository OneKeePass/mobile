function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
import * as React from 'react';
import { StyleSheet } from 'react-native';
import { MD3LightTheme, MD3DarkTheme, useTheme, Menu, Divider, TextInput } from 'react-native-paper';

// See Creating dynamic theme colors
// https://callstack.github.io/react-native-paper/theming.html (old one)
// https://callstack.github.io/react-native-paper/docs/guides/theming#creating-dynamic-theme-colors ( new one )

// The theme colors are created for both light and dark color schemes in that page and copied here

// Used #3141AD as the primary color 
// #388E3C a custom color - 'custom0' 
// #FBC02D a custom color - 'custom1'
// Custom colors are added as 'custom0',...'custom1',...

// See what colors rgb(c1,c2,c3) represent, we can use search from  https://duckduckgo.com/  
// and color and its hex code are shown

// Copied it from the color codes scheme and then used it here. The color used to generate this is #3141AD
const custLightThemeColors = {
  "colors": {
    "primary": "rgb(68, 83, 191)",
    "onPrimary": "rgb(255, 255, 255)",
    "primaryContainer": "rgb(223, 224, 255)",
    "onPrimaryContainer": "rgb(0, 13, 96)",
    "secondary": "rgb(91, 93, 114)",
    "onSecondary": "rgb(255, 255, 255)",
    "secondaryContainer": "rgb(224, 225, 249)",
    "onSecondaryContainer": "rgb(24, 26, 44)",
    "tertiary": "rgb(119, 83, 108)",
    "onTertiary": "rgb(255, 255, 255)",
    "tertiaryContainer": "rgb(255, 215, 240)",
    "onTertiaryContainer": "rgb(45, 17, 39)",
    "error": "rgb(186, 26, 26)",
    "onError": "rgb(255, 255, 255)",
    "errorContainer": "rgb(255, 218, 214)",
    "onErrorContainer": "rgb(65, 0, 2)",
    "background": "rgb(255, 251, 255)",
    "onBackground": "rgb(27, 27, 31)",
    "surface": "rgb(255, 251, 255)",
    "onSurface": "rgb(27, 27, 31)",
    "surfaceVariant": "rgb(227, 225, 236)",
    "onSurfaceVariant": "rgb(70, 70, 79)",
    "outline": "rgb(119, 118, 128)",
    "outlineVariant": "rgb(199, 197, 208)",
    "shadow": "rgb(0, 0, 0)",
    "scrim": "rgb(0, 0, 0)",
    "inverseSurface": "rgb(48, 48, 52)",
    "inverseOnSurface": "rgb(243, 240, 244)",
    "inversePrimary": "rgb(188, 195, 255)",
    "elevation": {
      "level0": "transparent",
      "level1": "rgb(246, 243, 252)",
      "level2": "rgb(240, 238, 250)",
      "level3": "rgb(234, 233, 248)",
      "level4": "rgb(233, 231, 247)",
      "level5": "rgb(229, 228, 246)"
    },
    "surfaceDisabled": "rgba(27, 27, 31, 0.12)",
    "onSurfaceDisabled": "rgba(27, 27, 31, 0.38)",
    "backdrop": "rgba(47, 48, 56, 0.4)",
    "custom0": "rgb(16, 109, 32)",
    "onCustom0": "rgb(255, 255, 255)",
    "custom0Container": "rgb(157, 248, 152)",
    "onCustom0Container": "rgb(0, 34, 4)",
    "custom1": "rgb(121, 89, 0)",
    "onCustom1": "rgb(255, 255, 255)",
    "custom1Container": "rgb(255, 223, 160)",
    "onCustom1Container": "rgb(38, 26, 0)"
  }
};

// Copied it from the color codes scheme and then used it here. The color used to generate this is #3141AD
const custDarkThemeColors = {
  "colors": {
    "primary": "rgb(188, 195, 255)",
    "onPrimary": "rgb(8, 30, 144)",
    "primaryContainer": "rgb(41, 58, 166)",
    "onPrimaryContainer": "rgb(223, 224, 255)",
    "secondary": "rgb(196, 197, 221)",
    "onSecondary": "rgb(45, 47, 66)",
    "secondaryContainer": "rgb(67, 69, 89)",
    "onSecondaryContainer": "rgb(224, 225, 249)",
    "tertiary": "rgb(230, 186, 215)",
    "onTertiary": "rgb(69, 38, 61)",
    "tertiaryContainer": "rgb(93, 60, 84)",
    "onTertiaryContainer": "rgb(255, 215, 240)",
    "error": "rgb(255, 180, 171)",
    "onError": "rgb(105, 0, 5)",
    "errorContainer": "rgb(147, 0, 10)",
    "onErrorContainer": "rgb(255, 180, 171)",
    "background": "rgb(27, 27, 31)",
    "onBackground": "rgb(228, 225, 230)",
    "surface": "rgb(27, 27, 31)",
    "onSurface": "rgb(228, 225, 230)",
    "surfaceVariant": "rgb(70, 70, 79)",
    "onSurfaceVariant": "rgb(199, 197, 208)",
    "outline": "rgb(144, 144, 154)",
    "outlineVariant": "rgb(70, 70, 79)",
    "shadow": "rgb(0, 0, 0)",
    "scrim": "rgb(0, 0, 0)",
    "inverseSurface": "rgb(228, 225, 230)",
    "inverseOnSurface": "rgb(48, 48, 52)",
    "inversePrimary": "rgb(68, 83, 191)",
    "elevation": {
      "level0": "transparent",
      "level1": "rgb(35, 35, 42)",
      "level2": "rgb(40, 40, 49)",
      "level3": "rgb(45, 46, 56)",
      "level4": "rgb(46, 47, 58)",
      "level5": "rgb(50, 51, 62)"
    },
    "surfaceDisabled": "rgba(228, 225, 230, 0.12)",
    "onSurfaceDisabled": "rgba(228, 225, 230, 0.38)",
    "backdrop": "rgba(47, 48, 56, 0.4)",
    "custom0": "rgb(130, 219, 126)",
    "onCustom0": "rgb(0, 57, 10)",
    "custom0Container": "rgb(0, 83, 18)",
    "onCustom0Container": "rgb(157, 248, 152)",
    "custom1": "rgb(248, 189, 42)",
    "onCustom1": "rgb(64, 45, 0)",
    "custom1Container": "rgb(92, 67, 0)",
    "onCustom1Container": "rgb(255, 223, 160)"
  }
};

// MD3LightTheme is extended with our app colors from 'custLightThemeColors' and 'custDarkThemeColors'
export const custLightTheme = {
  ...MD3LightTheme,
  colors: custLightThemeColors.colors
};
export const custDarkTheme = {
  ...MD3DarkTheme,
  colors: custDarkThemeColors.colors
};
export const RNPMenu = props => {
  // Following is another way of overriding props
  //   return (
  //     <Menu contentStyle={{backgroundColor: theme4.colors.onPrimary}} {...props}>
  //       {props.children}
  //     </Menu>
  //   );
  const theme = useTheme();
  const bc = theme.dark ? theme.colors.surfaceVariant : theme.colors.onPrimary;
  // Should use 'bc' not '{bc}' to use the already evaluated bc value
  return /*#__PURE__*/React.createElement(Menu, _extends({}, props, {
    contentStyle: [styles.contentStyle, {
      backgroundColor: bc
    }, props.style]
  }), props.children);
};
export const RNPDivider = props => {
  const theme = useTheme();
  //const bc = theme.dark? theme.colors.surfaceVariant: theme.colors.onSurfaceVariant;
  const bc = theme.colors.outline;
  return /*#__PURE__*/React.createElement(Divider, _extends({}, props, {
    style: [{
      backgroundColor: bc
    }, props.style]
  }));
};

// export const RNPTextInput = props => {
//   return (
//     <TextInput {...props} style={[styles.textInput, props.style]}>
//       {props.children}
//     </TextInput>
//   );
// };

// export const RNPTextInput = React.forwardRef((props, ref) => {
//   return (
//     <TextInput {...props} ref={ref} style={[styles.textInput, props.style]}>
//       {props.children}
//     </TextInput>
//   );
// });

// Now our custom textinput can accept 'ref' prop if used in addition to other props
export const RNPTextInput = React.forwardRef((props, ref) => {
  const theme = useTheme();
  //const bc = theme.dark? theme.colors.surfaceVariant: theme.colors.onPrimary;
  const bc = theme.colors.background;
  // For now only the 'backgroundColor' is set from theme
  return /*#__PURE__*/React.createElement(TextInput, _extends({}, props, {
    ref: ref,
    style: [{
      backgroundColor: bc
    }, props.style]
  }), props.children);
});
const styles = StyleSheet.create({
  contentStyle: {
    backgroundColor: custLightTheme.colors.onPrimary
  },
  textInput: {
    backgroundColor: custLightTheme.colors.onPrimary
  }
});

/*
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

*/