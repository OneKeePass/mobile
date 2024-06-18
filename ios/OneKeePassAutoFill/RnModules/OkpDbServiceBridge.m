//
//  OkpDbServiceBridge.m
//  OneKeePassAutoFill
//
//  Created on 6/14/24.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(OkpDbService, NSObject)

RCT_EXTERN_METHOD(invokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelExtension:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end

