package cf.adinar;

import java.util.Map;

import jnr.ffi.annotations.IgnoreError;
import junit.framework.TestCase;
import org.junit.Ignore;

public class FstringMigratorTest extends TestCase {
    public void testFullIteration() {
        FstringMigrator mu = new FstringMigrator(
                "costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
        );

        String format = mu.getFirstFormat();

        // Result is equal to the prefix
        assertEquals("costam abcdef f", mu.result.toString());
        assertEquals("'abe {aa} {bb}'", format);
        assertEquals("(aa=cc, bb=ewq) abcdefgh", mu.buffer);

        String arguments = mu.getArgumentsWithoutBrackets();

        assertEquals("aa=cc, bb=ewq", arguments);
        assertEquals(" abcdefgh", mu.buffer);

        FstringMigrator.Arguments args = mu.parseArguments(arguments);
        Map<String, String> argumentsMap = args.map;

        assertEquals(" abcdefgh", mu.buffer);
        assertEquals("cc", argumentsMap.get("aa"));
        assertEquals("ewq", argumentsMap.get("bb"));


        mu.applyArgumentsToFormat(args, format);

        assertEquals("costam abcdef f'abe {cc} {ewq}'", mu.result.toString());
    }

    public void testBasicNamed() {
        tst("costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh",
                "costam abcdef f'abe {cc} {ewq}' abcdefgh");
    }

    public void testMultipleFormats() {
        tst("costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
                        + "aaaa \"{ee} {ff}\".format(ee=ciastko, ff=blabla)",
                "costam abcdef f'abe {cc} {ewq}' abcdefghaaaa f\"{ciastko} {blabla}\"");
    }

    public void testMultipleFormatsWithNewline() {
        tst("costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
                        + "\n aaaa \"{ee} {ff}\".format(ee=ciastko, ff=blabla)",
                "costam abcdef f'abe {cc} {ewq}' abcdefgh\n aaaa f\"{ciastko} {blabla}\"");
    }

    public void testMultipleFormats2() {
        tst("costam abcdef \"abe {aa} {bb}\".format(aa=cc, bb=ewq) abcdefgh"
                        + "aaaa '{ee} {ff}'.format(ee=ciastko, ff=blabla)",
                "costam abcdef f\"abe {cc} {ewq}\" abcdefghaaaa f'{ciastko} {blabla}'");
    }


    public void testComplex() {
        tst("now print some complex stuff 'abe {self.ciastko} {other.run()}'"
                + ".format(self=slf, other=fun()) abcdefgh",
                "now print some complex stuff f'abe {slf.ciastko} {fun().run()}' abcdefgh");
    }

    public void testFunCall() {
        tst("'Would have send {msg}'.format(msg=msg.as_string())", "f'Would have send {msg.as_string()}'");
    }

    public void testQuotes() {
        tst("sth 'Sending \\'{subject}\\' to {email}'.format(subject=subject, email=email_dest)",
                "sth f'Sending \\'{subject}\\' to {email_dest}'");
    }
    public void testQuotes2() {
        tst("'some text' sth 'Sending \\'{subject}\\' to {email}'.format(subject=subject, email=email_dest)",
                "'some text' sth f'Sending \\'{subject}\\' to {email_dest}'");
    }

    public void testNumbered() {
        tst("'{0} {1} {0}'.format(abc, def)", "f'{abc} {def} {abc}'");
    }

    public void testNonNamed() {
        tst("'{} {} {}'.format(abc, def, ghi)", "f'{abc} {def} {ghi}'");
    }

//    Newlines are not yet suppported
/*
    public void testFullIterationLineBreak() {
        FstringMigrator mu = new FstringMigrator("'{}'\n'{}'.format(first, second)");

        String format = mu.getFirstFormat();

        // Result is equal to the prefix
        assertEquals("", mu.result.toString());
        assertEquals("'{}'\n'{}'", format);
        assertEquals("(first, second)", mu.buffer);

        String arguments = mu.getArgumentsWithoutBrackets();

        assertEquals("first, second", arguments);
        assertEquals("", mu.buffer);

        FstringMigrator.Arguments args = mu.parseArguments(arguments);
        Map<String, String> argumentsMap = args.map;

        assertEquals(" abcdefgh", mu.buffer);
        assertEquals("cc", argumentsMap.get("aa"));
        assertEquals("ewq", argumentsMap.get("bb"));


        mu.applyArgumentsToFormat(args, format);

        assertEquals("costam abcdef f'abe {cc} {ewq}'", mu.result.toString());
    }

    public void testLineBreak() {
        tst("'{}'\n'{}'.format(first, second)", "f'{first}'\nf'{second}'");
    }
*/

    private void tst(String in, String out) {
        FstringMigrator mu = new FstringMigrator(in);
        assertEquals(out, mu.getResultReplace());
    }
}
