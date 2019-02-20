#ifndef DRAW_H_
#define DRAW_H_

/*****************************************************************************/
/* Configuration                                                             */
/*****************************************************************************/

/***** These macros are configurable, the ones further below are not *****/

#define SCREEN_HEADER_START_ROW   1
#define SCREEN_BODY_START_ROW     5
#define SCREEN_FOOTER_START_ROW   13

#define SINGLE_SPACED   1
#define DOUBLE_SPACED   2
#define LINE_SPACING    DOUBLE_SPACED

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/

/*************** DON'T CHANGE THESE MACROS (#DEFINES) ***************/

// Note: these macros have been hardcoded to match the DISP_HEIGHT,
// DISP_WIDTH, FONT_HEIGHT, FONT_WIDTH macros in disp.h and render.h
// The disp.h and render.h files were not included because this produced
// "attempt to redefine macro" warnings and I'm not sure why.
// These macros are in units of pixels
#define DISP_HEIGHT   128
#define DISP_WIDTH    128
#define FONT_HEIGHT   8
#define FONT_WIDTH    6

// Max number of rows for this LCD
#define MAX_LCD_ROWS   (DISP_HEIGHT / FONT_HEIGHT)
#define MAX_LCD_COLS   (DISP_WIDTH / FONT_WIDTH)

// Max number of rows in each section
#define MAX_TEXT_HEADER_ROWS   1 // No built in support for anything other than one header line at the moment (not hard to change though)
#if LINE_SPACING == SINGLE_SPACED
  #define MAX_TEXT_BODY_ROWS   (SCREEN_FOOTER_START_ROW - SCREEN_BODY_START_ROW)
#elif LINE_SPACING == DOUBLE_SPACED
  #define MAX_TEXT_BODY_ROWS   ((SCREEN_FOOTER_START_ROW - SCREEN_BODY_START_ROW + 1) / LINE_SPACING)
#endif
#define MAX_TEXT_FOOTER_ROWS   (MAX_LCD_ROWS - SCREEN_FOOTER_START_ROW - 1) // Minus one because dashed line takes up a line

// Max buffer size for each section
#define MAX_TEXT_HEADER_BUFFER_SIZE   (MAX_TEXT_HEADER_ROWS * MAX_LCD_COLS)
#define MAX_TEXT_BODY_BUFFER_SIZE     (MAX_TEXT_BODY_ROWS   * MAX_LCD_COLS)
#define MAX_TEXT_FOOTER_BUFFER_SIZE   (MAX_TEXT_FOOTER_ROWS * MAX_LCD_COLS)

/*****************************************************************************/
/* Typedefs                                                                  */
/*****************************************************************************/

typedef struct {
  const uint8_t *image_bits; // Pointer to the sprite image's bit array
  uint8_t height;            // The sprite's height
  uint8_t width;             // The sprite's width
} Sprite_t;

/*****************************************************************************/
/* Function Prototypes                                                       */
/*****************************************************************************/

// Initialization function
void drawInit(void);

// Text drawing functions
void drawText(uint8_t *str, uint8_t row, uint8_t col);
void drawTextBeforeHeader(uint8_t *str);
void drawTextHeader(uint8_t *str);
void drawTextHeaderTwoLines(uint8_t *str1, uint8_t *str2);
void drawTextFooter(uint8_t *str);
void drawTextBody(uint8_t *str);
void drawTextBodyAtCol(uint8_t *str, uint8_t col);

// Sprite drawing functions
void drawSprite(Sprite_t *sprite, uint8_t row, uint8_t col);
void drawSpriteToLineBuffer(Sprite_t *sprite, uint8_t row, uint8_t col);
void drawSpriteLineBufferToLcd(uint8_t row);

// Erasing functions
void eraseScreen(void);
void eraseLineBuffer(void);

// Unit conversion / utility functions
uint8_t rowToPixel(uint8_t rowNum);
uint8_t colToPixel(uint8_t colNum);

#endif /* DRAW_H_ */
