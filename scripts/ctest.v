# concurrency tests

[1 [10 <] [ dup puts receive puts '>consumer>' puts succ ] while] fork [id] let

1 [10 <] [ dup '<producer<' unit cons id send succ ] while
