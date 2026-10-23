package com.ibek.ghost;

import android.text.TextUtils;

/**
 * Калькулятор-итого для поля ввода.
 * Пользователь пишет суммы слитно (200000) или через точку как разделитель тысяч (200.000),
 * числа разделяет пробелом/переводом строки (неявный плюс) либо знаками + и -.
 * Возвращаем строку «Итого: 250 000» или null, если считать нечего или это обычный текст.
 */
public class Calc {

    public static String totalLabel(CharSequence textCs) {
        if (textCs == null) {
            return null;
        }
        String text = textCs.toString();
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        // Разрешены только цифры, точки, пробелы/переводы строк и + -.
        // Любой посторонний символ (буква и т.п.) — значит это обычное сообщение, не считаем.
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits++;
                continue;
            }
            if (ch == '.' || ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '+' || ch == '-') {
                continue;
            }
            return null;
        }
        if (digits == 0) {
            return null;
        }

        long sum = 0;
        long cur = 0;
        boolean haveCur = false;
        int sign = 1;
        int numbers = 0;
        boolean sawOperator = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                cur = cur * 10 + (ch - '0');
                haveCur = true;
            } else if (ch == '.') {
                // разделитель тысяч — пропускаем
            } else if (ch == '+' || ch == '-') {
                if (haveCur) {
                    sum += sign * cur;
                    numbers++;
                    cur = 0;
                    haveCur = false;
                }
                sign = (ch == '-') ? -1 : 1;
                sawOperator = true;
            } else {
                // пробел/перевод строки — разделитель чисел (неявный плюс)
                if (haveCur) {
                    sum += sign * cur;
                    numbers++;
                    cur = 0;
                    haveCur = false;
                    sign = 1;
                }
            }
        }
        if (haveCur) {
            sum += sign * cur;
            numbers++;
        }

        // показываем только если реально есть что суммировать: 2+ числа или был знак
        if (numbers == 0 || (numbers < 2 && !sawOperator)) {
            return null;
        }

        return "Итого: " + group(sum);
    }

    /** Разбивка на тройки пробелами: 250000 -> «250 000» */
    private static String group(long v) {
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) {
                sb.append(' ');
            }
        }
        sb.reverse();
        return (neg ? "-" : "") + sb;
    }
}
