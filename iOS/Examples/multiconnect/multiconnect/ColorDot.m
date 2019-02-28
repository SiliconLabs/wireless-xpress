//
//  GrayDot.m
//  multiconnect
//
//  Created by Brant Merryman on 10/12/18.
//  Copyright © 2018 Silicon Labs. All rights reserved.
//

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
