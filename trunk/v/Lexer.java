package v;

import java.util.*;
import java.util.regex.*;

public class Lexer {
    // Lexer does not know any thing about compounds.
    StringBuffer _word = new StringBuffer();
    public Stack<Character> _cstack = new Stack<Character>(); // stack for compound.

    public LinkedList<Term> _queue = new LinkedList<Term>();

    public CharStream _stream = null;
    public Lexer(CharStream q) {
        _stream = q;
    }

    private boolean isStringBoundary(char c) {
        if (c == '"' | c == '\'' || c == '`')
            return true;
        return false;
    }

    private boolean isCompoundBoundary(char c) {
        if (c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}' )
            return true;
        return false;
    }

    private boolean isPunctuation(char c) {
        // dummy
        return false;
    }

    private boolean isBoundary(char c) {
        if (c == 0)
            return true;
        if (Character.isWhitespace(c))
            return true;
        if (isStringBoundary(c))
            return true;
        if (isCompoundBoundary(c))
            return true;
        if (isPunctuation(c))
            return true;
        return false;
    }

    static final Pattern P_INT = Pattern.compile("^-*[0-9]+$");
    static final Pattern P_FLOAT = Pattern.compile("^-*[0-9][0-9.Ee-]*$");

    void add(Term term) {
        _queue.add(term);
        _word.setLength(0);
    }

    public void reset() {
        _queue.clear();
    }

    public void lex() {
        // Use the V.read to fetch the values.
        // loop on each char, and add the found
        // symbols to the end of queue.
        _word.setLength(0);
        char c = _stream.read();
        switch (c) {
            case '#':
                lcomment();
                break;
            case ' ':
            case '\t':
            case '\n':
            case '\r':
            case '\f':
            case '\b':
                space();
                break;

                //string
            case '"':
            case '\'':
            case '`':
                string();
                break;

                //compound.
            case '[':
            case '(':
            case '{':
                copen();
                break;

            case ']':
            case ')':
            case '}':
                cclose();
                break;

            default: // word fetch until the next space.
                if (c >= 32 && c <= 126)
                    word();
        }
    }

    static char closeCompound(char c) {
        switch(c) {
            case '{':
                return '}';
            case '(':
                return ')';
            case '[':
                return ']';
            default:
                throw new VException("Invalid compound char (" + c + ")");
        }
    }

    void copen() {
        _cstack.push(closeCompound(_stream.current()));
        add(new Term<Character>(Type.TOpen, _stream.current()));
    }

    // we just make sure that we read the compound chars in sequence.
    // and keep tab on the compound is complete or not.
    void cclose() {
        if (_cstack.empty())
            throw new VException("Invalid close ("
                    + _stream.current() + ") - compound stack empty.");

        char c = _cstack.pop();
        if (c != _stream.current())
            throw new VException("Invalid close ("
                    + _stream.current() + ") - expects (" + c + ")");

        add(new Term<Character>(Type.TClose, _stream.current()));
    }

    void lcomment() {
        while (_stream.read() != '\n');
    }

    // we can recognize the string here itself so no point passing it to the parser.
    void string() {
        char start = _stream.current();
        // look for unescaped end same as start.
        char c;
        while (true) {
            c = _stream.read();
            if (c == '\\') { // escaped.
                // read next char and continue.
                _word.append(c);
                _word.append(_stream.read());
                continue;
            }
            if (start == c)
                break;
            _word.append(c);
        }
        add(new Term<String>(Type.TString, _word.toString()));
    }

    void space() {
        while(true) {
            char c = _stream.peek();
            if (c != '\n' && Character.isWhitespace(c))
                _stream.read();
            else
                break;
        }
    }

    void word() {
        _word.append(_stream.current());
        while (!isBoundary(_stream.peek()))
            _word.append(_stream.read());

        // does it look like a number?
        String word = _word.toString();

        if (P_INT.matcher(word).matches())
            add(new Term<Integer>(Type.TInt, Integer.parseInt(_word.toString())));
        else if (P_FLOAT.matcher(word).matches())
            add(new Term<Float>(Type.TFloat, Float.parseFloat(_word.toString())));
        else
            add(new Term<String>(Type.TSymbol, _word.toString()));
    }

    public void dump() {
        for (Token e: _queue) {
            V.outln("; " + e.value());
        }
    }

    // get next symbol.
    public Term next() {
        // do we have any thing on the stack?
        // if we have return it from there.
        // else run lex and try again.
        if (_queue.size() == 0) {
            lex();
            return next();
        }
        return _queue.removeFirst();
    }
}
