//
//  OkpDbServiceBridge.m
//  OneKeePassAutoFill
//
//  Created on 6/14/24.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(OkpDbService, NSObject)

// This meant for the ussual Commands api calls
RCT_EXTERN_METHOD(invokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

// This is meant for making invoke commands for autofill/app group related calls
RCT_EXTERN_METHOD(autoFillInvokeCommand:(NSString *)commandName args:(NSString *)args  resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelExtension:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end

