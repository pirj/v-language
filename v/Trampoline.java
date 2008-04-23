package v;
import java.util.*;

enum Op {
    OpExit,
    OpEval,
    OpCmd,
    OpX
};

class Cont {
    public Iterator<Term> stream;
    public VFrame scope;
    public Cont cont;
    // current operation.
    Op op;

    // current operation in child classes.
    int top;

    // the below should go off TODO:.
    Token sym;
    Quote quote;
    Quote cmd;

    VException e;
    
    HashMap<String,Object> store = new HashMap<String,Object>();
    Node<Term> n;
    public Cont(Quote q, VFrame f, Cont n) {
        scope = f;
        cont = n;
        op = Op.OpEval;
        // TODO: remove quote.
        quote = q;
        cmd = q;
        if (quote.tokens() != null)
            stream = quote.tokens().iterator();
        else 
            stream = null;
        sym = null;
        n = null;
        top = 0;
    }
    public boolean hasNext() {
        if (stream == null)
            return false;
        return stream.hasNext();
    }

    public Term next() {
        if (stream == null)
            return null;
        return stream.next();
    }
}

public class Trampoline {
    public static void doeval(Quote q, VFrame scope) {
        Cont now = new Cont(q,scope, null);
        cont(now);
    }

    public static void cont(Cont now) {
        while(now != null) {
            switch (now.op) {
                case OpEval:
                    now = do_one(now);
                    break;
                case OpCmd:
                    now = ((Cmd)now.cmd).trampoline(now);
                    break;
                case OpX:
                    if (now.sym == null) { // enclosing quote
                        // is now available?
                        if (now.cont == null)
                            throw now.e;
                        now.cont.e = now.e;
                        now = now.cont;
                        now.op = Op.OpX;
                    } else if( now.sym.value().equals("catch")) {
                        now.op = Op.OpCmd; // return to evaluation, but this time we go for the catch clause.
                        now.e.addLine(now.sym.value());
                    } else { // comes if the symbol is undefined.
                        // save e
                        VException e = now.e;
                        e.addLine(now.sym.value());
                        now = now.cont;
                        now.e = e;
                        now.op = Op.OpX;
                    }
                    break;
                case OpExit:
                    now = null;
                    break;
            }
        }
    }

    private static boolean cando(VStack stack) {
        if (stack.empty())
            return false;
        if (stack.peek().type == Type.TSymbol)
            return true;
        return false;
    }


    private static Quote validq(Token sym, VFrame scope) {
        Quote q = scope.lookup(sym.value());
        if (q == null)
            throw new VException("err:undef_symbol"
                    ,sym, sym.value());
        return q;
    }

    public static Cont do_one(Cont c) {
        if(!c.hasNext()) {
            return c.cont;
        }
        VStack stack = c.scope.stack();
        stack.push(c.next());
        // if we cant do it, then return ourselves.
        if (!cando(stack)) {
            return c;
        }
        // pop the first token in the stack
        Token sym = stack.pop();
        try {
            Quote mq = validq(sym, c.scope);
            // Invoke the quote on our quote by passing
            // us as the parent.
            // is that a Cmd?
            if (mq instanceof v.Cmd) {
                Cont cnew = new Cont(mq, c.scope, c);

                // TODO: the below should go away.
                cnew.sym = sym;

                cnew.op = Op.OpCmd;
                return cnew;
            } else {
                // else return a continuation
                return new Cont(mq, c.scope.child() , c);
            }
        } catch (VException e) {
            e.addLine(sym.value());
            QuoteStream qs = new QuoteStream();
            qs.add((Term)sym);
            Cont cx = new Cont(new CmdQuote(qs), c.scope, c);
            cx.sym = sym;
            cx.op = Op.OpX;
            cx.e = e;
            return cx;
        }
    }
}
