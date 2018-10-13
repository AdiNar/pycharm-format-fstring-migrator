package main.adinar;

import java.util.Map;

import junit.framework.TestCase;

public class MigratorUtilsTest extends TestCase {
    public void testSingleIteration() {
        MigratorUtils mu = new MigratorUtils(
                "costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
        );

        String format = mu.getFirstFormat();
        
        assertEquals("costam abcdef f", mu.result.toString());
        assertEquals("'abe {aa} {bb}'", format);
        assertEquals("(aa=cc, bb=ewq) abcdefgh", mu.buffer);

        String arguments = mu.getArgumentsWithoutBrackets();

        assertEquals("aa=cc, bb=ewq", arguments);
        assertEquals(" abcdefgh", mu.buffer);

        Map<String, String> argumentsMap = mu.parseArguments(arguments);

        assertEquals(" abcdefgh", mu.buffer);
        assertEquals("cc", argumentsMap.get("aa"));
        assertEquals("ewq", argumentsMap.get("bb"));


        mu.applyArgumentsToFormat(argumentsMap, format);

        assertEquals("costam abcdef f'abe {cc} {ewq}'", mu.result.toString());
    }

    public void testBasic() {
        MigratorUtils mu = new MigratorUtils(
                "costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
        );

        assertEquals("costam abcdef f'abe {cc} {ewq}' abcdefgh", mu.getResultReplace());
    }

    public void testMultipleFormats() {
        MigratorUtils mu = new MigratorUtils(
                "costam abcdef 'abe {aa} {bb}'.format(aa=cc, bb=ewq) abcdefgh"
                        + "aaaa \"{ee} {ff}\".format(ee=ciastko, ff=blabla)"
        );

        assertEquals("costam abcdef f'abe {cc} {ewq}' abcdefgh"
                + "aaaa f\"{ciastko} {blabla}\"", mu.getResultReplace());
    }

    public void testMultipleFormats2() {
        MigratorUtils mu = new MigratorUtils(
                "costam abcdef \"abe {aa} {bb}\".format(aa=cc, bb=ewq) abcdefgh"
                        + "aaaa '{ee} {ff}'.format(ee=ciastko, ff=blabla)"
        );

        assertEquals("costam abcdef f\"abe {cc} {ewq}\" abcdefgh"
                + "aaaa f'{ciastko} {blabla}'", mu.getResultReplace());
    }


    public void testComplex() {
        MigratorUtils mu = new MigratorUtils(
                "now print some complex stuff 'abe {self.ciastko} {other.run()}'"
                        + ".format(self=slf, other=fun()) "
                        + "abcdefgh"
        );

        assertEquals("now print some complex stuff f'abe {slf.ciastko} {fun().run()}' abcdefgh",
                mu.getResultReplace());
    }
}
