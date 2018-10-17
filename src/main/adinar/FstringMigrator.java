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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.Nullable;

public class FstringMigrator {
    private static final String FORMAT_1 = "\".format(";
    private static final String FORMAT_2 = "'.format(";
    String buffer;
    StringBuffer result;
    private FormatInfo firstFormatInfo;
    private String firstFormat;

    public FstringMigrator(String selection) {
        this.buffer = selection;
        result = new StringBuffer();
    }

    public String getResultReplace() {
        while (!buffer.isEmpty()) {
            firstFormat = getFirstFormat();
            if (firstFormat == null) break;
            String argumentsRaw = getArgumentsWithoutBrackets();
            Arguments mapArguments = parseArguments(argumentsRaw);
            applyArgumentsToFormat(mapArguments, firstFormat);
        }

        return result.toString();
    }

    void applyArgumentsToFormat(Arguments arguments, String text) {
        for (Map.Entry<String, String> entry : arguments.map.entrySet()) {
            text = fillAllFormatsWithEntry(text, entry, this::textReplace);
        }

        Map<String, String> emptyHandler = new HashMap<>();

        for (String arg : arguments.list) {
            emptyHandler.put("", arg);
            Map.Entry<String, String> entryElement = emptyHandler.entrySet().iterator().next();
            text = fillAllFormatsWithEntry(text, entryElement, this::textReplaceFirst);
        }

        result.append(text);
    }

    private String textReplace(TextReplaceArgs args) {
        return args.text.replace(args.template, args.data);
    }

    private String textReplaceFirst(TextReplaceArgs args) {
        return StringUtils.replaceOnce(args.text, args.template, args.data);
    }

    private String fillAllFormatsWithEntry(String text, Map.Entry<String,String> entry,
            Function<TextReplaceArgs, String> fun) {
        text = fillWithEntry(text, "{%s.", entry, fun);
        text = fillWithEntry(text, "{%s!", entry, fun);
        return fillWithEntry(text, "{%s}", entry, fun);
    }

    private String fillWithEntry(String text, String format, Map.Entry<String, String> entry,
            Function<TextReplaceArgs, String> fun) {
        return fun.apply(new TextReplaceArgs(text, String.format(format, entry.getKey()),
                String.format(format, entry.getValue())));
    }

    Arguments parseArguments(String argumentsRaw) {
        Map<String, String> argumentsMap = new HashMap<>();
        List<String> argumentsList = new ArrayList<>();
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
            switch (vals.length) {
            case 1:
                argumentsList.add(vals[0].trim());
                break;
            case 2:
                argumentsMap.put(vals[0].trim(), vals[1].trim());
                break;
            default:
                assert false;  // Seems like syntax error
            }
        }

        Arguments result = new Arguments(argumentsMap, argumentsList);
        result.addNumberedArgsToMapped();

        return result;
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
        if (firstFormatInfo == null || firstFormatInfo.startPos < 0) {
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

    private FormatInfo getFirstFormatInfo() {
        char quoteType = '"';
        int formatEnd = buffer.indexOf(FORMAT_1);
        int formatEnd2 = buffer.indexOf(FORMAT_2);
        if (formatEnd < 0 || (formatEnd2 >= 0 && formatEnd > formatEnd2)) {
            formatEnd = buffer.indexOf(FORMAT_2);
            quoteType = '\'';
        }
        int lastCharBeforeClosingQuotePos = formatEnd - 1;

        if (lastCharBeforeClosingQuotePos < 0) return null;

        String prefixWithFormat = buffer.substring(0, lastCharBeforeClosingQuotePos);
        Pattern openingQuotePattern = Pattern.compile(".*(?<!\\\\)" + quoteType);
        Matcher matcher = openingQuotePattern.matcher(prefixWithFormat);
        int formatStart = 0;
        if (matcher.find()) {
            formatStart = matcher.group(0).length() - 1;
        }

        return new FormatInfo(formatStart, formatEnd, quoteType);
    }

    private class FormatInfo {
        final int startPos;

        public FormatInfo(int startPos, int endPos, char quoteType) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.quoteType = quoteType;
        }

        final int endPos;
        final char quoteType;

        public int getFormatStartWithoutQuotePos() {
            return startPos + 1;
        }

        public int getAfterFormatWordPos() {
            assert FORMAT_1.length() == FORMAT_2.length();
            return endPos + FORMAT_1.length() - 1; // Minus one so the closing bracket will be on the right
        }
    }

    class Arguments {
        final Map<String, String> map;
        final List<String> list;

        private Arguments(Map<String, String> map, List<String> list) {
            this.map = map;
            this.list = list;
        }

        // Handles cases like '{0} {1}'.format(first, second)
        public void addNumberedArgsToMapped() {
            for (int i=0; i<list.size(); i++) {
                map.put(String.valueOf(i), list.get(i));
            }
        }
    }

    class TextReplaceArgs {
        final String text;
        final String template;

        public TextReplaceArgs(String text, String template, String data) {
            this.text = text;
            this.template = template;
            this.data = data;
        }

        final String data;
    }
}
