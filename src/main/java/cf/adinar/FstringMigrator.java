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

package cf.adinar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FstringMigrator {
    private static final String DOUBLE_QUOTE_FORMAT = "\".format(";
    private static final String SINGLE_QUOTE_FORMAT = "'.format(";
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
                throw new RuntimeException("Not supported expression: " + arg);
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
        String format = buffer.substring(firstFormatInfo.startPos, firstFormatInfo.endPos + 1);
        buffer = buffer.substring(firstFormatInfo.getPosOfOpeningBracketAfterWordFormat());
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
        QuoteOccurence singleQuote = new QuoteOccurence(buffer, SINGLE_QUOTE_FORMAT, '\'');
        QuoteOccurence doubleQuote = new QuoteOccurence(buffer, DOUBLE_QUOTE_FORMAT, '"');
        QuoteOccurence firstQuote = singleQuote.compareTo(doubleQuote) < 0 ? singleQuote : doubleQuote;
        if (firstQuote.pos < 0) return null;
        assert buffer.charAt(firstQuote.pos) == firstQuote.quoteChar;

        String prefixWithFirstFormat = buffer.substring(0, firstQuote.pos + 1);
        String groupWithTextBeforeFormatStringRegex = "[\\s\\S]*";
        String lastNotEscapedQuoteRegex = String.format("(?<!\\\\)%c", firstQuote.quoteChar);
        Pattern openingQuotePattern = Pattern.compile(String.format("(%s)(%s[\\s\\S]*)%c",
                        groupWithTextBeforeFormatStringRegex, lastNotEscapedQuoteRegex, firstQuote.quoteChar));
        Matcher matcher = openingQuotePattern.matcher(prefixWithFirstFormat);
        int formatStart = 0;
        if (matcher.find()) {
            formatStart = getLengthOfTextBeforeFormatString(matcher);
        }

        return new FormatInfo(formatStart, firstQuote.pos, firstQuote.quoteChar);
    }

    private int getLengthOfTextBeforeFormatString(Matcher matcher) {
        return matcher.group(1).length();
    }

    private static class QuoteOccurence implements Comparable<QuoteOccurence> {
        final int pos;
        final char quoteChar;

        public QuoteOccurence(String buffer, String formatTemplate, char quoteChar) {
            this.pos = buffer.indexOf(formatTemplate);
            this.quoteChar = quoteChar;
        }

        public boolean exists() {
            return pos >= 0;
        }

        @Override
        public int compareTo(@NotNull QuoteOccurence o) {
            if (o.exists() && o.pos < this.pos) return 1;
            return -1;
        }

        @Override
        public String toString() {
            return String.format("Quote %c at position %s, %s exists", quoteChar, pos, exists() ? "does" : "does not");
        }
    }

    private static class FormatInfo {
        final int startPos;

        public FormatInfo(int startPos, int endPos, char quoteType) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.quoteType = quoteType;
        }

        final int endPos;
        final char quoteType;

        public int getPosOfOpeningBracketAfterWordFormat() {
            assert DOUBLE_QUOTE_FORMAT.length() == SINGLE_QUOTE_FORMAT.length();
            return endPos + DOUBLE_QUOTE_FORMAT.length() - 1; // Bracket is included in FORMAT string
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
