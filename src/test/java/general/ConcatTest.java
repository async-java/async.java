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
public class ConcatTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void testConcat(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Concat(List.of(
      
      v -> {
        v.done(null, List.of(1, 2, 3));
      },
      
      v -> {
        v.done(null, List.of(4, 5, 6));
      }
    
    ), (err, results) -> {
      System.out.println(results);
      z.complete();
    });
    
  }
  
  @Test
  public void testConcatDeep(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.ConcatDeep(List.of(
      
      v -> {
        v.done(null, List.of(1, 2, 3));
      },
      
      v -> {
        v.done(null, List.of(4, 5, 6));
      },
      
      v -> {
        v.done(null, List.of(List.of(7, 8), List.of(List.of(9))));
      },
      
      v -> {
        v.done(null, List.of(List.of(10, 11), Arrays.asList(12, 13, 14, Arrays.asList(15, 16))));
      }
    
    ), (err, results) -> {
      System.out.println(results);
      z.complete();
    });
    
  }
  
}
