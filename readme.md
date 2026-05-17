
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


## Installation

### Maven Central (recommended)

```xml
<dependency>
  <groupId>io.github.async-java</groupId>
  <artifactId>async-java</artifactId>
  <version>0.2.0</version>
</dependency>
```

> The legacy `com.oresoftware:async.0.1:0.1.1012` artifact remains on Central
> for backwards compatibility but is frozen at the 2019 source. New code
> should use `io.github.async-java:async-java`.

### Gradle

```kotlin
implementation("io.github.async-java:async-java:0.2.0")
```

### Snapshot builds

Snapshot versions (`0.x.y-SNAPSHOT`) are published to the Sonatype Central
snapshot endpoint:

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>
```

### Arbitrary git refs (JitPack)

Any git tag, branch, or commit SHA is buildable on demand by
[JitPack](https://jitpack.io):

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.async-java</groupId>
  <artifactId>async.java</artifactId>
  <version>v0.2.0</version>  <!-- or a branch name, or a 10-char commit SHA -->
</dependency>
```

### Maintainers

See [RELEASING.md](RELEASING.md) for the steps to cut a new release.


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

* [Series](https://async-java.github.io/org/ores/async/Asyncc.html#Concat(int,java.util.List,org.ores.async.Asyncc.Mapper,org.ores.async.Asyncc.IAsyncCallback) "(target|_blank)")
* [Parallel](https://www.google.com)/[ParallelLimit](https://www.google.com) 
* <a href="https://async-java.github.io/org/ores/async/Asyncc.html#Waterfall(java.util.List,org.ores.async.Asyncc.IAsyncCallback)" target="_blank">Waterfall</a>
* [Inject](https://async-java.github.io/org/ores/async/Asyncc.html#Concat(int,java.util.List,org.ores.async.Asyncc.Mapper,org.ores.async.Asyncc.IAsyncCallback) "(target|_blank)") - (most recommended)

### Map/Filter/Reduce/Each

* [Map](https://www.google.com "(target|_blank)"), [MapSeries](https://www.google.com), [MapLimit](https://www.google.com)
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




2. `async.waterfall` is considered harmful. We use a ma



3. We have a shortCircuited boolean available to check if we can end early.