package v;
import java.util.*;
import java.io.*;
import v.java.*;

class Shield {
    // current stack
    Node<Term> stack;
    Quote quote;
    Shield(VStack s, Quote q) {
        stack = s.now;
        quote = q;
    }
};

class LstCmd extends Cmd {
    boolean savestack = false;
    public LstCmd() {
    }

    void savestack() {
        savestack = true;
    }

    final int Start=0,Eval=1,Exit=2;
    public Cont trampoline(Cont c) {
        VFrame q = c.scope;
        VStack p = q.stack();

        switch (c.top) {
            case Start:
                return start(c);
            case Eval:
                if (c.hasNext()) return eval(c);
                else return last(c);
            case Exit:
            default:
                return exit(c);
        }
    }

    Cont start(Cont c) {
        return c;
    }
    Cont eval(Cont c) {
        return c;
    }
    Cont last(Cont c) {
        c.top = Exit;
        return c;
    }
    Cont exit(Cont c) {
        return c.cont;
    }
}

public class Prologue {
    private static boolean and(Term a, Term b) {
        return a.bvalue() && b.bvalue();
    }

    private static boolean or(Term a, Term b) {
        return a.bvalue() || b.bvalue();
    }

    private static boolean isGt(Term a, Term b) {
        return a.numvalue().doubleValue()> b.numvalue().doubleValue();
    }

    private static boolean isEq(Term a, Term b) {
        switch(a.type) {
            case TInt:
            case TDouble:
                return a.numvalue().doubleValue() ==  b.numvalue().doubleValue();
            case TString:
                return a.svalue().equals(b.svalue());
            case TSymbol:
                return a.svalue() == b.svalue(); // constant strings
            default:
                return a.value().equals(b.value());
        }
    }

    private static boolean isLt(Term a, Term b) {
        if (isGt(a,b))
            return false;
        if (isEq(a,b))
            return false;
        return true;
    }

    @SuppressWarnings("unchecked")
        static Map.Entry<String, CmdQuote> splitdef(Quote qval) {
            HashMap<String, CmdQuote> map = new HashMap<String, CmdQuote>();
            Iterator<Term> it = (Iterator<Term>)qval.tokens().iterator();
            Term<String> symbol = it.next();

            // copy the rest of tokens to our own stream.
            QuoteStream nts = new QuoteStream();
            while (it.hasNext())
                nts.add(it.next());

            // we define it on the enclosing scope.
            // so our new command's parent is actually q rather than
            // parent.
            map.put(symbol.val, new CmdQuote(nts)); 
            return map.entrySet().iterator().next();
        }

    static QuoteStream evalres(TokenStream res, HashMap<String, Term> symbols) {
        QuoteStream r = new QuoteStream();
        Iterator<Term> rstream = res.iterator();
        while(rstream.hasNext()) {
            Term t = rstream.next();
            switch(t.type) {

                case TQuote:
                    QuoteStream nq = evalres(t.qvalue().tokens(), symbols);
                    r.add(new Term<Quote>(Type.TQuote, new CmdQuote(nq)));
                    break;
                case TSymbol:
                    // do we have it in our symbol table? if yes, replace, else just push it in.
                    String sym = t.svalue();
                    if (symbols.containsKey(sym)) {
                        // does it look like *xxx ?? 
                        if (sym.charAt(0) == '*') {
                            // expand it.
                            Term star = symbols.get(sym);
                            for(Term x : star.qvalue().tokens()) {
                                r.add(x);
                            }
                        } else
                            r.add(symbols.get(sym));
                        break;
                    }
                default:
                    // just push it in.
                    r.add(t);
            }
        }
        return r;
    }

    static void evaltmpl(TokenStream tmpl, TokenStream elem, HashMap<String, Term> symbols) {
        //Take each point in tmpl, and proess elements accordingly.
        Iterator<Term> tstream = tmpl.iterator();
        Iterator<Term> estream = elem.iterator();
        while(tstream.hasNext()) {
            Term t = tstream.next();
            switch (t.type) {
                case TSymbol:
                    try {
                        // _ means any one
                        // * means any including nil unnamed.
                        // *a means any including nil but named with symbol '*a'
                        String value = t.value();
                        if (value.charAt(0) == '_') {
                            // eat one from estream and continue.
                            estream.next();
                            break;
                        } else if (value.charAt(0) == '*') {
                            QuoteStream nlist = new QuoteStream();
                            // * is all. but before we slurp, check the next element
                            // in the template. If there is not any, then slurp. If there
                            // is one, then slurp until last but one, and leave it.
                            if (tstream.hasNext()) {
                                Term tmplterm = tstream.next();
                                Term lastelem = null;

                                // slurp till last but one.
                                while(estream.hasNext()) {
                                    lastelem = estream.next();
                                    if (estream.hasNext())
                                        nlist.add(lastelem);
                                }

                                switch (tmplterm.type) {
                                    case TSymbol:
                                        // assign value in symbols.
                                        symbols.put(tmplterm.svalue(), lastelem);
                                        break;
                                    case TQuote:
                                        evaltmpl(tmplterm.qvalue().tokens(), lastelem.qvalue().tokens(), symbols);
                                        break;
                                    default:
                                        if (tmplterm.value().equals(lastelem.value()))
                                            break;
                                        else
                                            throw new VException("err:view:eq",lastelem,tmplterm.value() + " != "+lastelem.value());
                                }

                            } else {
                                // we can happily slurp now.
                                while(estream.hasNext())
                                    nlist.add(estream.next());
                            }
                            if (value.length() > 1) { // do we have a named list?
                                symbols.put(value, new Term<Quote>(Type.TQuote, new CmdQuote(nlist)));
                            }
                        } else {
                            Term e = estream.next();
                            symbols.put(t.value(), e);
                        }
                        break;
                    } catch (VException e) {
                        e.addLine(t.value());
                        throw e;
                    } catch (Exception e) {
                        throw new VException("err:view:sym",t,t.value() + " " + e.getMessage());
                    }

                case TQuote:
                    // evaluate this portion again in evaltmpl.
                    try {
                        Term et = estream.next();
                        evaltmpl(t.qvalue().tokens(), et.qvalue().tokens(), symbols);
                    } catch (VException e) {
                        e.addLine(t.value());
                        throw e;
                    } catch (Exception e) {
                        throw new VException("err:view:quote",t,t.value() + " " + e.getMessage());
                    }
                    break;
                default:
                    //make sure both matches.
                    Term eterm = estream.next();
                    if (t.value().equals(eterm.value()))
                        break;
                    else
                        throw new VException("err:view:eq",eterm, t.value() + " != " +eterm.value());
            }
        }
    }

