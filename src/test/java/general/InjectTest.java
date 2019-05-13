package general;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ores.async.Asyncc;
import org.ores.async.NeoInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RunWith(VertxUnitRunner.class)
public class InjectTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void testInjectCompose(TestContext tc) {
    
    Async z = tc.async();
    
    var inject = Asyncc.Inject(
      
      v -> {
        return Map.entry("xxx", new NeoInject.Task<>(y -> {
          y.done(null, v);
        }));
      },
      
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
    
    inject.run("zoom", (err, results) -> {
      assert err == null : err.toString();
      System.out.println(results);
      z.complete();
    });
    
  }
  
  @Test
  public void testInjectCircular(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Inject(
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
      
      ),

//      new Asyncc.IAsyncCallback<Map<String, Object>, Object>() {
//        @Override
//        public void done(Object o, Map<String, Object> v) {
//
//        }
//      }
      (err, results) -> {
        System.out.println(results);
        z.complete();
      }
    );
    
  }
  
  @Test
  public void testInjectSimple(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Object, Object>Inject(
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
          synchronized (System.out) {
            System.out.println("Here is star:");
            System.out.println((Integer) v.get("star"));
          }
          v.done(null, 3);
        }),
        
        "bar", new NeoInject.Task<>("foo", v -> {
          Object foo = v.get("foo");
          synchronized (System.out) {
            System.out.println("foo:");
            System.out.println(foo);
          }
          v.done(null, 5);
        })
      
      ),
      (err, results) -> {
        System.out.println(results);
        z.complete();
      }
    );
    
  }
  
  @Test
  public void testInjectComposed(TestContext tc) {
    
    Async z = tc.async();
    
    var mustafa = Asyncc.Inject(Map.of("fizz", new NeoInject.Task<>(v -> {
    
    })));
    
    Asyncc.<Object, Object>Inject(
      
      Map.of(
        "star", new NeoInject.Task<>(Set.of("bar"), v -> {
          Object foo = v.get("foo");
          Object bar = v.get("bar");
          System.out.println("foo:");
          System.out.println(foo);
          System.out.println("bar:");
          System.out.println(bar);
          v.done(null, 7);
        }),
        "foo", new NeoInject.Task<>(Set.of("star"), v -> {
          
          Asyncc.Inject(Map.of("dage", new NeoInject.Task<>(x -> {
              x.done(null, null);
            })),
            v::done);
          
        }),
        "bar", new NeoInject.Task<>(Set.of(), v -> {
          Object foo = v.get("foo");
          System.out.println("foo:");
          System.out.println(foo);
          v.done(null, 5);
        })
      
      ),
      (err, results) -> {
        System.out.println(results);
        z.complete();
      }
    );
    
  }
  
  @Test
  public void testInject(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Object>Inject(
      
      Map.of(
        "star", new NeoInject.Task<>(Set.of("bar"), v -> {
          Object foo = v.get("foo");
          Object bar = v.get("bar");
          System.out.println("foo:");
          System.out.println(foo);
          System.out.println("bar:");
          System.out.println(bar);
          v.done(null, 7);
        }),
        "foo", new NeoInject.Task<>(Set.of("star"),
          v -> {
            v.done(null, 3);
            
          }
        ),
        "bar", new NeoInject.Task<>(Set.of(), v -> {
          Object foo = v.get("foo");
          System.out.println("foo:");
          System.out.println(foo);
          v.done(null, 5);
        })
      
      ),
      (err, results) -> {
        System.out.println(results);
        z.complete();
      }
    );
    
  }
  
}
