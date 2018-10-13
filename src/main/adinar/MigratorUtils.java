/*
   Copyright 2018 Adrian Naruszko

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package main.adinar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

public class MigratorUtils {
    private static final String FORMAT_1 = "\".format(";
    private static final String FORMAT_2 = "'.format(";
    String buffer;
    StringBuffer result;
    private FormatInfo firstFormatInfo;
    private String firstFormat;

    public MigratorUtils(String selection) {
        this.buffer = selection;
        result = new StringBuffer();
    }

    public String getResultReplace() {
        while (!buffer.isEmpty()) {
            firstFormat = getFirstFormat();
            if (firstFormat == null) break;
            String argumentsRaw = getArgumentsWithoutBrackets();
            Map<String, String> arguments = parseArguments(argumentsRaw);
            applyArgumentsToFormat(arguments, firstFormat);
        }

        return result.toString();
    }

    void applyArgumentsToFormat(Map<String, String> arguments, String format) {
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            format = format.replace(String.format("{%s}", entry.getKey()),
                                    String.format("{%s}", entry.getValue()))
                           .replace(String.format("{%s.", entry.getKey()),
                                    String.format("{%s.", entry.getValue()))
                           .replace(String.format("{%s!", entry.getKey()),
                                    String.format("{%s!", entry.getValue()));
        }

        result.append(format);
    }

    Map<String,String> parseArguments(String argumentsRaw) {
        Map<String, String> map = new HashMap<>();
        int cnt = 0, start = 0, end = 0;
        List<String> list = new ArrayList<>();
        for (char c : argumentsRaw.toCharArray()) {
            if (c == '(') cnt++;
            if (c == ')') cnt--;

            if (c == ',' && cnt == 0) {
                list.add(argumentsRaw.substring(start, end));
                start = end + 1;
            }

            end++;
        }

        if (start != end - 1) {
            list.add(argumentsRaw.substring(start));
        }

        for (String arg : list) {
            String[] vals = arg.split("=");
            assert vals.length == 2;
            // vals[1] = adjustValIfStringType(vals[1]);
            map.put(vals[0].trim(), vals[1].trim());
        }

        return map;
    }

    private String adjustValIfStringType(String val) {
        String res = replaceQuoteTypeIfNeededAndPossible(val, '\'', '"');
        if (res == null) {
            res = replaceQuoteTypeIfNeededAndPossible(val, '"', '\'');

            if (res == null) {
                String valWithoutQuotes = val.substring(1, val.length() - 1);
                return String.format("\\%c%s\\%c",
                        firstFormatInfo.quoteType, valWithoutQuotes,
                        firstFormatInfo.quoteType);
            }
        }

        return res;
    }

    @Nullable
    private String replaceQuoteTypeIfNeededAndPossible(String val, char currentChar, char wantedChar) {
        char[] cv = val.toCharArray();
        if (cv[0] == currentChar && firstFormatInfo.quoteType == currentChar) {
            if (!val.contains(String.valueOf(wantedChar))) {
                cv[0] = wantedChar;
                cv[cv.length - 1] = wantedChar;
            } else {
                return null;
            }
        }

        return new String(cv);
    }

    String getFirstFormat() {
        firstFormatInfo = getFirstFormatInfo();
        if (firstFormatInfo.startPos < 0) {
            result.append(buffer);
            return null; // No data left
        }
        result.append(buffer, 0, firstFormatInfo.startPos);
        result.append("f");
        String format = String.format("%c%s%c",
                firstFormatInfo.quoteType, buffer.substring(firstFormatInfo.getFormatStartWithoutQuotePos(),
                        firstFormatInfo.endPos), firstFormatInfo.quoteType);
        buffer = buffer.substring(firstFormatInfo.getAfterFormatWordPos());
        return format;
    }

    private String getArguments() {
        int pos = 0, cnt = 0;
        boolean wasOpened = false;

        while (cnt > 0 || !wasOpened) {
            if (buffer.charAt(pos) == '(') {
                cnt++;
                wasOpened = true;
            }
            if (buffer.charAt(pos) == ')') cnt--;
            pos++;
        }

        String result = buffer.substring(0, pos);
        buffer = buffer.substring(pos);
        return result;
    }

    String getArgumentsWithoutBrackets() {
        String str = getArguments();
        return str.substring(1, str.length() - 1);
    }

    private String cleanUpArguments(String args) {
        return args.replace("\\", "");
    }

    private FormatInfo getFirstFormatInfo() {
        char quoteType = '"';
        int formatEnd = buffer.indexOf(FORMAT_1);
        int formatEnd2 = buffer.indexOf(FORMAT_2);
        if (formatEnd < 0 || (formatEnd2 >= 0 && formatEnd > formatEnd2)) {
            formatEnd = buffer.indexOf(FORMAT_2);
            quoteType = '\'';
        }
        int lastCharBeforeClosingQuotePos = formatEnd - 1;
        int formatStart = buffer.lastIndexOf(quoteType, lastCharBeforeClosingQuotePos);

        return new FormatInfo(formatStart, formatEnd, quoteType);
    }

    public char getFormatQuote(String selection) {
        int dbl = selection.indexOf("\"");
        int sngl = selection.indexOf("'");

        if (isSingleQuote(dbl, sngl)) {
            return '\'';
        }

        return '"';
    }

    private int getFormatOpeningPos(String selection) {
        int dbl = selection.indexOf("\"");
        int sngl = selection.indexOf("'");

        if (isSingleQuote(dbl, sngl)) return sngl;
        return dbl;
    }

    private boolean isSingleQuote(int dbl, int sngl) {
        return dbl == -1 || (sngl >= 0 && sngl < dbl);
    }

    private class FormatInfo {
        int startPos, endPos;
        char quoteType;

        public FormatInfo(int startPos, int endPos, char quoteType) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.quoteType = quoteType;
        }

        public int getFormatStartWithoutQuotePos() {
            return startPos + 1;
        }

        public int getAfterFormatWordPos() {
            assert FORMAT_1.length() == FORMAT_2.length();
            return endPos + FORMAT_1.length() - 1; // Minus one so the closing bracket will be on the right
        }
    }
}
