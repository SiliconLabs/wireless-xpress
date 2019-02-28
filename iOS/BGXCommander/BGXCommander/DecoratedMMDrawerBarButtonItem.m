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

#import "DecoratedMMDrawerBarButtonItem.h"

@interface DecoratedButtonView : UIButton

@property (nonatomic,strong) UIColor * menuButtonNormalColor;
@property (nonatomic,strong) UIColor * menuButtonHighlightedColor;

@property (nonatomic,strong) UIColor * shadowNormalColor;
@property (nonatomic,strong) UIColor * shadowHighlightedColor;

-(UIColor *)menuButtonColorForState:(UIControlState)state;
-(void)setMenuButtonColor:(UIColor *)color forState:(UIControlState)state;

-(UIColor *)shadowColorForState:(UIControlState)state;
-(void)setShadowColor:(UIColor *)color forState:(UIControlState)state;

@property (nonatomic) DecorationState decorationState;

@end

@implementation DecoratedButtonView

-(id)initWithFrame:(CGRect)frame{
    self = [super initWithFrame:frame];
    if(self){
        [self setMenuButtonNormalColor:[[UIColor whiteColor] colorWithAlphaComponent:0.9f]];
        [self setMenuButtonHighlightedColor:[UIColor colorWithRed:139.0/255.0
                                                            green:135.0/255.0
                                                             blue:136.0/255.0
                                                            alpha:0.9f]];
        
        [self setShadowNormalColor:[[UIColor blackColor] colorWithAlphaComponent:0.5f]];
        [self setShadowHighlightedColor:[[UIColor blackColor] colorWithAlphaComponent:0.2f]];
    }
    return self;
}

-(UIColor *)menuButtonColorForState:(UIControlState)state{
    UIColor * color;
    switch (state) {
        case UIControlStateNormal:
            color = self.menuButtonNormalColor;
            break;
        case UIControlStateHighlighted:
            color = self.menuButtonHighlightedColor;
            break;
        default:
            break;
    }
    return color;
}

-(void)setMenuButtonColor:(UIColor *)color forState:(UIControlState)state{
    switch (state) {
        case UIControlStateNormal:
            [self setMenuButtonNormalColor:color];
            break;
        case UIControlStateHighlighted:
            [self setMenuButtonHighlightedColor:color];
            break;
        default:
            break;
    }
    [self setNeedsDisplay];
}

-(UIColor *)shadowColorForState:(UIControlState)state{
    UIColor * color;
    switch (state) {
        case UIControlStateNormal:
            color = self.shadowNormalColor;
            break;
        case UIControlStateHighlighted:
            color = self.shadowHighlightedColor;
            break;
        default:
            break;
    }
    return color;
}

-(void)setShadowColor:(UIColor *)color forState:(UIControlState)state{
    switch (state) {
        case UIControlStateNormal:
            [self setShadowNormalColor:color];
            break;
        case UIControlStateHighlighted:
            [self setShadowHighlightedColor:color];
            break;
        default:
            break;
    }
    [self setNeedsDisplay];
}

-(void)drawRect:(CGRect)rect{
    //// General Declarations
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSaveGState(context);
    
    //// Color Declarations
    UIColor* fillColor = [UIColor whiteColor];
    
    //// Frames
    CGRect frame = CGRectMake(0, 0, 26, 26);
    
    //// Bottom Bar Drawing
    UIBezierPath* bottomBarPath = [UIBezierPath bezierPathWithRect: CGRectMake(CGRectGetMinX(frame) + floor((CGRectGetWidth(frame) - 16) * 0.50000 + 0.5), CGRectGetMinY(frame) + floor((CGRectGetHeight(frame) - 1) * 0.72000 + 0.5), 16, 1)];
    [fillColor setFill];
    [bottomBarPath fill];
    
    
    //// Middle Bar Drawing
    UIBezierPath* middleBarPath = [UIBezierPath bezierPathWithRect: CGRectMake(CGRectGetMinX(frame) + floor((CGRectGetWidth(frame) - 16) * 0.50000 + 0.5), CGRectGetMinY(frame) + floor((CGRectGetHeight(frame) - 1) * 0.48000 + 0.5), 16, 1)];
    [fillColor setFill];
    [middleBarPath fill];
    
    
    //// Top Bar Drawing
    UIBezierPath* topBarPath = [UIBezierPath bezierPathWithRect: CGRectMake(CGRectGetMinX(frame) + floor((CGRectGetWidth(frame) - 16) * 0.50000 + 0.5), CGRectGetMinY(frame) + floor((CGRectGetHeight(frame) - 1) * 0.24000 + 0.5), 16, 1)];
    [fillColor setFill];
    [topBarPath fill];
    
    if (NoDecoration != self.decorationState) {
    
        UIImage * decorationImage;
        
        if (SecurityDecoration == self.decorationState) {
            decorationImage = [UIImage imageNamed:@"Security_Decoration"];
        } else if (UpdateDecoration == self.decorationState) {
            decorationImage = [UIImage imageNamed:@"Update_Decoration"];
        }
        
    
        if (decorationImage) {
            CGFloat xOffset = (self.bounds.size.width - decorationImage.size.width)/2;
            CGFloat yOffset = 0; //(self.bounds.size.height - decorationImage.size.height)/2;
            
            [decorationImage drawInRect:CGRectMake(xOffset, yOffset, decorationImage.size.width, decorationImage.size.height)];
        }

    }
    
    CGContextRestoreGState(context);
}

-(void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event{
    [super touchesBegan:touches withEvent:event];
    [self setNeedsDisplay];
}

-(void)touchesEnded:(NSSet *)touches withEvent:(UIEvent *)event{
    [super touchesEnded:touches withEvent:event];
    [self setNeedsDisplay];
}

-(void)touchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event{
    [super touchesCancelled:touches withEvent:event];
    [self setNeedsDisplay];
}

-(void)setSelected:(BOOL)selected{
    [super setSelected:selected];
    [self setNeedsDisplay];
}

-(void)setHighlighted:(BOOL)highlighted{
    [super setHighlighted:highlighted];
    [self setNeedsDisplay];
}

-(void)setTintColor:(UIColor *)tintColor{
    if([super respondsToSelector:@selector(setTintColor:)]){
        [super setTintColor:tintColor];
    }
}

-(void)tintColorDidChange{
    [self setNeedsDisplay];
}

@end

@interface DecoratedMMDrawerBarButtonItem()

@property (nonatomic, strong) DecoratedButtonView * decoratedButtonView;

@end

@implementation DecoratedMMDrawerBarButtonItem

-(void)touchUpInside:(id)sender{
    
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
    [self.target performSelector:self.action withObject:sender];
#pragma clang diagnostic pop;
    
}


-(id)initWithTarget:(id)target action:(SEL)action{
    DecoratedButtonView * buttonView = [[DecoratedButtonView alloc] initWithFrame:CGRectMake(0, 0, 26, 26)];
    [buttonView addTarget:self action:@selector(touchUpInside:) forControlEvents:UIControlEventTouchUpInside];
    self = [self initWithCustomView:buttonView];
    if(self){
        self.decoratedButtonView = buttonView;
    }
    self.action = action;
    self.target = target;
    return self;
}

- (void)setDecorationState:(DecorationState)ds
{
    self.decoratedButtonView.decorationState = ds;
    [self.decoratedButtonView setNeedsDisplay];
}

- (DecorationState)getDecorationState
{
    return self.decoratedButtonView.decorationState;
}

@end
