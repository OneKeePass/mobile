

This works and release builds without any reference to 'react-native-device-info'
But we need to remove 'react-native-device-info' map during dev time so that 
Krell can use in 'krell_repl.js'


Release time

module.exports = {
  dependencies: {
    'react-native-vector-icons': {
      platforms: {
        ios: null,
      },
    },
    'react-native-device-info': {
      platforms: {
        android: null,
      },
    },
  },
};


Debug/Dev time

module.exports = {
  dependencies: {
    'react-native-vector-icons': {
      platforms: {
        ios: null,
      },
    },
  },
};


=====================================================================================

See https://github.com/react-native-community/cli/issues/1538

This did not work as the release build still tries to include react-native-device-info and fails
because of the use 'buildTypes: ['debug']'


module.exports = {
  dependencies: {
    'react-native-vector-icons': {
      platforms: {
        ios: null,
      },
    },
    'react-native-device-info': {
      platforms: {
        android: {
          buildTypes: ['debug'],
        },
      },
    },
  },
};