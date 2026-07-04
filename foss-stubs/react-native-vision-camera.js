'use strict';

// FOSS-build stub for react-native-vision-camera.
//
// The real package (v5) executes NitroModules.createHybridObject('CameraFactory')
// at import time (see node_modules/react-native-vision-camera/src/VisionCamera.ts).
// Fully-FOSS Android APK builds exclude the native camera module (see
// react-native.config.js), so that side effect crashes the app at startup with
// "Cannot create an instance of HybridObject CameraFactory".
//
// metro.config.js aliases the bare 'react-native-vision-camera' import to this
// stub when OKP_FOSS_BUILD=1, so the JS bundle loads without touching the native
// camera. The camera / QR-scan UI is already disabled at runtime via
// is-rn-native-camera-vison-disabled (NativeModules.CameraView is nil in FOSS
// builds), so none of these stubs are actually invoked — they exist only so the
// property access done at cljs namespace-load time is safe.

// Dummy component so (r/adapt-react-class Camera) in rn_components.cljs works.
function Camera() {
  return null;
}

// Static methods read as (.-Camera ...) / called from background.cljs.
Camera.getCameraPermissionStatus = function () {
  return 'denied';
};
Camera.requestCameraPermission = function () {
  return Promise.resolve('denied');
};
Camera.getMicrophonePermissionStatus = function () {
  return 'denied';
};
Camera.requestMicrophonePermission = function () {
  return Promise.resolve('denied');
};
Camera.getAvailableCameraDevices = function () {
  return [];
};

// Hooks read at namespace load in rn_components.cljs. Safe no-op implementations
// (they call no other hooks, so they are legal to invoke if ever rendered).
function useCameraPermission() {
  return {
    hasPermission: false,
    requestPermission: function () {
      return Promise.resolve(false);
    },
  };
}

function useCameraDevice() {
  return undefined;
}

function useCodeScanner() {
  return undefined;
}

module.exports = {
  Camera: Camera,
  useCameraPermission: useCameraPermission,
  useCameraDevice: useCameraDevice,
  useCodeScanner: useCodeScanner,
};
