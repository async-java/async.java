
# Asyncc.java

>
>  Port of async.js to Java.
>  Primarily for use with Vert.x.
>  Uses async primitives (error-first callbacks)
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
