//
//  OkpDocumentPickerServiceBridge.m

#import <Foundation/Foundation.h>

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(OkpDocumentPickerService, NSObject)

RCT_EXTERN_METHOD(pickDirectory:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(pickKdbxFileToOpen:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(pickKdbxFileToCreate:(NSString *)fileName resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(pickAndSaveNewKdbxFile:(NSString *)fileName jsonArgs:(NSString *)jsonArgs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(pickOnSaveErrorSaveAs:(NSString *)existingFullFileNameUri resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
