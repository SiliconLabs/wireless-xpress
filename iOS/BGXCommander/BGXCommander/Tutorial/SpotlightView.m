/*
 * Copyright 2018-2020 Silicon Labs
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

#import "SpotlightView.h"


SpotlightView * gSpotlightView = nil;

@interface SpotlightLayer : CALayer
@property (nonatomic) CGPoint spotlightLocation;
@property (nonatomic) CGFloat spotlightRadius;
@end
@implementation SpotlightLayer



-(id)initWithLayer:(id)layer {
  if( ( self = [super initWithLayer:layer] ) ) {
    if ([layer isKindOfClass:[SpotlightLayer class]]) {
      self.spotlightLocation = ((SpotlightLayer*)layer).spotlightLocation;
    }
  }
  return self;
}

+(BOOL)needsDisplayForKey:(NSString*)key {
  if( [key isEqualToString:@"spotlightLocation"]
     || [key isEqualToString:@"spotlightRadius"]
     || [key isEqualToString:@"opacity"]  )
    return YES;
  return [super needsDisplayForKey:key];
}

-(id<CAAction>)actionForKey:(NSString *)event {

  if( [event isEqualToString:@"spotlightLocation"]
     || [event isEqualToString:@"spotlightRadius"]
     || [event isEqualToString:@"opacity"]   ) {
    CABasicAnimation *theAnimation = [CABasicAnimation
                                      animationWithKeyPath:event];

    theAnimation.fromValue = [[self presentationLayer] valueForKey:event];
    return theAnimation;
  }

  return [super actionForKey:event];
}

@end

@implementation SpotlightView

- (void)awakeFromNib
{
  [self generalInit];
  [super awakeFromNib];
}

+ (SpotlightView *)spotlightView
{
  return gSpotlightView;
}

+(Class)layerClass {
  return [SpotlightLayer class];
}

-(id)init {
  if( self = [super init] ) {

    [self generalInit];
  }
  return self;
}

- (id)initWithFrame:(CGRect)frame
{
  if( ( self = [super initWithFrame:frame] ) ) {

     [self generalInit];
  }
  return self;
}

- (void)generalInit
{
  CALayer * layer = [self layer];
  layer.opaque = NO;

  gSpotlightView = self;
}


- (void)drawRect:(CGRect)rect {
    // Drawing code
}



- (void)drawLayer:(CALayer *)layer inContext:(CGContextRef)context
{
      // Drawing code

  CGPoint sp = ((SpotlightLayer*)layer).spotlightLocation;

  CGFloat  op = ((SpotlightLayer *)layer).opacity;

  CGFloat rd = ((SpotlightLayer *)layer).spotlightRadius;

  UIColor * grayColor = [UIColor colorWithRed:0.0 green:0.0 blue:0.0 alpha:op];
  CGContextSetFillColorWithColor(context, grayColor.CGColor);

  CGRect circleRect = CGRectMake(sp.x - rd, sp.y - rd, 2 * rd, 2 * rd);

  CGContextAddEllipseInRect(context, circleRect);
  CGContextAddRect(context, CGRectInfinite);
  CGContextEOClip(context);
  CGContextFillRect(context, self.bounds);

}

- (void)setSpotlightPosition:(CGPoint)pt radius:(CGFloat)r opacity:(CGFloat)opacity animated:(BOOL)animated
{
  if (animated) {

    CABasicAnimation* locationAnimation = [CABasicAnimation animation];
    CABasicAnimation* opacityAnimation = [CABasicAnimation animation];
    CABasicAnimation* radiusAnimation = [CABasicAnimation animation];

    locationAnimation.duration = 0.50;
    locationAnimation.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionEaseOut];
    locationAnimation.fromValue = [NSValue valueWithCGPoint:((SpotlightLayer *) self.layer).spotlightLocation];
    locationAnimation.toValue = [NSValue valueWithCGPoint:pt];

    opacityAnimation.duration = 0.50;
    opacityAnimation.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionEaseOut];
    opacityAnimation.fromValue = [NSNumber numberWithFloat:((SpotlightLayer *) self.layer).opacity];
    opacityAnimation.toValue = [NSNumber numberWithFloat:opacity];

    radiusAnimation.duration = 0.50;
    radiusAnimation.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionEaseOut];
    radiusAnimation.fromValue = [NSNumber numberWithFloat:((SpotlightLayer *) self.layer).spotlightRadius];
    radiusAnimation.toValue = [NSNumber numberWithFloat:r];


    self.spotlightLocation = pt;
    self.spotlightRadius = r;
    self.opacity = opacity;

    [self.layer addAnimation:locationAnimation forKey:@"spotlightLocation"];
    [self.layer addAnimation:opacityAnimation forKey:@"opacity"];
    [self.layer addAnimation:radiusAnimation forKey:@"spotlightRadius"];

  } else {
    self.spotlightLocation = pt;
    self.spotlightRadius = r;
    self.opacity = opacity;

    [self setNeedsDisplay];
  }

}

- (CGPoint)spotlightLocation
{
  return ((SpotlightLayer *)self.layer).spotlightLocation;
}

- (void)setSpotlightLocation:(CGPoint)spotlightLocation
{
  ((SpotlightLayer *)self.layer).spotlightLocation = spotlightLocation;
}

- (CGFloat)spotlightRadius
{
  return ((SpotlightLayer *)self.layer).spotlightRadius;
}

- (void)setSpotlightRadius:(CGFloat)spotlightRadius
{
  ((SpotlightLayer *)self.layer).spotlightRadius = spotlightRadius;
}

- (double)opacity
{
  return ((SpotlightLayer *)self.layer).opacity;
}

- (void)setOpacity:(double)opacity
{
  ((SpotlightLayer *)self.layer).opacity = opacity;
}

@end
