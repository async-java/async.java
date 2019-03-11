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
public class ParallelTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
    
    System.out.println("here is the before hook");
    
  }
  
  @Test
  public void testParallel(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(
      
      v -> {
        
        v.done(null, null);
      }, (e, results) -> {
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
  @Test
  public void testParallelComposed(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(
      
      Asyncc.Parallel(t -> {
          t.done(null, 5);
        },
        t -> {
          t.done(null, 8);
        }),
      
      v -> {
        
        v.done(null, null);
      }
    
    , (e, results) -> {
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
  @Test
  public void testParallelLimitMap(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Object>ParallelLimit(3, Map.of(
      
      "foo", v -> {
        v.done(null, 2);
      },
      
      "bar", v -> {
        v.done(null, 3);
      }
    
    ), (e, results) -> {
      
      System.out.println(results.toString());
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
  @Test
  public void testParallelMap(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Object>Parallel(Map.of(
      
      "foo", v -> {
        v.done(null, 2);
      },
      
      "bar", v -> {
        v.done(null, 3);
      }
    
    ), (e, results) -> {
      
      System.out.println(results.toString());
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
  @Test
  public void testParallelLimit(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.ParallelLimit(4, asList(
      
      v -> {
        v.done(null, 1);
      },
      
      v -> {
        v.done(null, 2);
      },
      
      v -> {
        
        new Thread(() -> {
          try {
            Thread.sleep(3);
          } catch (Exception e) {
            System.out.println(e);
          }
          v.done(null, 3);
        })
          .start();
      },
      
      v -> {
        new Thread(() -> {
          try {
            Thread.sleep(3);
          } catch (Exception e) {
            System.out.println(e);
          }
          v.done(null, 4);
        })
          .start();
      },
      
      v -> {
        v.done(null, 5);
      },
      
      v -> {
        v.done(null, 6);
      },
      
      v -> {
        new Thread(() -> {
          try {
            Thread.sleep(3);
          } catch (Exception e) {
            System.out.println(e);
          }
          
          v.done(null, 7);
        })
          .start();
      },
      
      v -> {
        v.done(null, 8);
      }
    
    ), (e, results) -> {
  
      assert e == null : e.toString();
  
      System.out.println("DDDDDDDAMN results:");
      System.out.println(results.toString());
      
      z.complete();
      
    });
  }
  
}