    static Cmd _def = new Cmd() {
        public Cont trampoline(Cont c) {
            // eval is passed in the quote representing the current scope.
            VFrame q = c.scope;
            VStack p = q.stack();
            Term t = p.pop();
            Map.Entry<String, CmdQuote> entry = splitdef(t.qvalue());
            String symbol = entry.getKey();

            // we define it on the enclosing scope. because the evaluation
            // is done on child scope.
            V.debug("Def [" + symbol + "] @ " + q.id());
            q.def(symbol, entry.getValue());

            return c.cont;
        }

    };

    static Cmd _me = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            p.push(new Term<VFrame>(Type.TFrame, q));
            return c.cont;
        }
    };

    static Cmd _parent = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            VFrame t = p.pop().fvalue();
            p.push(new Term<VFrame>(Type.TFrame, t.parent()));
            return c.cont;
        }

    };

    static Cmd _defenv = new Cmd() {
        public Cont trampoline(Cont c) {
            // eval is passed in the quote representing the current scope.
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term t = p.pop();
            Map.Entry<String, CmdQuote> entry = splitdef(t.qvalue());
            String symbol = entry.getKey();
            b.fvalue().def(symbol, entry.getValue());
            return c.cont;
        }
    };

    static Cmd _defmodule = new Cmd() {
        final int Start=0,Eval=1,Exit=2;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term t = p.pop();

                        Map.Entry<String, CmdQuote> entry = splitdef(t.qvalue());
                        String module = entry.getKey();
                        c.store.put("defmodule:module", module);
                        CmdQuote qfull = entry.getValue();

                        // split it again to get exported defs. 
                        Iterator<Term> it = (Iterator<Term>)qfull.tokens().iterator();
                        Quote pub = it.next().qvalue();
                        c.store.put("defmodule:pub", pub);

                        QuoteStream nts = new QuoteStream();
                        while (it.hasNext())
                            nts.add(it.next());

                        c.top = Eval;
                        CmdQuote qval = new CmdQuote(nts); 
                        // we define it on the enclosing scope.
                        // so our new command's parent is actually q rather than
                        // parent.
                        Cont cont = new Cont(qval,q, c);
                        return cont;
                    }
                case Eval:
                    {
                        Quote pub = (Quote) c.store.get("defmodule:pub");
                        String module = (String) c.store.get("defmodule:module");
                        // and save the frame in our parents namespace.
                        Term<VFrame> f = new Term<VFrame>(Type.TFrame, q);
                        QuoteStream fts = new QuoteStream();
                        fts.add(f);
                        V.debug("Def :" + module + "@" + q.parent().id());
                        q.parent().def('$' + module, new CmdQuote(fts));

                        // now bind all the published tokens to our parent namespace.
                        Iterator <Term> i = pub.tokens().iterator();
                        while(i.hasNext()) {
                            // look up their bindings and rebind it to parents.
                            String s = i.next().value();
                            Quote libs = Util.getdef('$' + module + '[' + s + "] &i");
                            q.parent().def(module + ':' + s ,libs);
                        }
                        c.top = Exit;
                        return c;
                    }
                case Exit:
                default:
                        // discard our continuation and return the parent.
                        return c.cont;
            }
        }
    };

    // [a b c obj method] java
    static Cmd _java = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            // eval is passed in the quote representing the current scope.
            VStack p = q.stack();
            Term v = p.pop();
            LinkedList<Term> st = new LinkedList<Term>();
            for(Term t: v.qvalue().tokens())
                st.addFirst(t);

            Iterator<Term> i = st.iterator();
            Term method = i.next();
            Term object = i.next();
            QuoteStream qs = new QuoteStream();
            while(i.hasNext())
                qs.add(i.next());
            Term res = Helper.invoke(object, method, new CmdQuote(qs));
            p.push(res);
            return c.cont;
        }
    };

    // a b c [a b c : [a b c]] V
    // [a b c] [[a b c] : a b c] V
    // [a b c] [[a _] : [a a]] V -- _ indicates any value.
    // [a b c] [[a *b] : [a a]] V -- * indicates an addressible list.
    //
    // a b c d e f [a *b : [a b]] V => a b c d [e f] -- we ignore the
    // *x on the first level and treat it as just an element.

    static Cmd _view = new Cmd() { // For now, it is an atomic operation. I am not sure if it is the right choice
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term v = p.pop();
            Iterator<Term> fstream = v.qvalue().tokens().iterator();

            // iterate through the quote, and find where ':' is then split it
            // into two half and analyze the first.
            QuoteStream tmpl = new QuoteStream();
            while (fstream.hasNext()) {
                Term t = fstream.next();
                if (t.type == Type.TSymbol && t.value() == Sym.lookup(":"))
                    break;
                tmpl.add(t);
            }

            QuoteStream res = new QuoteStream();
            while (fstream.hasNext()) {
                Term t = fstream.next();
                res.add(t);
            }

            // collect as much params as there is from stack as there is in the template
            // first level.
            QuoteStream elem = new QuoteStream();
            fstream = tmpl.iterator();
            LinkedList<Term> st = new LinkedList<Term>();
            while (fstream.hasNext()) {
                Term t = fstream.next();
                Term e = p.pop();
                st.addFirst(e);
            }
            for (Term e: st)
                elem.add(e);

            HashMap<String, Term> symbols = new HashMap<String, Term>();
            //Now take each elem and its pair templ and extract the symbols and their meanings.
            evaltmpl(tmpl, elem, symbols);

            // now go over the quote we were just passed and replace each symbol with what we
            // have if we do have a definition.
            QuoteStream resstream = evalres(res, symbols);
            CmdQuote qs = new CmdQuote(resstream);

            Iterator<Term> i = qs.tokens().iterator();
            while (i.hasNext())
                p.push(i.next());
            return c.cont;
        }
    };

    // trans looks for a [[xxx] [yyy]] instead of splitting with [xxx : yyy]
    static Cmd _trans = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term v = p.pop();
            Iterator<Term> fstream = v.qvalue().tokens().iterator();

            QuoteStream tmpl = (QuoteStream)fstream.next().qvalue().tokens();

            QuoteStream res = new QuoteStream();
            while (fstream.hasNext())
                res.add(fstream.next());

            // collect as much params as there is from stack as there is in the template
            // first level.
            QuoteStream elem = new QuoteStream();
            fstream = tmpl.iterator();
            LinkedList<Term> st = new LinkedList<Term>();
            while (fstream.hasNext()) {
                Term t = fstream.next();
                Term e = p.pop();
                st.addFirst(e);
            }
            for (Term e: st)
                elem.add(e);

            HashMap<String, Term> symbols = new HashMap<String, Term>();
            //Now take each elem and its pair templ and extract the symbols and their meanings.
            evaltmpl(tmpl, elem, symbols);

            // now go over the quote we were just passed and replace each symbol with what we
            // have if we do have a definition.
            QuoteStream resstream = evalres(res, symbols);
            CmdQuote qs = new CmdQuote(resstream);

            Iterator<Term> i = qs.tokens().iterator();
            while (i.hasNext())
                p.push(i.next());
            return c.cont;
        }
    };

    static Cmd _words = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            VFrame f = p.pop().fvalue();

            // copy the rest of tokens to our own stream.
            QuoteStream nts = new QuoteStream();
            for(String s: sort(f.dict().keySet()))
                nts.add(new Term<String>(Type.TSymbol,s));
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts)));
            return c.cont;
        }
    };

    static Cmd _catch = new Cmd() {
        final int Start=0,Throw=1;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start: 
                    {
                        Term cexpr = p.pop();
                        Term texpr = p.pop();
                        Cont cont = new Cont(texpr.qvalue(), q.child(), c);
                        c.top = Throw;
                        c.store.put("catch:expr", cexpr);
                        return cont;
                    }
                case Throw:
                default:
                    {
                        Term cexpr = (Term) c.store.get("catch:expr");
                        Cont cont = new Cont(cexpr.qvalue(), q.child(), c.cont);
                        return cont;
                    }
            }
        }
    };

    static Cmd _throw = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term t = q.stack().peek();
            Cont cont = c.cont;
            cont.op = Op.OpX; // overwrite the original op as it is an exception now.
            cont.e = new VException("err:throw", t, t.value());
            return cont;
        }
    };

    static Cmd _stack = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            p.push(new Term<Quote>(Type.TQuote, p.quote()));
            return c.cont;
        }
    };

    static Cmd _unstack = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term t = p.pop();
            p.dequote(t.qvalue());
            return c.cont;
        }
    };


    static Cmd _abort = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            q.stack().clear();
            return c.cont;
        }
    };

    static Cmd _true = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            q.stack().push(new Term<Boolean>(Type.TBool, true));
            return c.cont;
        }
    };

    static Cmd _false = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            q.stack().push(new Term<Boolean>(Type.TBool, false));
            return c.cont;
        }
    };

    // Control structures
    static Cmd _if = new Cmd() {
        final int Start=0,Cond=1,Eval=2,Exit=3;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term action = p.pop();
                        Term cond = p.pop();
                        c.store.put("if:action", action);

                        if (cond.type == Type.TQuote) {
                            Cont ifcont = new Cont(cond.qvalue(),q.child(), c);
                            c.n = p.now; // save the stack.
                            c.top = Cond;
                            return ifcont;
                        } else {
                            p.push(cond);
                            c.n = p.now; // save the stack.
                            c.top = Eval;
                        }
                        return c;
                    }
                case Cond:
                    {
                        Term cond = p.pop(); // pop off the result.
                        // restore the stack
                        p.now = c.n;
                        // push it back so that eval will find it.
                        p.push(cond);
                        c.top = Eval;
                        return c;
                    }
                case Eval:
                    {
                        c.top = Exit;
                        Term cond = p.pop(); // pop off the result.
                        // dequote the action and push it to stack.
                        if (cond.bvalue()) {
                            Term action = (Term) c.store.get("if:action");
                            Cont cont = new Cont(action.qvalue(),q.child(), c);
                            return cont;
                        }
                        return c;
                    }
                case Exit:
                default:
                        // discard our continuation and return the parent.
                        return c.cont;
            }
        }
    };

    static Cmd _when = new Cmd() {
        final int Start=0,LStart=1,Cond=2,Eval=3,Exit=4;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term wquote = p.pop();
                        Cont cont = new Cont(wquote.qvalue(),q.child(), c);
                        // making sure that we come back.
                        cont.sym = c.sym;
                        cont.cmd = c.cmd;
                        cont.op = c.op;
                        cont.top = LStart;

                        c.top = Exit; // make sure that we exit once we finish the loop.
                        return cont;
                    }
                case LStart:
                    {
                        if (c.hasNext()) {
                            Term cond = c.next();
                            Term action = c.next();
                            c.store.put("if:action", action);

                            if (cond.type == Type.TQuote) {
                                Cont ifcont = new Cont(cond.qvalue(),q.child(), c);
                                c.n = p.now; // save the stack.
                                c.top = Cond;
                                return ifcont;
                            } else {
                                p.push(cond);
                                c.n = p.now; // save the stack.
                                c.top = Eval;
                            }
                            return c;
                        } else {
                            // exit out of the continuation of loop.
                            return c.cont;
                        }
                    }
                case Cond:
                    {
                        Term cond = p.pop(); // pop off the result.
                        // restore the stack
                        p.now = c.n;
                        // push it back so that eval will find it.
                        p.push(cond);
                        c.top = Eval;
                        return c;
                    }
                case Eval:
                    {
                        c.top = LStart;
                        Term cond = p.pop(); // pop off the result.
                        // dequote the action and push it to stack.
                        if (cond.bvalue()) {
                            Term action = (Term) c.store.get("if:action");
                            Cont cont = new Cont(action.qvalue(),q.child(), c);
                            return cont;
                        }
                        return c;
                    }
                case Exit:
                default:
                    return c.cont;
            }
        }
    };

    static Cmd _choice = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term af = p.pop();
            Term at = p.pop();
            Term cond = p.pop();

            if (cond.bvalue())
                p.push(at);
            else
                p.push(af);
            return c.cont;
        }
    };


    static Cmd _ifte = new Cmd() {
        final int Start=0,Cond=1,Eval=2,Exit=3;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term eaction = p.pop();
                        Term action = p.pop();
                        Term cond = p.pop();
                        c.store.put("if:action", action);
                        c.store.put("if:eaction", eaction);

                        if (cond.type == Type.TQuote) {
                            Cont ifcont = new Cont(cond.qvalue(),q.child(), c);
                            c.n = p.now; // save the stack.
                            c.top = Cond;
                            return ifcont;
                        } else {
                            p.push(cond);
                            c.n = p.now; // save the stack.
                            c.top = Eval;
                        }
                        return c;
                    }
                case Cond:
                    {
                        Term cond = p.pop(); // pop off the result.
                        // restore the stack
                        p.now = c.n;
                        // push it back so that eval will find it.
                        p.push(cond);
                        c.top = Eval;
                        return c;
                    }
                case Eval:
                    {
                        c.top = Exit;
                        Term cond = p.pop(); // pop off the result.
                        // dequote the action and push it to stack.
                        if (cond.bvalue()) {
                            Term action = (Term) c.store.get("if:action");
                            return new Cont(action.qvalue(),q.child(), c);
                        } else {
                            Term action = (Term) c.store.get("if:eaction");
                            return new Cont(action.qvalue(),q.child(), c);
                        }
                    }
                case Exit:
                default:
                        // discard our continuation and return the parent.
                        return c.cont;
            }
        }
    };

    static Cmd _while = new Cmd() {

        final int Start=0,LStart=1,Cond=2,Eval=3,Exit=4;
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term action = p.pop();
                        Term cond = p.pop();

                        Cont cont = new Cont(c.quote,c.scope, c);
                        // making sure that we come back.
                        cont.sym = c.sym;
                        cont.cmd = c.cmd;
                        cont.op = c.op;
                        cont.top = LStart;
                        cont.store.put("while:action", action);
                        cont.store.put("while:cond", cond);

                        c.top = Exit; // make sure that we exit once we finish the loop.
                        return cont;

                    }
                case LStart:
                    {
                        Term cond = (Term) c.store.get("while:cond");
                        if (cond.type == Type.TQuote) {
                            Cont ifcont = new Cont(cond.qvalue(),q.child(), c);
                            c.n = p.now; // save the stack.
                            c.top = Cond;
                            return ifcont;
                        } else {
                            p.push(cond);
                            c.n = p.now; // save the stack.
                            c.top = Eval;
                        }
                        return c;
                    }
                case Cond:
                    {
                        Term cond = p.pop(); // pop off the result.
                        // restore the stack
                        p.now = c.n;
                        // push it back so that eval will find it.
                        p.push(cond);
                        c.top = Eval;
                        return c;
                    }
                case Eval:
                    {
                        Term cond = p.pop(); // pop off the result.
                        // dequote the action and push it to stack.
                        if (cond.bvalue()) {
                            Term action = (Term) c.store.get("while:action");
                            Cont cont = new Cont(action.qvalue(),q.child(), c);
                            c.top = LStart;
                            return cont;
                        }
                        c.top = Exit;
                        return c;
                    }
                case Exit:
                default:
                        // discard our continuation and return the parent.
                        return c.cont;
            }
        }
    };

    // Libraries
    static Cmd _print = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term t = p.pop();
            V.out(t.value());
            return c.cont;
        }
    };

    static Cmd _peek = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            if (p.empty()) {
                V.outln("");
            } else {
                Term t = p.peek();
                V.outln(t.value());
            }
            return c.cont;
        }
    };

    static Cmd _show = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            p.dump();
            return c.cont;
        }
    };

    static Collection<String> sort(Set<String> ks) {
        LinkedList<String> ll = new LinkedList(ks);
        Collections.sort(ll);
        return ll;
    }

    static Cmd _vdebug = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            V.outln(q.parent().id());
              q.stack().dump();
              for(String s: sort(q.parent().dict().keySet())) V.out(s + " ");
              V.outln("\n________________");
            return c.cont;
        }
    };

    static Cmd _dframe = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            q.stack().dump();
            q = q.parent(); // remove the current child frame.
            while(q != null) {
                dumpframe(q);
                q = q.parent();
            }
            return c.cont;
        }
        public void dumpframe(VFrame q) {
            V.outln(q.id());
            for(String s: sort(q.dict().keySet())) V.out(s + " ");
            V.outln("\n________________");
        }
    };

    static Cmd _debug = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            V.debug(p.pop().bvalue());
            return c.cont;
        }
    };

    // this map is not a stack invariant. specifically 
    // 1 2 3 4  [a b c d] [[] cons cons] map => [[4 a] [3 b] [2 c] [1 d]]
    static Cmd _map = new LstCmd() {
        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term list = p.pop();

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            cont.store.put("map:action", action);
            return cont;
        }

        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            // did we go through a round before this?
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            if (nts == null) {
                nts = new QuoteStream();
                c.store.put("map:result", nts);
            } else {
                nts.add(p.pop());
            }

            Term t = c.next();
            p.push(t);
            Term action = (Term)c.store.get("map:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }

        Cont last(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            nts.add(p.pop());
            c.top= Exit;
            return c;
        }

        Cont exit(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts)));
            return c.cont;
        }
    };

    // map is a stack invariant. specifically 
    // 1 2 3 4  [a b c d] [[] cons cons] map => [[4 a] [4 b] [4 c] [4 d]]
    static Cmd _map_i = new LstCmd() {
        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term list = p.pop();

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            Node<Term> n = p.now;
            cont.store.put("map:stack", n);
            cont.store.put("map:action", action);
            return cont;
        }

        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            // did we go through a round before this?
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            if (nts == null) {
                c.store.put("map:result", new QuoteStream());
            } else {
                nts.add(p.pop());
                p.now = (Node<Term>)c.store.get("map:stack");
            }

            Term t = c.next();
            p.push(t);
            Term action = (Term)c.store.get("map:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }

        Cont last(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            nts.add(p.pop());
            p.now = (Node<Term>)c.store.get("map:stack");
            c.top= Exit;
            return c;
        }

        Cont exit(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts = (QuoteStream)c.store.get("map:result");
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts)));
            return c.cont;
        }
    };

    static Cmd _split = new LstCmd() {
        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term list = p.pop();

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            cont.store.put("split:action", action);
            return cont;
        }

        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            // did we go through a round before this?
            QuoteStream[] nts = (QuoteStream[])c.store.get("split:result");
            if (nts == null) {
                nts = new QuoteStream[]{new QuoteStream(), new QuoteStream()};
                c.store.put("split:result", nts);
            } else {
                Term t = (Term)c.store.get("split:term");
                if (p.pop().bvalue())
                    nts[0].add(t);
                else
                    nts[1].add(t);
            }

            Term t = c.next();
            c.store.put("split:term", t);
            p.push(t);
            Term action = (Term)c.store.get("split:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }

        Cont last(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream[] nts = (QuoteStream[])c.store.get("split:result");
            Term t = (Term)c.store.get("split:term");
            if (p.pop().bvalue())
                nts[0].add(t);
            else
                nts[1].add(t);
            c.top= Exit;
            return c;
        }

        Cont exit(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts[] = (QuoteStream[])c.store.get("split:result");
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts[0])));
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts[1])));
            return c.cont;
        }
    };

    static Cmd _split_i = new LstCmd() {

        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term list = p.pop();

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            Node<Term> n = p.now;
            cont.store.put("split:stack", n);
            cont.store.put("split:action", action);
            return cont;
        }

        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            // did we go through a round before this?
            QuoteStream[] nts = (QuoteStream[])c.store.get("split:result");
            if (nts == null) {
                nts = new QuoteStream[]{new QuoteStream(), new QuoteStream()};
                c.store.put("split:result", nts);
            } else {
                Term t = (Term)c.store.get("split:term");
                if (p.pop().bvalue())
                    nts[0].add(t);
                else
                    nts[1].add(t);
                p.now = (Node<Term>)c.store.get("split:stack");
            }

            Term t = c.next();
            c.store.put("split:term", t);
            p.push(t);
            Term action = (Term)c.store.get("split:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }

        Cont last(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream[] nts = (QuoteStream[])c.store.get("split:result");
            Term t = (Term)c.store.get("split:term");
            if (p.pop().bvalue())
                nts[0].add(t);
            else
                nts[1].add(t);
            p.now = (Node<Term>)c.store.get("split:stack");
            c.top= Exit;
            return c;
        }

        Cont exit(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            QuoteStream nts[] = (QuoteStream[])c.store.get("split:result");
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts[0])));
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts[1])));
            return c.cont;
        }

    };

    static Cmd _fold = new LstCmd() {
        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term init = p.pop();
            Term list = p.pop();
            p.push(init);

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            cont.store.put("fold:action", action);
            return cont;
        }
        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term t = c.next();
            p.push(t);
            Term action = (Term)c.store.get("fold:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }
    };

    static Cmd _fold_i = new LstCmd() {
        Cont start(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term action = p.pop();
            Term init = p.pop();
            Term list = p.pop();


            Node<Term> n = p.now;
            p.push(init);

            Cont cont = new Cont(list.qvalue(),c.scope, c.cont);
            // making sure that we come back.
            cont.sym = c.sym;
            cont.cmd = c.cmd;
            cont.op = c.op;
            cont.top = Eval;
            cont.store.put("fold:stack", n);
            cont.store.put("fold:action", action);
            return cont;
        }
        Cont eval(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term t = c.next();
            Term res = p.pop();// save the result
            p.now = (Node<Term>)c.store.get("fold:stack"); // and reset the stack
            p.push(res);
            p.push(t);
            Term action = (Term)c.store.get("fold:action");
            Cont cont = new Cont(action.qvalue(), q, c);
            return cont;
        }

        Cont exit(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term res = p.pop();
            p.push(res);
            return c.cont;
        }
    };

    static Cmd _size = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term list = p.pop();
            int count = 0;
            for(Term t: list.qvalue().tokens())
                ++count;

            p.push(new Term<Integer>(Type.TInt , count));
            return c.cont;
        }
    };

    static Cmd _isin = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term i = p.pop();
            Term list = p.pop();
            for(Term t: list.qvalue().tokens()) {
                if (t.type() == i.type() && t.value() == i.value()) {
                    p.push(new Term<Boolean>(Type.TBool, true));
                    return c.cont;
                }
            }
            p.push(new Term<Boolean>(Type.TBool, false));
            return c.cont;
        }
    };

    static Cmd _at = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term i = p.pop();
            int idx = i.ivalue();
            Term list = p.pop();
            int count = 0;
            for(Term t: list.qvalue().tokens()) {
                if (count == idx) {
                    p.push(t);
                    return c.cont;
                }
                ++count;
            }
            throw new VException("err:at:overflow",i, "[" + list.value() + "]:" + idx);
        }
    };

    static Cmd _drop = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term i = p.pop();
            int num = i.ivalue();
            Term list = p.pop();

            QuoteStream nts = new QuoteStream();

            for(Term t: list.qvalue().tokens()) {
                if (num <= 0)
                    nts.add(t);
                --num;
            }
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts)));
            return c.cont;
        }
    };

    static Cmd _take = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term i = p.pop();
            int num = i.ivalue();
            Term list = p.pop();
            int count = 0;

            QuoteStream nts = new QuoteStream();

            for(Term t: list.qvalue().tokens()) {
                if (count >= num)
                    break;
                ++count;
                nts.add(t);
            }
            p.push(new Term<Quote>(Type.TQuote, new CmdQuote(nts)));
            return c.cont;
        }
    };



    static Cmd _dequote = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term prog = p.pop();
            Cont cont = new Cont(prog.qvalue(), q, c.cont);
            return cont;
        }
    };

    static Cmd _dequoteenv = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();

            Term prog = p.pop();
            Term env = p.pop();
            Cont cont = new Cont(prog.qvalue(), env.fvalue(), c.cont);
            return cont;
        }
    };

    static Cmd _add = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            double dres = a.numvalue().doubleValue() + b.numvalue().doubleValue();
            int ires  = (int)dres;
            if (dres == ires)
                p.push(new Term<Integer>(Type.TInt, ires));
            else
                p.push(new Term<Double>(Type.TDouble, dres));
            return c.cont;
        }
    };

    static Cmd _sub = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            double dres = a.numvalue().doubleValue() - b.numvalue().doubleValue();
            int ires  = (int)dres;
            if (dres == ires)
                p.push(new Term<Integer>(Type.TInt, ires));
            else
                p.push(new Term<Double>(Type.TDouble, dres));
            return c.cont;
        }
    };

    static Cmd _mul = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            double dres = a.numvalue().doubleValue() * b.numvalue().doubleValue();
            int ires  = (int)dres;
            if (dres == ires)
                p.push(new Term<Integer>(Type.TInt, ires));
            else
                p.push(new Term<Double>(Type.TDouble, dres));
            return c.cont;
        }
    };

    static Cmd _div = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            double dres = a.numvalue().doubleValue() / b.numvalue().doubleValue();
            int ires  = (int)dres;
            if (dres == ires)
                p.push(new Term<Integer>(Type.TInt, ires));
            else
                p.push(new Term<Double>(Type.TDouble, dres));
            return c.cont;
        }
    };

    static Cmd _gt = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, isGt(a, b)));
            return c.cont;
        }
    };

    static Cmd _lt = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, isLt(a, b)));
            return c.cont;
        }
    };

    static Cmd _lteq = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, !isGt(a, b)));
            return c.cont;
        }
    };

    static Cmd _gteq = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, !isLt(a, b)));
            return c.cont;
        }
    };

    static Cmd _eq = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, isEq(a, b)));
            return c.cont;
        }
    };

    static Cmd _neq = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, !isEq(a, b)));
            return c.cont;
        }
    };

    static Cmd _and = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, and(a, b)));
            return c.cont;
        }
    };

    static Cmd _or = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term b = p.pop();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, or(a, b)));
            return c.cont;
        }
    };

    static Cmd _not = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, !a.bvalue()));
            return c.cont;
        }
    };


    // Predicates do not consume the element. 
    static Cmd _isbool = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TBool));
            return c.cont;
        }
    };

    static Cmd _isinteger = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TInt));
            return c.cont;
        }
    };

    static Cmd _isdouble = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TDouble));
            return c.cont;
        }
    };

    static Cmd _issym = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TSymbol));
            return c.cont;
        }
    };

    static Cmd _islist = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TQuote));
            return c.cont;
        }
    };

    static Cmd _isstr = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TString));
            return c.cont;
        }
    };

    static Cmd _isnum = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool,
                        a.type == Type.TInt || a.type == Type.TDouble));
            return c.cont;
        }
    };

    static Cmd _ischar = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Boolean>(Type.TBool, a.type == Type.TChar));
            return c.cont;
        }
    };

    static Cmd _tostring = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<String>(Type.TString, a.value()));
            return c.cont;
        }
    };

    static Cmd _toint = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Integer>(Type.TInt, (new Double(a.value())).intValue()));
            return c.cont;
        }
    };

    static Cmd _todecimal = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term a = p.pop();
            p.push(new Term<Double>(Type.TDouble, new Double(a.value())));
            return c.cont;
        }
    };


    /* stdlib.v
     * [stdlib 
     *      [qsort  xxx yyy].
     *      [binsearch aaa bbb].
     * ]
     *
     * 'stdlib' use
     * [1 7 3 2 2] stdlib:qsort
     * */
    static Cmd _use = new Cmd() {
        final int Start=0,Eval=1,Exit=2;
        public Cont trampoline(Cont c) {
            // eval is passed in the quote representing the current scope.
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term file = p.pop();
                        if (file.type == Type.TQuote) {
                            Cont cont = new Cont(file.qvalue(),q,c);

                            cont.top = Eval;
                            c.top = Exit;
                            cont.op = c.op;
                            cont.sym = c.sym;
                            cont.cmd = c.cmd;
                            return cont;
                        } else {
                            QuoteStream nts = new QuoteStream();
                            nts.add(file);
                            CmdQuote qval = new CmdQuote(nts); 
                            Cont cont = new Cont(qval,q,c);

                            cont.top = Eval;
                            c.top = Exit;
                            cont.op = c.op;
                            cont.sym = c.sym;
                            cont.cmd = c.cmd;
                            return cont;
                        }
                    }
                case Eval:
                    {
                        if (c.hasNext()) {
                            Term file = c.next();
                            try {
                                String val = file.svalue() + ".v";
                                // Try and see if the file requested is any of the standard defined
                                String chars = Util.getresource(val);
                                CharStream cs = chars == null? new FileCharStream(val) : new BuffCharStream(chars);
                                CmdQuote module = new CmdQuote(new LexStream(cs));
                                Cont cont = new Cont(module, q, c);
                                return cont;
                            } catch (Exception vx) {
                                throw new VException("err:use",file, file.value());
                            }
                        } else {
                            c.top = Exit;
                            return c;
                        }
                    }
                case Exit:
                default:
                    return c.cont;
            }
        }
    };

    // [std] $me &use
    static Cmd _useenv = new Cmd() {
        final int Start=0,Eval=1,Exit=2;
        public Cont trampoline(Cont c) {
            // eval is passed in the quote representing the current scope.
            VFrame q = c.scope;
            VStack p = q.stack();
            switch (c.top) {
                case Start:
                    {
                        Term env = p.pop();
                        Term file = p.pop();
                        if (file.type == Type.TQuote) {
                            Cont cont = new Cont(file.qvalue(),q,c);

                            cont.top = Eval;
                            c.top = Exit;
                            cont.op = c.op;
                            cont.sym = c.sym;
                            cont.cmd = c.cmd;
                            cont.store.put("useenv:env", env);
                            return cont;
                        } else {
                            QuoteStream nts = new QuoteStream();
                            nts.add(file);
                            CmdQuote qval = new CmdQuote(nts); 
                            Cont cont = new Cont(qval,q,c);

                            cont.top = Eval;
                            c.top = Exit;
                            cont.op = c.op;
                            cont.sym = c.sym;
                            cont.cmd = c.cmd;
                            cont.store.put("useenv:env", env);
                            return cont;
                        }
                    }
                case Eval:
                    {
                        if (c.hasNext()) {
                            Term file = c.next();
                            try {
                                String val = file.svalue() + ".v";
                                // Try and see if the file requested is any of the standard defined
                                String chars = Util.getresource(val);
                                CharStream cs = chars == null? new FileCharStream(val) : new BuffCharStream(chars);
                                CmdQuote module = new CmdQuote(new LexStream(cs));
                                Term env = (Term) c.store.get("useenv:env");
                                Cont cont = new Cont(module, env.fvalue(), c);
                                return cont;
                            } catch (Exception vx) {
                                vx.printStackTrace();
                                throw new VException("err:&use",file, file.value());
                            }
                        } else {
                            c.top = Exit;
                            return c;
                        }
                    }
                case Exit:
                default:
                    return c.cont;
            }
        }
    };

    static Cmd _eval = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term buff = p.pop();
            try {
                Quote val = Util.getdef(buff.svalue());
                Cont cont = new Cont(val, q, c.cont);
                return cont;
            } catch (VException e) {
                e.addLine("eval " + buff.value());
                throw e;
            } catch (Exception e) {
                throw new VException("err:eval",buff, buff.value());
            }
        }
    };

    static Cmd _evalenv = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Quote qenv = p.pop().qvalue();
            VFrame env = qenv.tokens().iterator().next().fvalue();
            Term buff = p.pop();
            Quote val = Util.getdef(buff.svalue());
            Cont cont = new Cont(val, env, c.cont);
            return c.cont;
        }
    };

    static Cmd _help = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            HashMap <String,Quote> bind = q.dict();
            for(String s : new TreeSet<String>(bind.keySet()))
                V.outln(s);
            return c.cont;
        }
    };

    static Cmd _env = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Quote env = Util.getdef("[platform java]");
            p.push(new Term<Quote>(Type.TQuote, env));
            return c.cont;
        }
    };

    static Cmd _time = new Cmd() {
        public Cont trampoline(Cont c) {
            VFrame q = c.scope;
            VStack p = q.stack();
            Term t = p.pop();
            boolean val = t.bvalue();
            V.showtime(val);
            return c.cont;
        }
    };

    public static Cont init(final VFrame iframe) {
        //meta
        iframe.def(".", _def);
        iframe.def("&.", _defenv);
        iframe.def("module", _defmodule);
        iframe.def("&words", _words);
        iframe.def("&parent", _parent);
        iframe.def("$me", _me);

        iframe.def("&i", _dequoteenv);
        iframe.def("i", _dequote);

        iframe.def("view", _view);
        iframe.def("trans", _trans);
        iframe.def("java", _java);

        iframe.def("true", _true);
        iframe.def("false", _false);
        iframe.def("catch", _catch);
        iframe.def("throw", _throw);
        iframe.def("$stack", _stack);
        iframe.def("stack!", _unstack);

        iframe.def("and", _and);
        iframe.def("or", _or);
        iframe.def("not", _not);

        //control structures
        iframe.def("ifte", _ifte);
        iframe.def("if", _if);
        iframe.def("when", _when);
        iframe.def("while", _while);
        iframe.def("choice", _choice);

        //io
        iframe.def("put", _print);

        //others
        iframe.def("?", _peek);
        iframe.def("??", _show);
        iframe.def("?debug", _vdebug);
        iframe.def("?stack", _show);
        iframe.def("?frame", _dframe);
        iframe.def(".debug", _debug);

        iframe.def("abort", _abort);

        //list
        iframe.def("size", _size);
        iframe.def("in?", _isin);
        iframe.def("at", _at);
        iframe.def("drop", _drop);
        iframe.def("take", _take);


        // on list
        iframe.def("map!", _map);
        iframe.def("map", _map_i);
        iframe.def("split!", _split);
        iframe.def("split", _split_i);
        iframe.def("fold!", _fold);
        iframe.def("fold", _fold_i);

        //arith
        iframe.def("+", _add);
        iframe.def("-", _sub);
        iframe.def("*", _mul);
        iframe.def("/", _div);

        //bool
        iframe.def("=", _eq);
        iframe.def("==", _eq);
        iframe.def("!=", _neq);
        iframe.def(">", _gt);
        iframe.def("<", _lt);
        iframe.def("<=", _lteq);
        iframe.def(">=", _gteq);

        //predicates
        iframe.def("integer?", _isinteger);
        iframe.def("double?", _isdouble);
        iframe.def("boolean?", _isbool);
        iframe.def("symbol?", _issym);
        iframe.def("list?", _islist);
        iframe.def("char?", _ischar);
        iframe.def("number?", _isnum);
        iframe.def("string?", _isstr);

        iframe.def(">string", _tostring);
        iframe.def(">int", _toint);
        iframe.def(">decimal", _todecimal);

        //modules
        iframe.def("use", _use);
        iframe.def("&use", _useenv);
        iframe.def("eval", _eval);
        iframe.def("&eval", _evalenv);

        iframe.def("help", _help);
        iframe.def("env", _env);
        
        iframe.def(".time!", _time);

        Quote libs = Util.getdef("'std' use");
        return new Cont(libs, iframe, null);
    }
}

