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
public class EachTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
    System.out.println("Running before hook in each-test.");
  }
  
  @Test
  public void runEachCompose(TestContext tc) {
    
    Async v = tc.async();
    
    var eacher = Asyncc.Each((m, x, cb) -> {
        cb.done(null);
      },
      List.of(1, 2, 3), (x, cb) -> {
        
        cb.done(null);
        
      });
    
    var x = Asyncc.Each(List.of(5, 6, 7), eacher);
    
    x.run(err -> {
      assert err == null : err.toString();
      v.complete();
    });
    
  }
  
  @Test
  public void runEach(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Each(List.of(1, 2, 3), (x, cb) -> {
      
      cb.done(null);
      
    }, err -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
    
  }
  
  @Test
  public void runEachWithInject(TestContext tc) {
    
    Async x = tc.async();
    
    var inject = Asyncc.Inject(
      
      v -> Map.entry("xxx", new NeoInject.Task<>(y -> {
        y.done(null, v);
      })),
      
      Map.of(
        
        "star", new NeoInject.Task<>(v -> {
          Object foo = v.get("foo");
          Object bar = v.get("bar");
          System.out.println("foo:");
          System.out.println(foo);
          System.out.println("bar:");
          System.out.println(bar);
          v.done(null, 7);
        }),
        
        "foo", new NeoInject.Task<>("star", v -> {
          v.done(null, 3);
        }),
        
        "bar", new NeoInject.Task<>(Set.of("foo"), v -> {
          Object foo = v.get("foo");
          System.out.println("foo:");
          System.out.println(foo);
          v.done(null, 5);
        })
      
      ));
    
    Asyncc.Each(List.of(1, 2, 3), inject, err -> {
      
      assert err == null : err.toString();
      x.complete();
      
    });
    
  }
  
  @Test
  public void runEachWithWaterfall(TestContext tc) {
    
    Async v = tc.async();
    
    var water = Asyncc.Waterfall((z, t) -> {
      
      t.set("init", z);
      t.done(null);
      
    }, t -> {
      
      t.set("bar", t.get("init"));
      t.done(null);
      
    });
    
    Asyncc.Each(List.of(1, 2, 3), water, err -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
    
  }
  
  @Test
  public void eachWithEntrySet(TestContext tc) {
    
    Async v = tc.async();
    
    var z = Map.of("foo", "bar").entrySet();
    
    Asyncc.Each(z, (x, cb) -> {
      
      cb.done(null);
      
    }, err -> {
      
      assert err == null : err.toString();
      v.complete();
      
    });
    
  }
  
  @Test
  public void eachWithMap(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.<Map.Entry<String, Integer>, Object>Each(Map.of("foo", 5), (x, cb) -> {
      
      var z = x.getValue();
      cb.done(null);
      
    }, err -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
    
  }
  
}
