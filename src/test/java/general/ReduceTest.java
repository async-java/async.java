package general;

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

import static java.util.Arrays.asList;

@RunWith(VertxUnitRunner.class)
public class ReduceTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  public <T> void run(T x) {
    
    if (x instanceof HashMap) {
      ((HashMap) x).put("foo", "bar");
    }
    
  }
  
  @Test
  public void testReduce(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Reduce(List.of(1, 2, 3), (prev, curr, v) -> {
      
      v.done(null, (Integer) prev + curr);
      
    }, (err, result) -> {
      
      System.out.println("The result:");
      System.out.println(result);
      tc.assertEquals(result, 6);
      z.complete();
      
    });
    
  }
  
  @Test
  public void testReduceInitialVal(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Reduce(1, List.of(1, 2, 3), (prev, curr, v) -> {
      
      v.done(null, (Integer) prev + curr);
      
    }, (err, result) -> {
      
      System.out.println("The result:");
      System.out.println(result);
      tc.assertEquals(result, 7);
      z.complete();
      
    });
    
  }
  
}
