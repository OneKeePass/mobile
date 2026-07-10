const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
const path = require('path');
/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */

// Fully-FOSS Android APK builds exclude react-native-vision-camera's native
// module (see react-native.config.js). vision-camera v5 runs
// NitroModules.createHybridObject('CameraFactory') at import time, which crashes
// such a build at startup. When OKP_FOSS_BUILD=1 (set by the release script for
// --target apk) we redirect the bare 'react-native-vision-camera' import to a
// local stub so the JS bundle loads without the native camera.
const FOSS_BUILD = process.env.OKP_FOSS_BUILD === '1';
const visionCameraStub = path.resolve(
  __dirname,
  'foss-stubs/react-native-vision-camera.js',
);

const config = {
  resolver: {
    resolveRequest: (context, moduleName, platform) => {
      if (FOSS_BUILD && moduleName === 'react-native-vision-camera') {
        return {type: 'sourceFile', filePath: visionCameraStub};
      }
      return context.resolveRequest(context, moduleName, platform);
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
