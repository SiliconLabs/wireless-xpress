/*
 * Copyright 2019 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#import "Version.h"



@implementation Version

- (id)initWithString:(NSString *)versionString
{
    self = [super init];
    if (self) {
        NSArray * comps = [versionString componentsSeparatedByString:@"."];
        if (4 != comps.count) {
            [NSException raise:NSInvalidArgumentException
                        format:@"Invalid version string: %@. Expected 4 components, got %lu", versionString, (unsigned long) comps.count];
        }
        
        if (! [[NSScanner scannerWithString:[comps objectAtIndex:0]] scanInt:&_major] ) {
            [NSException raise:NSInvalidArgumentException format:@"Invalid major version: %@. Expected an integer.", [comps objectAtIndex:0]];
        }
        if (! [[NSScanner scannerWithString:[comps objectAtIndex:1]] scanInt:&_minor] ) {
            [NSException raise:NSInvalidArgumentException format:@"Invalid major version: %@. Expected an integer.", [comps objectAtIndex:1]];
        }
        if (! [[NSScanner scannerWithString:[comps objectAtIndex:2]] scanInt:&_build] ) {
            [NSException raise:NSInvalidArgumentException format:@"Invalid major version: %@. Expected an integer.", [comps objectAtIndex:2]];
        }
        if (! [[NSScanner scannerWithString:[comps objectAtIndex:3]] scanInt:&_revision] ) {
            [NSException raise:NSInvalidArgumentException format:@"Invalid major version: %@. Expected an integer.", [comps objectAtIndex:3]];
        }

    }
    return self;
}

+ (Version *)versionFromString:(NSString *)versionString
{
    return [[Version alloc] initWithString: versionString];
}

- (NSComparisonResult)compare:(Version *)otherVersion
{
    NSComparisonResult cr = NSOrderedSame;
    
    if (self.major < otherVersion.major) {
        cr = NSOrderedAscending;
    } else if (self.major > otherVersion.major) {
        cr = NSOrderedDescending;
    } else if (self.minor < otherVersion.minor) {
        cr = NSOrderedAscending;
    } else if (self.minor > otherVersion.minor) {
        cr = NSOrderedDescending;
    } else if (self.build < otherVersion.build) {
        cr = NSOrderedAscending;
    } else if (self.build > otherVersion.build) {
        cr = NSOrderedDescending;
    } else if (self.revision < otherVersion.revision) {
        cr = NSOrderedAscending;
    } else if (self.revision > otherVersion.revision) {
        cr = NSOrderedDescending;
    }
    
    return cr;
}

- (NSString *)description
{
    return [NSString stringWithFormat:@"Version: %d.%d.%d.%d", self.major, self.minor, self.build, self.revision];
}

@end
