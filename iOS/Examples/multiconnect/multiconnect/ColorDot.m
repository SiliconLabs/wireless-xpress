/*
 * Copyright 2018-2019 Silicon Labs
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

#import "ColorDot.h"



@implementation ColorDot



- (void)awakeFromNib
{
    self.backgroundColor = [UIColor clearColor];
    [super awakeFromNib];
}

- (void)setColor:(UIColor *)color
{
    _color = color;
    [self setNeedsDisplay];
}

- (void)drawRect:(CGRect)rect
{
    CGFloat red, green, blue, alpha;

    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSaveGState(context);
    CGContextAddEllipseInRect(context, self.bounds);
    [self.color getRed:&red green:&green blue:&blue alpha:&alpha];
    CGContextSetRGBFillColor(context, red, green, blue, alpha);
    CGContextFillPath(context);
    CGContextRestoreGState(context);
}

@end
