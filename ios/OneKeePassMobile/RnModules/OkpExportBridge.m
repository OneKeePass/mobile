//
//  OkpExportBridge.m
//  OneKeePassMobile
//
//  Created on 3/2/23.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>


@interface RCT_EXTERN_MODULE(OkpExport, NSObject)
RCT_EXTERN_METHOD(exportKdbx:(NSString *)fullFileNameUri resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
@end
