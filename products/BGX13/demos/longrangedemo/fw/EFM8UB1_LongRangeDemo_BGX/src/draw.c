/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

// Necessary LCD driver files
#include <SI_EFM8UB1_Register_Enums.h>
#include "disp.h"
#include "render.h"

// Files added for this LCD drawing library
#include <STRING.h>
#include "draw.h"

/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

// Constant used to easily draw a dashed line across the screen
SI_SEGMENT_VARIABLE(DASHED_LINE, static const uint8_t*, SI_SEG_CODE) =
{
  "---------------------"
};

// A global line buffer used to render text and sprites to the LCD
SI_SEGMENT_VARIABLE(line[DISP_BUF_SIZE], static uint8_t, RENDER_LINE_SEG);

/***************************************************************************//**
 * @brief
 *    Initialize the LCD display
 ******************************************************************************/
void drawInit(void)
{
  DISP_Init();
}

/***************************************************************************//**
 * @brief
 *    Display one line of text to the LCD. Text will be displayed horizontally
 *    given the row and starting column.
 *
 * @param[in] str
 *    The string to display
 *
 * @param[in] row
 *    The row number to display the text (must be between 0 - 15)
 *
 * @param[in] col
 *    The column number to display the text (must be between 0 - 20)
 *
 * @details
 *    The LCD is 128 x 128 pixels, the font height is 8 pixels, and the font
 *    width is 6 pixels. Max row = (128 / 8) = 16. Max column = (128 / 6) = 21.
 *    With zero-based indexing, row numbers range from (0 - 15) and column
 *    numbers range from (0 - 20).
 *
 * @details
 *    The other drawTextXxx() functions were mainly created for convenience.
 *    This is the only function really necessary for drawing text and offers the
 *    most flexibility in terms of placing text at the exact row and column
 *    desired.
 ******************************************************************************/
void drawText(uint8_t *str, uint8_t row, uint8_t col)
{
  uint8_t r;

  // Convert row and column to numeric pixel equivalents
  uint8_t pixelRow = rowToPixel(row);
  uint8_t pixelCol = colToPixel(col);

  RENDER_ClrLine(line);
  for (r = 0; r < FONT_HEIGHT; r++)
  {
    RENDER_StrLine(line, pixelCol, r, str);
    DISP_WriteLine(pixelRow + r, line);
  }
}

/***************************************************************************//**
 * @brief
 *    Display a line of text the line before the actual title header text. This
 *    function was mainly created to display the device name at the top of each
 *    screen.
 *
 *            BGX-Device-Name
 *    ---------------------------------
 *              Header Text
 *    ---------------------------------
 *
 * @param[in] str
 *    The string to display. The full string will only be displayed if less
 *    than 21 characters. Input to this function is assumed to be one line
 *    in length
 ******************************************************************************/
void drawTextBeforeHeader(uint8_t *str)
{
#if SCREEN_HEADER_START_ROW == 0
  str = ""; // Note: This is a dummy assignment to ignore the compiler warning "unreferenced local variable."
            //       Since we didn't dereference the pointer, this statement doesn't do anything
#elif SCREEN_HEADER_START_ROW > 0
  uint8_t startCol;

  // If the header already starts at line zero, return immediately
  if (SCREEN_HEADER_START_ROW == 0)
  {
    return;
  }

  // Calculate the starting column in order to center the title bar's text
  startCol = (MAX_LCD_COLS - strlen(str)) / 2;

  // Draw the text one line before the header
  drawText(str, SCREEN_HEADER_START_ROW - 1, startCol);
#endif
}

