package com.aa.pdftoepub;

public class Utils {
    public static boolean isZero(float number) {
        return number < 0.01 && number > -0.01;
    }
}
