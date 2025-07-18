#import "ZipPlugin.h"
#import "CDVFile.h"

@implementation ZipPlugin

- (NSString *)pathForURL:(NSString *)urlString
{
    // Attempt to use the File plugin to resolve the destination argument to a
    // file path.
    NSString *path = nil;
    id filePlugin = [self.commandDelegate getCommandInstance:@"File"];
    if (filePlugin != nil) {
        CDVFilesystemURL* url = [CDVFilesystemURL fileSystemURLWithString:urlString];
        path = [filePlugin filesystemPathForURL:url];
    }
    // If that didn't work for any reason, assume file: URL.
    if (path == nil) {
        if ([urlString hasPrefix:@"file:"]) {
            path = [[NSURL URLWithString:urlString] path];
        }
    }
    return path;
}

- (void)unzip:(CDVInvokedUrlCommand*)command
{
    self->_command = command;
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* pluginResult = nil;
        @try {
            NSString *zipURL = [command.arguments objectAtIndex:0];
            NSString *destinationURL = [command.arguments objectAtIndex:1];
            NSError *error;

            NSString *zipPath = [self pathForURL:zipURL];
            NSString *destinationPath = [self pathForURL:destinationURL];

            if([SSZipArchive unzipFileAtPath:zipPath toDestination:destinationPath overwrite:YES password:nil error:&error delegate:self]) {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            } else {
                NSString *code = @"UNKNOWN_ERROR";
                NSString *msg = @"Error occurred during unzipping";
                if (error) {
                    msg = [error localizedDescription] ?: msg;
                    switch (error.code) {
                        case ENOENT:
                            code = @"NO_ZIP_FILE";
                            break;
                        case ENOSPC:
                            code = @"OUT_OF_STORAGE";
                            break;
                        default:
                            if ([msg containsString:@"No space left"] || [msg containsString:@"out of space"] || [msg containsString:@"ENOSPC"]) {
                                code = @"OUT_OF_STORAGE";
                            } else if ([msg containsString:@"directory"] && [msg containsString:@"create"]) {
                                code = @"OUTPUT_DIR_ERROR";
                            } else if ([msg containsString:@"corrupt"] || [msg containsString:@"CRC"] || [msg containsString:@"crc"]) {
                                code = @"BAD_ZIP_FILE";
                            }
                            break;
                    }
                }
                NSDictionary *errDict = @{ @"code": code, @"message": msg };
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:errDict];
            }
        } @catch(NSException* exception) {
            NSString *msg = [exception reason] ?: @"Error occurred during unzipping";
            NSDictionary *errDict = @{ @"code": @"UNKNOWN_ERROR", @"message": msg };
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:errDict];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)zipArchiveWillUnzipFileAtIndex:(NSInteger)fileIndex totalFiles:(NSInteger)totalFiles archivePath:(NSString *)archivePath fileInfo:(unz_file_info)fileInfo
{
    NSMutableDictionary* message = [NSMutableDictionary dictionaryWithCapacity:2];
    [message setObject:[NSNumber numberWithLongLong:fileIndex] forKey:@"loaded"];
    [message setObject:[NSNumber numberWithLongLong:totalFiles] forKey:@"total"];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self->_command.callbackId];
}
@end