/***************************************************************************//**
 * @brief
 *    Display a title bar centered at the top of the screen in the following
 *    format. This function is really the same as drawTextHeader() except that
 *    the header this time has two lines of text.
 *    ---------------------------------
 *           Header Text Line 1
 *           Header Text Line 2
 *    ---------------------------------
 *
 * @param[in] str1
 *    The string to display. The full string will only be displayed if less
 *    than 21 characters. Input to this function is assumed to be one line
 *    in length
 *
 * @param[in] str2
 *    The string to display. The full string will only be displayed if less
 *    than 21 characters. Input to this function is assumed to be one line
 *    in length
 *
 * @details
 *    The default starting row to display the text can be changed with the
 *    SCREEN_HEADER_START_ROW macro
 ******************************************************************************/
void drawTextHeaderTwoLines(uint8_t *str1, uint8_t *str2)
{
  // Calculate the starting columns in order to center the title bar's text
  uint8_t startCol1 = (MAX_LCD_COLS - strlen(str1)) / 2;
  uint8_t startCol2 = (MAX_LCD_COLS - strlen(str2)) / 2;

  // Display the text
  drawText(DASHED_LINE, SCREEN_HEADER_START_ROW, 0);
  drawText(str1, SCREEN_HEADER_START_ROW + 1, startCol1);
  drawText(str2, SCREEN_HEADER_START_ROW + 3, startCol2); // Note: skips a line for readability
  drawText(DASHED_LINE, SCREEN_HEADER_START_ROW + 4, 0);
}

/***************************************************************************//**
 * @brief
 *    Display a title bar centered at the top of the screen in the following
 *    format:
 *    ---------------------------------
 *               Header Text
 *    ---------------------------------
 *
 * @param[in] str
 *    The string to display. The full string will only be displayed if less
 *    than 21 characters. Input to this function is assumed to be one line
 *    in length
 *
 * @details
 *    The default starting row to display the text can be changed with the
 *    SCREEN_HEADER_START_ROW macro
 ******************************************************************************/
void drawTextHeader(uint8_t *str)
{
  // Calculate the starting column in order to center the title bar's text
  uint8_t startCol = (MAX_LCD_COLS - strlen(str)) / 2;

  // Display the text
  drawText(DASHED_LINE, SCREEN_HEADER_START_ROW, 0);
  drawText(str, SCREEN_HEADER_START_ROW + 1, startCol);
  drawText(DASHED_LINE, SCREEN_HEADER_START_ROW + 2, 0);
}

/***************************************************************************//**
 * @brief
 *    Display text at the bottom of the screen in the format shown below. Note
 *    that a second dashed line is not drawn below the text to save space:
 *    ---------------------------------
 *               Footer Text
 *
 * @param[in] str
 *    The string to display
 *
 * @details
 *    This function assumes that the user will pass in a string in the format
 *    shown below with one or more '\n' linefeeds. See the @note
 *
 * @details
 *    The default starting row to display the text can be changed with the
 *    SCREEN_FOOTER_START_ROW macro.
 *
 * @note
 *    Since this function uses strtok() and strtok() modifies its input string,
 *    the str argument must be declared in a memory space that is modifiable.
 *    Meaning, the string passed in to the drawTextBody() function cannot be
 *    declared in the CODE segment or be a string literal since string literals
 *    are stored in a read-only memory segment (for most compilers at least).
 *
 *    Recommended usage (use the XDATA segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_XDATA)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't use the CODE segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_CODE)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't pass string literals to this function):
 *      drawTextBody("First Line.\nSecond Line.");
 ******************************************************************************/
void drawTextFooter(uint8_t *str)
{
  uint8_t i;
  uint8_t *token;
  const uint8_t delimiter[] = "\n";

  // Display the dashed line separating the footer text from the body text
  drawText(DASHED_LINE, SCREEN_FOOTER_START_ROW, 0);

  token = strtok(str, delimiter);
  i = 1;
  while (token != 0)
  {
    drawText(token, SCREEN_FOOTER_START_ROW + i, 0);
    token = strtok(0, delimiter);
    i += 1;
  }
}

