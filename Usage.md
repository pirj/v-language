#summary V usage

# Introduction #

V language is a simple concatenative language inspired by joy. Currently it
exists in JVM land only.

# Usage #
(Notice that the prompt is '|')
  * invocation
```
   java v/V
      |V|
   |
```
  * Simple arithmetic
```
   |1 2 +
   =3
```

  * Define words using the function '.'
```
   |[add +].
   |1 1 add
   =2
   |[check [10 >] ['is gt 10' println] ['is lt 10' println] ifte].

   |1 check
   =is lt 10
   |100 check
   =is gt 10
```

  * how qsort would look like this.
```
   |[qsort
      [small?]
      []
      [uncons [>] split&]
      [[swap] dip cons concat]
      binrec].

   |[1 3 2 8] qsort
   |?
   =1 2 3 8
```
Notice the similarity between this and the joy version [here](http://en.wikipedia.org/wiki/Joy_(programming_language)).


> # differences from joy. #

  * No invariant stack for combinators.
The stack invariant combinators does not exist as of now.
ie the ifte condition consumes values from the stack unlike joy
which saves the stack state before execution of condition and restores it
after it.

So a recursive def of factorial looks like below:
```
[fact
    [dup 0 =]
        [pop 1]
        [dup 1 - fact *]
    ifte].
```
```
Notice how we have to do the [dup 0 =] instead of just [0 =] in the classic joy.
(the definition is accomplished by the word '.')
```

  * V also allows internal definitions.
ie
```
[area
    [pi 3.1415].
    [sq dup *].
    sq pi *].
```

  * Homoiconic
V strives to be as much homoiconic as possible so a V program that is invoked
from the command line with args a b c is no different from this.
```
|a b c [my v prog]i
```
ie: the args are pushed to the stack, the quote i passed to stack and dequoted.

This means the special syntax for DEFINE LIBRA etc in joy are not present in V


