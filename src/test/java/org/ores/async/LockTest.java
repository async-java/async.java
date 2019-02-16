package org.ores.async;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ores.async.Asyncc;
import org.ores.async.NeoInject;
import org.ores.async.NeoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;



@RunWith(VertxUnitRunner.class)
public class LockTest {
  
  final static Logger log = LoggerFactory.getLogger(LockTest.class);

  
  @Before
  public void onBefore() {


  }
  

  
  @Test
  public void run(TestContext tc) {
  
    Async a = tc.async();
  
    var lock = new NeoLock("butt");
  
    Asyncc.Parallel(
      
      v -> lock.acquire((err, r) -> {
          System.out.println("BIG 1");
          CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS).execute(() -> {
            r.releaseLock();
            v.done(null, null);
          });
        }),
      
      v -> lock.acquire((err, r) -> {
          System.out.println("BIG 2");
          CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS).execute(() -> {
            r.releaseLock();
            v.done(null, null);
          });
        }),
      
      v -> lock.acquire((err, r) -> {
          System.out.println("BIG 3");
          CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS).execute(() -> {
            r.releaseLock();
            v.done(null, null);
          });
        }),
    
      (err, results) -> {
        a.complete();
      });
  
  }
  
}