/***************************************************************************//**
 * @brief
 *    Display text in the body (middle) of the screen with each '\n' separated
 *    string at the same specified column
 *
 * @param[in] str
 *    The string to display.
 *
 * @param[in] col
 *    The column to display all of the '\n' separated strings at
 *
 * @details
 *    This function assumes that the user will pass in a string in the format
 *    shown below with one or more '\n' linefeeds. See the @note
 *
 * @details
 *    The default starting row to display the text can be changed with the
 *    SCREEN_BODY_START_ROW macro. The default line spacing can be changed
 *    with LINE_SPACING macro.
 *
 * @note
 *    Since this function uses strtok() and strtok() modifies its input string,
 *    the str argument must be declared in a memory space that is modifiable.
 *    Meaning, the string passed in to the drawTextBody() function cannot be
 *    declared in the CODE segment or be a string literal since string literals
 *    are stored in a read-only memory segment (for most compilers at least).
 *
 *    Recommended usage (use the XDATA segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_XDATA)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't use the CODE segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_CODE)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't pass string literals to this function):
 *      drawTextBody("First Line.\nSecond Line.");
 ******************************************************************************/
void drawTextBodyAtCol(uint8_t *str, uint8_t col)
{
  uint8_t i;
  uint8_t *token;
  const uint8_t delimiter[] = "\n";

  token = strtok(str, delimiter);
  i = 0;
  while (token != 0)
  {
    drawText(token, SCREEN_BODY_START_ROW + i, col);
    token = strtok(0, delimiter);
    i += LINE_SPACING;
  }
}

/***************************************************************************//**
 * @brief
 *    Display text in the body (middle) of the screen
 *
 * @param[in] str
 *    The string to display
 *
 * @details
 *    This function assumes that the user will pass in a string in the format
 *    shown below with one or more '\n' linefeeds. See the @note
 *
 * @details
 *    The default starting row to display the text can be changed with the
 *    SCREEN_BODY_START_ROW macro. The default line spacing can be changed
 *    with LINE_SPACING macro.
 *
 * @note
 *    Since this function uses strtok() and strtok() modifies its input string,
 *    the str argument must be declared in a memory space that is modifiable.
 *    Meaning, the string passed in to the drawTextBody() function cannot be
 *    declared in the CODE segment or be a string literal since string literals
 *    are stored in a read-only memory segment (for most compilers at least).
 *
 *    Recommended usage (use the XDATA segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_XDATA)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't use the CODE segment to store the input string):
 *      SI_SEGMENT_VARIABLE(text[], uint8_t, SI_SEG_CODE)
 *        = "First Line.\nSecond Line.";
 *      drawTextBody(text);
 *
 *    Won't work (don't pass string literals to this function):
 *      drawTextBody("First Line.\nSecond Line.");
 ******************************************************************************/
void drawTextBody(uint8_t *str)
{
  drawTextBodyAtCol(str, 0);
}

/***************************************************************************//**
 * @brief
 *    Draw a sprite to the specified row and column of the LCD.
 *
 * @note
 *    This function draws the entire sprite and each line drawn will overwrite
 *    anything on the same row(s) as the sprite. This is because the LCD driver
 *    code in render.c only supports drawing entire lines to the LCD. For
 *    example, say you had existing text that occupied rows (10 - 17) and
 *    columns (0 - 11). Even if you specified pixelStartRow = 10 and
 *    pixelStartCol = 15 (so on the same row but starting at a different
 *    column) and the sprite's height was 8 pixels and width was 8 pixels, then
 *    the text would be completely overwritten since the entire line has to be
 *    written.
 *
 * @note
 *    The sprite's width must be divisible by 8, as specified by
 *    RENDER_SpriteLine())
 *
 * @param sprite [in]
 *    The sprite to draw to the LCD
 *
 * @param pixelStartRow [in]
 *    The starting row of the LCD (in units of pixels) to draw the sprite to.
 *    The ending row is the start row + the sprite height.
 *
 * @param pixelStartCol [in]
 *    The starting column of the LCD (in units of pixels) to draw the sprite
 *    to. The end column is the start column + the sprite width.
 ******************************************************************************/
