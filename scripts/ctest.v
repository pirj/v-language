# concurrency tests

[1 [10 <] [ dup puts receive puts '>consumer>' puts succ ] while] fork [pid] let

1 [10 <] [ dup '<producer<' unit cons pid send succ ] while
