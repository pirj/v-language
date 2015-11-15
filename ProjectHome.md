A tiny concatenative language implemented for experimentation.

The source is under Public Domain (un-copyrighted.)

The full featured language has two implementations now, One is implemented
over JVM and the other is native.

(drop me a note [rnair6-iit edu] if you are interested)

To run the jvm implementation, extract the distribution in any directory,
```
gmake -f makefile.j
gmake -f makefile.j run
     V
|
```

The native distribution can be installed withe the standard
```
configure && make && make install
/usr/local/bin/v
     V
|
```
invocation.

The language is a close relative of postscript, forth and joy. and is stack based. ie:
```
|2 3 *
=6
|2 3 * 5 +
=11
```

See [status](http://code.google.com/p/v-language/wiki/Status) for a tutorial and more info.
and See [rosettacode](http://rosettacode.org/wiki/V) for more examples.
The Functions available in V are available in this page: [functions](Functions.md)

(The releases are out of date and multiple fixes have gone in.
Please check out and build rather than use them.)

Example functions in V.
getting the roots (with out using the stack shuffling word 'view')
```
[quad-formula
    [a b c] let
    [minisub 0 b -].
    [radical b b * 4 a * c * - sqrt].
    [divisor 2 a *].
    [root1 minisub radical + divisor /].
    [root2 minisub radical - divisor /].
    root1 root2
].

|2 4 -30 quad-formula ??
=(-5.0 3.0)
```

using 'view'

```
[quad-root
    [a b c : [0 b - b b * 4 a * c * - sqrt + 2 a * /]] view i 
].

|2 4 -30 quad-root ??
=(3)

```

contrast this with the definition in scheme [here](http://lambda-the-ultimate.org/node/900)

```
(define quadratic-formula
   (lambda (a b c)
      (let ([minusb (- 0 b)]
            [radical (sqrt (- (* b b) (* 4 ( * a c))))]
            [divisor (* 2 a)] )
         let ([root1 (/ (+ minusb radical) divisor)]
              [root2 (/ (- minusb radical) divisor)])
           (cons root1 root2)))))
```



Definition of Qsort.
```
[qsort
    #definitions
    [joinparts [pivot [*list1] [*list2] : [*list1 pivot *list2]] view].
    [split_on_first_element uncons [>] split&].
    #args starts for binrec. notice that 2 arguments (termination condition
    #and its result) are on first line.
    [small?] []
    [split_on_first_element]
    #binrec recurses on the result of split_on_first_element before applying joinparts.
    [joinparts]
    binrec].
```

Some explanations.
```
The first and second lines (terminated by '.') are internal function definitions
(Notice how qsort is also terminated by '.') '.' is the definition syntax in V.

The first function joinparts
============================
The function joinpart contains just an application of the operator view.
'view' is list translator. It takes a list of the form [template : result]
then it tries to apply the template to the current stack. If it can be applied on the 
stack, then the arguments named in the template are bound to values in stack. The result
is then processed, and all the bound elements in result are replaced by their values.

[pivot [*list1] [*list2] : [*list1 pivot *list2]] view expects 3 arguments on the stack,
  the first a single element pivot, then two lists list1 and list2.
  It returns a list that is composed of elements of list1 followed by pivot
  followed by elements of list2 (as defined in result - RHS of ':').

ie:
44 [1 2 3] [5 6 7] [pivot [*list1] [*list2] : [*list1 pivot *list2]] view ??
=> [1 2 3 44 5 6 7]
(The function ?? is used to print out the elements in the stack now.)


The second function split_on_first_element
==========================================
The definition is [uncons [>] split&]
The uncons splits a list into the first element and the rest of the list.
ie:
[1 2 3 4 5] uncons ??
=1 [2 3 4 5]
split& takes two arguments, the first is the function F to split a list with,
and the second the list itself. All elements in the list that passes the function F
is put into the first list, and all that do not are put into the second list.

ie:
[1 2 3 4 5 6 7] [4 >] split& ??
=[5 6 7] [1 2 3 4]

The function F can also take an argument from the stack. so this also works.
4 [1 2 3 4 5 6 7] [>] split& ??
=[5 6 7] [1 2 3 4]

Thus the split_on_first_element takes the first element of a list, and split that
list based on that element as a filter.

binrec
=======
binrec expects 4 arguments, 

Arg1 is the terminating condition,
Arg2 is the result if the terminating condition is met.
Arg3 is an executable statement that returns two entities.
   The entire binrec statement is performed on each of the
   two entities until the terminating condition is met.
Arg4 is what to do with the result of the previous statement.
```


Algorithm.
```
Here, the small? checks if the list is empty or contains just one element.
if it is, then the result is arg2 - []
ie:
[] small? ??
=true

[1] small? ??
=true

[1 2 3 4] small? ??
=false

split_on_first_element takes is executed on all lists that are larger than size 1
and as explained above, splits them into two based on the first element.
on the resultent lists, the entire qsort is performed again due to binrec.
The last joinparts takes these elements (pivot list1 list2) which are present now
on the stack, and combines them to produce a single sorted list.
```

A slightly friendlier function (with out the binrec.)

```
[qsort
  [joinparts [pivot [*list1] [*list2] : [*list1 pivot *list2]] view].
  [split_on_first_element uncons [>] split&].
  [small?]
    []
    [split_on_first_element [list1 list2 : [list1 qsort list2 qsort joinparts]] view i]
  ifte].
```

The binrec and friends are more powerful than the explicit recursion done above, but for
people new to concatenative languages, this kind of recursion may look more intuitive.

The green-threads branch contains threads implemented using CPS and trampoline style.
The 'fork' creates a new thread and pushes its thread id into the stack for the main thread to pickup. 'send' takes two words in the stack, the id of the thread to send to and the message. 'receive' receives any messages send to this thread.

consumer
```
 [1
   [10 <] [
      dup puts
      receive
      put '>consumer>' puts
      succ ]
   while]
 fork
 [id] let
```

producer
```
1 [10 <] [
      dup '<producer<' unit cons 
      id send
      succ ]
   while
```

use
```
gmake -f makefile.j ctest 
```
on green-threads branch to run this example.