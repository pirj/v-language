# concurrency tests

[1 [10 <] [ '>consumer>' puts dup puts receive puts succ ] while] fork [id] let

1 [10 <] [ '<producer<' id send succ ] while
