﻿{{{
Use ? to see the last parameter in the stack.
?? will show you the entire stack.
(The prompt is assumed to be '|')
ie.
|1 1
|??
=(1 1)
|+
|??
=(2)


differences from joy.

V allows internal definitions.
ie

[area
> [3.1415](pi.md).
> [dup \*](sq.md).
> sq pi **].**

The character literal is just ~x

V strives to be as much homoiconic as possible so a V program that is invoked
from the command line with args a b c is no different from this.
|a b c [v prog](my.md)i
ie: the args are pushed to the stack, the quote i passed to stack and dequoted.

Things that end with '&' are non destructive (they dont consume values from stack).
ie:
> |[2 3](1.md) first& ??
> =([2 3](1.md) 1)
> |[2 3](1.md) length& ??
> =([2 3](1.md) 3)

They have counterparts that do not have '&' in their name.
> [2 3](1.md) length => 3

some examples in V


qsort:

[qsort
> [small?]
> [.md](.md)
> [[>](uncons.md) split&]
> [[swap](swap.md) dip cons concat]
> binrec].

|[9 6 7 8 4 6 2](0.md) qsort ??
=([2 4 6 6 7 8 9](0.md))


getting the roots

[root
> [b c](a.md) let
> [dup \*](sqr.md).
> [b sqr 4 a \* c \* - sqrt](discr.md).
> [0 b - discr + 2 a \* /](root1.md).
> [0 b - discr - 2 a \* /](root2.md).
> "root1 :" put root1 puts
> "root2 :" put root2 puts
].

some points to mention here.
the let makes use of a slightly dirty word '@',
it defines things in the binding of its parent.
let is defined as [[unit cons rev @ true](rev.md) map pop]
It can be used in any place, not just inside a word definition.

it is probably not a good idea to use let, since the naming of internal
variables makes it impure both in both concatenative and functional terms.
The 'let' and '@' may disappear in the future (and '.' may become strict
about redefining words).

the root1 root2 and discr are internal definitions created
using '.'

use help to see all the current bindings

Using throw and catch


eg:

[cmdthrows
> [
> > 'hi there throw' throw

> ] ['exception continued' puts ' more' throw](puts.md) catch
].
[mycmd
> [
> > cmdthrows

> ] ['exception caught' puts](puts.md) catch
].
mycmd

|mycmd
=hi there throw
=exception continued
=more
=exception caught

Using Java calls.

The word 'java' interprets the java method calls.  call syntax is
|[class/obj method](parameter.md) java
Primitive types are automatically converted to their java types.

|["I am here" length] java ?
=9

Here the string "I am here" is converted into a java.lang.String and then
the method length is invoked on this object.

Using a static method is the simplest operation on a class. The class name
is used as a symbol. If you pass a symbol in the place of class/obj it is
interpreted as a class name. else it is taken as a java object instance.

|[-100 java.lang.Math abs] java ?
=100

Invoking a constructor

|[java.util.Date new] java ?
={Tue Mar 13 19:59:22 IST 2007}

-- The java objects are always shown with '{}' around them.

and using that instance to invoke a method.
|unit [getDay](getDay.md) concat java ?
=2


Using an array as an argument.

|[[~a ~b ~c] java.lang.String new] java ?
='abc'

Accessing static fields

|[v.V version$] java ?
='0.002'

setting them.
|['0.001' v.V version$] java
|[v.V version$] java ?
='0.001'


Using the stack shuffling 'view'


[2](1.md) [[b](a.md) : a b] view
=1 2

'_' is used to ignore the value on the quote
'**' is used to indicate that there are 0 or more elements
left. and**xxx can be used to name them._

[2 3 4 5](1.md) [[\*rest](a.md) : **rest a] view
=2 3 4 5 1**

it can also be done on tail.

[2 3 4 5](1.md) [[**rest a] : a**rest] view
5 1 2 3 4

see the last examples for tests using view}}}
=======================================

Examples see
[http://v-language.googlecode.com/svn/trunk/scripts/test.v examples]

=======================================

Functions available:

[Functions functions]

=======================================```