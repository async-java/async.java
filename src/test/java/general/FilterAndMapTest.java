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

import static org.ores.async.Asyncc.Overloader.GENERIC;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

@RunWith(VertxUnitRunner.class)
public class FilterAndMapTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void runCompose0(TestContext tc) {
    
    Async v = tc.async();
    
    var x = Asyncc.Each(List.of(1, 2, 3), (val, cb) -> {
      cb.done(null);
    });
    
    x.run((err) -> {
      assert err == null : "Err should be null";
      v.complete();
    });
    
  }
  
  @Test
  public void runCompose1(TestContext tc) {
    
    Async v = tc.async();
    
    var eacher =  Asyncc.Each(GENERIC, List.of(3, 4, 5), (val, cb) -> {
      cb.done(null);
    });
    
    var x = Asyncc.Each(List.of(1, 2, 3), eacher);
    
    x.run((err) -> {
      
      assert err == null : err.toString();
      v.complete();
    });
    
  }
  
  @Test
  public void runCompose2(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Parallel(
      
      z -> {
        z.done(null, null);
      },
      
      Asyncc.FilterMap(List.of(1, 2, 3), (x, cb) -> {
        
        if (x == 2) {
          cb.discard();
        }
        
        cb.done(null, x);
        
      }),
      
      Asyncc.<Integer, Integer, Object>FilterMap(List.of(1, 2, 3), (x, cb) -> {
        
        Asyncc.<Integer, Object>Parallel(
          z -> {
            z.done(null, 4);
          },
          z -> {
            z.done(null, 5);
          },
          (Asyncc.IAsyncCallback) cb);
        
      }),
      
      (err, results) -> {
        assert err == null : "Err should be null";
        System.out.println(results.toString());
        v.complete();
      }
    
    );
    
  }
  
  @Test
  public void doStuff(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.FilterMap(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 2) {
        cb.discard();
      }
      
      cb.done(null, x);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println(results.toString());
      v.complete();
      
    });
  }
  
  @Test
  public void runEach(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.FilterMap(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 2) {
        cb.discard();
      }
      
      cb.done(null, x);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println(results.toString());
      v.complete();
      
    });
    
  }
  
  @Test
  public void runComposed0(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.FilterMap(Map.of(1, "foo", 2, "bar").entrySet(), (x, cb) -> {
      
      if (x.getValue().equals("bar")) {
        cb.discard();
      }
      
      cb.done(null, x);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println(results.toString());
      v.complete();
      
    });
    
  }
  
  @Test
  public void runComposed1(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.FilterMap(Map.ofEntries(Map.entry("a", 1), Map.entry("b", 2)).entrySet(), (x, cb) -> {
      
      cb.done(null, x);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println(results.toString());
      v.complete();
      
    });
  }
  
  @Test
  public void testMap1(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Integer, Object>FilterMap(
      
      asList(1, 2, 3),
      
      (v, cb) -> {
        
        if (v == 2) {
          cb.discard();
        }
        cb.done(null, v + 2);
//        cb.run("foo", kv.value + 2);
      },
      
      (e, results) -> {
        
        System.out.println(results.toString());
        
        if (e != null) {
          throw new Error(e.toString());
        } else {
          z.complete();
        }
        
      });
  }
  
  @Test
  public void testMapSeries1(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Integer, Object>FilterMapSeries(List.of(3, 4, 5),
      
      (item, v) -> {
        v.done(null, 2 + item);
      },
      
      (e, results) -> {
        
        System.out.println(results.toString());
        
        if (e != null) {
          z.complete();
        } else {
          z.complete();
        }
        
      });
  }
  
  @Test
  public void testMapLimit1(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Integer, Object>FilterMapLimit(3, List.of(3, 4, 5),
      
      (k, v) -> {
        v.done(null, 2 + k);
      },
      
      (e, results) -> {
        
        System.out.println(results.toString());
        
        if (e != null) {
          z.complete();
        } else {
          z.complete();
        }
        
      });
  }
  
}
