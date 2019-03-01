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
public class SeriesTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
    final Map<String, Object> results = new HashMap<>();
    final List<Object> x = List.of(results.values());
  }
  
  @Test
  public void testSeriesCompose(TestContext tc) {
    Async z = tc.async();
    
    var mapper = (Asyncc.IMapper) Asyncc.Series((v, cb) -> {
        
        cb.done(null, (Integer) v + 3);
      },
      
      v -> {
        v.done(null, 3);
        
      });
    
    Asyncc.Map(List.of(1, 2, 3), mapper, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      z.complete();
    });
    
  }
  
  @Test
  public void testSeries(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Series(asList(
      
      v -> {
        new Thread(() -> {
          try {
            Thread.sleep(500);
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
            Thread.sleep(500);
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
            Thread.sleep(500);
          } catch (Exception e) {
            System.out.println(e);
          }
          
          v.done(null, null);
        })
          .start();
      }
    
    ), (e, results) -> {
      
      if (e != null) {
        z.complete();
      } else {
        z.complete();
      }
      
    });
  }
  
}