void drawSprite(Sprite_t *sprite, uint8_t pixelStartRow, uint8_t pixelStartCol)
{
  uint8_t r;
  RENDER_ClrLine(line);
  for (r = 0; r < sprite->height; r++)
  {
    RENDER_SpriteLine(line,
                      pixelStartCol,
                      r,
                      sprite->image_bits,
                      sprite->width);
    DISP_WriteLine(pixelStartRow + r, line);
  }
}

/***************************************************************************//**
 * @brief
 *    Draw a row of the sprite image to certain columns of the line buffer
 *
 * @details
 *    This function doesn't actually draw the sprite to the LCD. It simply
 *    renders the sprite to the global line buffer. This function should be used
 *    in conjunction with drawSpriteLineBufferToLcd() to actually draw the
 *    sprite to the LCD screen. Call drawSpriteToLineBuffer() first to setup the
 *    line buffer before calling drawSpriteLineBufferToLcd().
 *
 * @details
 *    This function grants low-level access to image rendering and normally
 *    should not normally have to be touched since the user could just call the
 *    drawSprite() function. This function is mainly useful for when the user is
 *    trying to draw multiple sprites on top of each other (which is not
 *    supported by the drawSprite() function).
 *
 * @param sprite [in]
 *    The sprite to draw to the line buffer
 *
 * @param pixelRowSprite [in]
 *    The row of the sprite (in units of pixels) to write to the line buffer
 *
 * @param pixelStartColLcd [in]
 *    The starting column (in units of pixels) of the line buffer to write to.
 *    The end column is the start column + the sprite width.
 ******************************************************************************/
void drawSpriteToLineBuffer(Sprite_t *sprite,
                            uint8_t pixelRowSprite,
                            uint8_t pixelStartColLcd)
{
  RENDER_SpriteLine(line,
                    pixelStartColLcd,
                    pixelRowSprite,
                    sprite->image_bits,
                    sprite->width);
}

/***************************************************************************//**
 * @brief
 *    Draw the line buffer holding a line of the sprite to a row of the LCD.
 *
 * @details
 *    This function actually draws the sprite to the LCD using the given pixel
 *    row of the global line buffer. This function should be used in conjunction
 *    with drawSpriteToLineBuffer(). Call drawSpriteToLineBuffer() first to
 *    setup the line buffer before calling drawSpriteLineBufferToLcd().
 *
 * @param pixelRow [in]
 *    The row of the LCD to draw to (in units of pixels)
 ******************************************************************************/
void drawSpriteLineBufferToLcd(uint8_t pixelRow)
{
  DISP_WriteLine(pixelRow, line);
}

/***************************************************************************//**
 * @brief
 *    Clear the entire screen to the background color.
 ******************************************************************************/
void eraseScreen(void)
{
  DISP_ClearAll();
}

/***************************************************************************//**
 * @brief
 *    Erase the global line buffer.
 ******************************************************************************/
void eraseLineBuffer(void)
{
  RENDER_ClrLine(line);
}

/***************************************************************************//**
 * @brief
 *    Convert an LCD row number to a pixel number
 *
 * @param rowNum [in]
 *    The row number to convert. LCD row numbers range from (0 - 15) and
 *    therefore the pixel row numbers range from (0 - 128).
 *
 * @return
 *    The LCD row number in pixels
 ******************************************************************************/
uint8_t rowToPixel(uint8_t rowNum)
{
  return rowNum * FONT_HEIGHT;
}

/***************************************************************************//**
 * @brief
 *    Convert an LCD row number to a pixel number
 *
 * @param rowNum [in]
 *    The row number to convert. LCD row numbers range from (0 - 20) and
 *    therefore the pixel row numbers range from (0 - 128).
 *
 * @return
 *    The LCD column number in pixels
 ******************************************************************************/
uint8_t colToPixel(uint8_t colNum)
{
  return colNum * FONT_WIDTH;
}
