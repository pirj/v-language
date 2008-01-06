package v;
import java.util.*;

enum Op {
    OpExit,
    OpEval,
    OpCmd
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
    
    HashMap<String,Object> store = new HashMap<String,Object>();
    Node<Term> n;
    public Cont(Iterator<Term> s, VFrame f, Cont n) {
        stream = s;
        scope = f;
        cont = n;
        op = Op.OpEval;
        sym = null;
        quote = null;
        n = null;
        top = 0;
    }
}

public class Trampoline {
    public static void doeval(Quote q, VFrame scope) {
        Cont now = new Cont(q.tokens().iterator(),scope, null);
        while(now != null) {
            switch (now.op) {
                case OpEval:
                    now = do_one(now);
                    break;
                case OpCmd:
                    if (now.sym.value().equals("if")) {
                        now = ((Cmd)now.quote).trampoline(now);
                    } else {
                        ((Cmd)now.quote).eval(now.scope.child());
                        now = now.cont;
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
        if(!c.stream.hasNext()) {
            return c.cont;
        }
        VStack stack = c.scope.stack();
        stack.push(c.stream.next());
        // if we cant do it, then return ourselves.
        if (!cando(stack)) {
            return c;
        }
        // pop the first token in the stack
        Token sym = stack.pop();
        Quote mq = validq(sym, c.scope);
        // Invoke the quote on our quote by passing
        // us as the parent.
        try {
            // is that a Cmd?
            if (mq instanceof v.Cmd) {
                Cont cnew = new Cont(c.stream, c.scope, c);
                // TODO: the below should go away.
                cnew.sym = sym;
                cnew.quote = mq;

                cnew.op = Op.OpCmd;
                return cnew;
            } else {
                // else return a continuation
                return new Cont(mq.tokens().iterator(), c.scope.child() , c);
            }
        } catch (VException e) {
            e.addLine(sym.value());
            throw e;
        }
    }
}
