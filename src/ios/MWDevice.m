#import "MWDevice.h"
#import "RSSI.h"
#import <Cordova/CDV.h>
#import "MBLMetaWear.h"
#import "MBLMetaWearManager.h"
#import "MWAccelerometer.h"


@implementation MWDevice {
  NSArray *scannedDevices;
  RSSI *rssi;
  MWAccelerometer *accelerometer;
}

- (void)scanForDevices:(CDVInvokedUrlCommand*)command
{
  CDVPluginResult* pluginResult = nil;
  NSMutableDictionary *boards = [NSMutableDictionary dictionaryWithDictionary:@{}];

  NSLog(@"Scanning for Metawears");

  [[MBLMetaWearManager sharedManager] startScanForMetaWearsAllowDuplicates:YES handler:^(NSArray *array) {
      scannedDevices = array;
      NSLog(@"Scanning callback");
      [[MBLMetaWearManager sharedManager] stopScanForMetaWears];
      for (MBLMetaWear *device in array) {
        NSMutableDictionary *entry = [NSMutableDictionary dictionaryWithDictionary:@{}];
        entry[@"address"] = device.identifier.UUIDString;
        entry[@"rssi"] = [device.discoveryTimeRSSI stringValue];
        boards[device.identifier.UUIDString] = entry;
        NSLog(@"Found MetaWear: %@", device);
        [device rememberDevice];
      }
      CDVPluginResult* pluginResult = nil;
      pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:boards];

      [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];

}

- (void)connect:(CDVInvokedUrlCommand*)command
{
  NSString* uUIDString = [command.arguments objectAtIndex:0];

  [[MBLMetaWearManager sharedManager] retrieveSavedMetaWearsWithHandler:^(NSArray *array) {
      __block CDVPluginResult *pluginResult = nil;

      bool foundDevice = false;
      for (MBLMetaWear *device in array){
        if([device.identifier.UUIDString isEqualToString:uUIDString]){
          foundDevice = true;
          [device connectWithTimeout:20 handler:^(NSError *error) {
              if ([error.domain isEqualToString:kMBLErrorDomain] &&
                  error.code == kMBLErrorConnectionTimeout) {
                NSLog(@"Connection Timeout");
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"ERROR"];
              }
              else {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"CONNECTED"];
                _connectedDevice = device;
              }
              [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }];
        }
      }
      if(foundDevice == false){
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"ERROR"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
      }
    }];
}

- (void)disconnect:(CDVInvokedUrlCommand*)command
{
  __block CDVPluginResult* pluginResult = nil;

  NSLog(@"disconnecting from metawear");
  [_connectedDevice disconnectWithHandler:^(NSError *error) {
      if ([error.domain isEqualToString:kMBLErrorDomain] &&
          error.code == kMBLErrorConnectionTimeout) {
        NSLog(@"Disconnect Problem");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"ERROR"];
      }
      else {
        NSLog(@"disconnecting");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"DISCONNECTED"];
        _connectedDevice = nil;
      }
      [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)readRssi:(CDVInvokedUrlCommand*)command
{
  if(rssi == nil)
    {
      rssi = [[RSSI alloc] initWithDevice:self];
    }
  NSLog(@"read RSSI on %@", rssi);
  [rssi readRssi:command];
}

- (void)startAccelerometer:(CDVInvokedUrlCommand*)command
{
  if(accelerometer == nil)
    {
      accelerometer = [[MWAccelerometer alloc] initWithDevice:self];
    }
  NSLog(@"read Accelerometer on %@", accelerometer);
  [accelerometer startAccelerometer:command];
}

- (void)stopAccelerometer:(CDVInvokedUrlCommand*)command
{
  NSLog(@"stop accelerometer on %@", accelerometer);
  [accelerometer stopAccelerometer:command];
}
@end
