package de.igslandstuhl.database.utils;

public class StringUtils {
    private StringUtils() {}
    public static String fitSize(String str, int size) {
        if (str.length() > size) {
            return str.substring(0, size-3) + "...";
        } else {
            return str + " ".repeat(size - str.length());
        }
    }
}
