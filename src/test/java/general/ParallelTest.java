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
  
  public interface IAsyncCallback<T, E> {
    void done(E e, T v);
  }
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
    
    IAsyncCallback<Optional,Object> cb = (e,v) -> {};
    
  }
  
  
  
  @Test
  public void testParallel(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(List.of(
      
      v -> {
        
        v.done(null, null);
      }
    
    ), (e, results) -> {
      
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
    
    Asyncc.Parallel(List.of(
      
      Asyncc.Parallel(t -> {
      
      }),
      
      v -> {
        
        v.done(null, null);
      }
    
    ), (e, results) -> {
      
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
        v.done(null, null);
      },
      
      v -> {
        v.done(null, null);
      },
      
      v -> {
        
        new Thread(() -> {
          try {
            Thread.sleep(3);
          } catch (Exception e) {
            System.out.println(e);
          }
          v.done(null, null);
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
          v.done(null, null);
        })
          .start();
      },
      
      v -> {
        v.done(null, null);
      },
      
      v -> {
        v.done(null, null);
      },
      
      v -> {
        new Thread(() -> {
          try {
            Thread.sleep(3);
          } catch (Exception e) {
            System.out.println(e);
          }
          
          v.done(null, null);
        })
          .start();
      },
      
      v -> {
        v.done(null, null);
      }
    
    ), (e, results) -> {
      
      System.out.println("DDDDDDDAMN");
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
}
