package v;

import java.util.*;



public class V {
    public static String version = "0.005";

    public static boolean singleassign = false;

    static void banner() {
        outln("\tV\t");
    }

    static boolean _showtime = false;

    public static void showtime(boolean val) {
        _showtime = val;
    }



    public static void main(final String[] args) {
        long start = System.currentTimeMillis();
        final VFrame frame = new VFrame(); // our frame chain.
        for(String s : args)
            frame.stack().push(new Term<String>(Type.TString, s));
        final boolean interactive = args.length == 0 ? true : false;
        // Setup the world quote.

        try {
            Prologue.init(frame);
            // do we have any args?
            CharStream cs = null;
            if (args.length > 0) {
                debug("Opening:" + args[0]);
                cs = new FileCharStream(args[0]);
            } else {
                banner();
                cs = new ConsoleCharStream();
            }

            CmdQuote program = new CmdQuote(new LexStream(cs));
            do {
                try {
                    Trampoline.doeval(program,frame.child()); // we save the original defs.
                } catch (VException e) {
                    outln(">" + e.message());
                    frame.dump();
                    //debug(e);
                } 
            } while (interactive);
            if (_showtime)
                outln("time: " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            outln("*>" + e.getMessage());
            frame.dump();
            debug(e);
        }
    }

    public static void outln(String var) {
        System.out.println(var);
    }

    public static void out(String var) {
        System.out.print(var);
    }

    public void outln(Term term) {
        outln(term.value());
    }

    public static void debug(Exception e) {
        //if (_debug) {
            outln(e.getMessage());
            e.printStackTrace();
        //}
    }
    public static void debug(String s) {
        if (_debug) outln(s);
    }

    static boolean _debug = false;
    static void debug(boolean val) {
        _debug = val;
    }
}
