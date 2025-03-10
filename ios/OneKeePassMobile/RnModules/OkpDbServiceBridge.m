//
//  OkpDbServiceBridge.m
//  OkpOneKeePassMobile
//
//  Created  9/15/22.
//
#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(OkpDbService, NSObject)

///  Invoke 
RCT_EXTERN_METHOD(invokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(iOSInvokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
///

RCT_EXTERN_METHOD(kdbxUriToOpenOnCreate:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(completeSaveAsOnError:(NSString *)args resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(readKdbx:(NSString *)fullFileNameUri jsonArgs:(NSString *)jsonArgs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(copyKeyFile:(NSString *)fullFileNameUri resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(uploadAttachment:(NSString *)fullFileNameUri jsonArgs:(NSString *)jsonArgs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(handlePickedFile:(NSString *)fullFileNameUri jsonArgs:(NSString *)jsonArgs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(saveKdbx:(NSString *) fullFileNameUri overwrite:(BOOL)overwrite  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(authenticateWithBiometric:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

// App Group related

RCT_EXTERN_METHOD(autoFillInvokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

//RCT_EXTERN_METHOD(copyFileToAppGroup:(NSString *)args resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

//RCT_EXTERN_METHOD(deleteAppGroupFiles:(NSString *)args resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end


