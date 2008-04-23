package v;
public enum Type {
    TSymbol,
    TQuote,
    TFrame,
    TCont,
    TString,
    TInt,
    TDouble,
    TChar,
    TBool,
    TObject, // only for java.

    // ----------- Used only in lexer
    TOpen,
    TClose
};

