package com.aa.pdftoepub;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;

import static com.aa.pdftoepub.Logger.debug;
import static java.lang.String.format;

public class PageConverter {
    public final float RIGHT_MARGIN_FOR_HORIZONTAL_NEW_LINES = 0.8f;
    public final float BIG_FONT_SIZE_THRESHOLD = 14;

    private final PDPage page;

    private StringBuilder sb;
    private float currentX = 0f;
    private float currentY = 0f;
    private float currentFontSize = 0f;
    private String lastText;

    public PageConverter(PDPage page) {
        this.page = page;
    }

    public StringBuilder convert() {
        debug(format("Page: width=%s, height=%s", width(), height()));

        page.getContentStreams().forEachRemaining(this::convertStream);

//        page.getResources().getXObjectNames().forEach(xObjectName -> {
//            Logger.debug("resource: ${xObjectName.name}");
//            var pdxObject = page.getResources().getXObject(xObjectName);
//        });

        return sb;
    }

    private void convertStream(PDStream pdStream) {
        try {
            sb = new StringBuilder();
            PDFStreamParser parser = new PDFStreamParser(pdStream.toByteArray());
            var tokens = parser.parse();
            for (var i = 0; i < tokens.size(); i++) {
                Object token = tokens.get(i);
                debug(token);
                if (token instanceof Operator operator) {
                    switch (operator.getName()) {
                        case Operators.BT -> {
                            debug("BT: ");
                            currentX = 0f;
                            currentY = 0f;
                            currentFontSize = 0f;
                            sb.append("<p>\n");
                        }

                        case Operators.ET -> {
                            debug("ET: ");
                            if (currentFontSize >= BIG_FONT_SIZE_THRESHOLD) {
                                sb.append("</span>");
                            }
                            sb.append("\n</p>\n");
                        }

                        case Operators.g -> {
                            var value = parseNumber(tokens.get(i - 1));
                            debug(format("g: %s", value));
                        }

                        case Operators.G -> {
                            var value = parseNumber(tokens.get(i - 1));
                            debug(format("G: %s", value));
                        }

                        case Operators.rg -> {
                            var r = parseNumber(tokens.get(i - 3));
                            var g = parseNumber(tokens.get(i - 2));
                            var b = parseNumber(tokens.get(i - 1));
                            debug(format("rg: rgb=(%s, %s, %s)", r, g, b));
                        }

                        case Operators.RG -> {
                            var r = parseNumber(tokens.get(i - 3));
                            var g = parseNumber(tokens.get(i - 2));
                            var b = parseNumber(tokens.get(i - 1));
                            debug(format("RG: rgb=(%s, %s, %s)", r, g, b));
                        }

                        case Operators.Td -> {
                            var x = parseNumber(tokens.get(i - 2));
                            var y = parseNumber(tokens.get(i - 1));
                            debug(format("Td: x=%s, y=%s", x, y));
                            if (Utils.isZero(currentX) && Utils.isZero(currentY)) {
                                currentX = x;
                                currentY = y;
                            } else {
                                sb.append("<br>\n".repeat(Math.max(0, newLines(x, y))));
                            }
                        }

                        case Operators.Tf -> {
                            var fontName = ((COSName)tokens.get(i - 2)).getName();
                            var fontSize = parseNumber(tokens.get(i - 1));
                            debug(format("Tf: Font=%s, size=%s", fontName, fontSize));
                            debug(format("    currentFontSize=%s", currentFontSize));
                            if (fontSize >= BIG_FONT_SIZE_THRESHOLD && currentFontSize < BIG_FONT_SIZE_THRESHOLD) {
                                sb.append("<span class=\"FONT-BIG\">");
                            } else if (fontSize < BIG_FONT_SIZE_THRESHOLD && currentFontSize >= BIG_FONT_SIZE_THRESHOLD) {
                                sb.append("</span>");
                            }
                            currentFontSize = fontSize;
                        }

                        case Operators.Tj ->{
                            var textArray = (COSArray)tokens.get(i - 1);
                            lastText = parseTextArray(textArray);
                            debug(format("Tj: %s", lastText));
                            sb.append(lastText);
                        }

                        case Operators.TJ -> {
                            var textArray = (COSArray)tokens.get(i - 1);
                            lastText = parseTextArray(textArray);
                            debug(format("TJ: %s", lastText));
                            sb.append(lastText);
                        }

                        case Operators.Tm -> {
                            var r = parseNumber(tokens.get(i - 5));
                            var g = parseNumber(tokens.get(i - 4));
                            var b = parseNumber(tokens.get(i - 3));
                            var x = parseNumber(tokens.get(i - 2));
                            var y = parseNumber(tokens.get(i - 1));
                            debug(format("Tm: r=%s, g=%s, b=%s, x=%s, y=%s", r, g, b, x, y));
                            var calculatedX = x - currentX;
                            var calculatedY = y - currentY;
                            sb.append("<br>\n".repeat(Math.max(0, newLines(calculatedX, calculatedY))));
                        }

                        default -> debug(format("Unsupported token %s", token));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private float width() {
        return page.getArtBox().getWidth();
    }

    private float height() {
        return page.getArtBox().getHeight();
    }

    private float parseNumber(Object obj) {
        if (obj instanceof COSFloat cosFloat) {
            return cosFloat.floatValue();
        } else if (obj instanceof COSInteger cosInteger) {
            return cosInteger.floatValue();
        } else {
            throw new RuntimeException(obj + " cannot be parsed as a number.");
        }
    }

    private String parseTextArray(COSArray textArray) {
        var sb = new StringBuilder();
        textArray.forEach(tokenElement -> {
            if (tokenElement instanceof COSString cosString) {
                sb.append(cosString.getString());
            } else if (tokenElement instanceof COSInteger cosInteger) {
                if (cosInteger.intValue() < -100) {
                    sb.append(" ");
                }
            }
        });
        return sb.append(" ").toString();
    }

    private int newLines(float x, float y) {
        debug(format("newLines: x=%s, y=%s, currentX=%s, currentY=%s, currentFontSize=%s", x, y, currentX, currentY, currentFontSize));

        var verticalNewLines = -y / currentFontSize;
        debug(format("newLines: %s=", verticalNewLines));
        var verticalNewLinesInt = verticalNewLines < 2 ? 0 : 2;
        debug(format("newLines: verticalNewLinesInt=%s", verticalNewLinesInt));

        var horizontalNewLinesInt = 0;
        if (lastText != null) {
            var lastTextWidth = lastText.length() * currentFontSize;
            debug(format("newLines: lastTextWidth=%s", lastTextWidth));
            var textEndPosition = currentX + x + lastTextWidth;
            debug(format("newLines: textEndPosition=%s, pageWidth:%s", textEndPosition, width()));
            if (textEndPosition < width() * RIGHT_MARGIN_FOR_HORIZONTAL_NEW_LINES) {
                horizontalNewLinesInt = 1;
            }
        }
        debug(format("newLines: horizontalNewLinesInt=%s", horizontalNewLinesInt));

        currentY += y;
        currentX += x;

        return verticalNewLinesInt + horizontalNewLinesInt;
    }
}
