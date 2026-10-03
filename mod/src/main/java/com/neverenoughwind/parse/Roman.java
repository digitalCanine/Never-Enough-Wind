package com.neverenoughwind.parse;

public final class Roman {
    private Roman() {}

    // 0 for anything that isnt a roman numeral
    public static int toInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = value(s.charAt(i));
            if (v == 0) return 0;
            int next = i + 1 < s.length() ? value(s.charAt(i + 1)) : 0;
            total += v < next ? -v : v;
        }
        return total;
    }

    public static String of(int n) {
        if (n <= 0) return "";
        String[] sym = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] val = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < val.length; i++) {
            while (n >= val[i]) {
                sb.append(sym[i]);
                n -= val[i];
            }
        }
        return sb.toString();
    }

    private static int value(char c) {
        return switch (c) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
        };
    }
}
