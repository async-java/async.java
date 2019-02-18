
# Asyncc.java

>
>  High-quality port of async.js to Java. </br>
>  Primarily for use with Vert.x. </br>
>  Uses async primitives (error-first callbacks), for performance and genericism. </br>
>
>  Adds nice structure to callback-passing-style codebases. 
>

#### Complete Documentation:
https://async-java.github.io/org/ores/async/Asyncc.html#method.summary


### Installation with Maven



### Simple example:

```java

import org.ores.async.Asyncc;

public void retrieveValue(){
  Asyncc.Parallel(t -> t.done(null,"foo"), (err, results) -> {
    
    
  });  
}


```



## Utility Methods
> Links to the documentation

### Control Flow

* [Series](https://www.google.com)
* [Parallel](https://www.google.com)/[ParallelLimit](https://www.google.com) 
* [Waterfall](https://www.google.com)
* [Inject](https://www.google.com) - (most recommended)

### Map/Filter/Reduce/Each

* [Map](https://www.google.com), [MapSeries](https://www.google.com), [MapLimit](https://www.google.com)
* [Filter](https://www.google.com), [FilterSeries](https://www.google.com), [FilterLimit](https://www.google.com)
* [Reduce](https://www.google.com) / [ReduceRight](https://www.google.com)
* [Each](https://www.google.com), [EachSeries](https://www.google.com), [EachLimit](https://www.google.com)


### Queue / Priority Queue

* [Queue](https://www.google.com)
* [PriorityQueue](https://www.google.com)


### Locking

* [Basic async locking](https://www.google.com)
> (Because the synchronized keyword blocks).



## Improvements and Quality

This library improves upon async.js. For those familiar, this library makes these improvements:


1. <i>Composability</i>. This is available because Java has method overloading and JS doesn't.

In JS:


With Java:




2. `async.waterfall` is considered harmful. We use a map, 