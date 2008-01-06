package v;

import java.util.*;

/* The world is a quote
 * */

public class CmdQuote implements Quote {
    /* The quote contains a Token stream
     * */

    // Our symbols to evaluate.
    TokenStream _tokens = null;

    public CmdQuote(TokenStream tokens) {
        _tokens = tokens;
    }

    public TokenStream tokens() {
        return _tokens;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        Iterator<Term> i = _tokens.iterator();
        while(i.hasNext()) {
            sb.append(i.next().value());
            if (i.hasNext())
                sb.append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

    HashMap<String, Object> _store = new HashMap<String, Object>();
    public HashMap<String, Object> store() {
        return _store;
    }
}
