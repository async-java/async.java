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

class ZoomCounter {
  
  public Integer val = 0;
  
  public ZoomCounter() {
  
  }
  
  public Integer getVal() {
    return this.val;
  }
  
  public Integer increment() {
    return ++this.val;
  }
}

interface UsesRunnable {
  Integer uses(Integer y);
}

interface Foo {
  abstract UsesRunnable prepareFunnable(Integer x);
}

interface Funnable {
  Integer ffun(Integer x, Integer y);
}

class HandleRunnable {

//  public UsesRunnable accept(Runnable r) {
//
//    return new UsesRunnable() {
//      @Override
//      public void uses() {
//        r.run();
//      }
//    };
//
//  }
  
  public Foo accept(Funnable r) {
    return x -> y -> r.ffun(x,y);
  }
  
}

class AcceptRunnable implements Asyncc.IAcceptRunnable {
  
  Vertx vertx;
  Context context;
  
  
  public AcceptRunnable(Vertx v, Context c) {
    this.vertx = v;
    this.context = c;
  }
  
  @Override
  public void accept(Runnable r) {
    
    context.runOnContext(v -> {
      r.run();
    });
  }
  
  
}

@RunWith(VertxUnitRunner.class)
public class AsyncTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  static Vertx vertx = Vertx.vertx();
  static Context context = vertx.getOrCreateContext();
  static AcceptRunnable ar = new AcceptRunnable(vertx, context);
  
  @Before
  public void onBefore() {

//    ar = new AcceptRunnable(vertx, context);
    Asyncc.setOnNext(ar);
    
    var h = new HandleRunnable().accept((x,y) -> {
      System.out.println("Here is our int: " + x);
      return x*y;
    });
    
    assert h.prepareFunnable(5).uses(8) == 40: "Should be 40";
    
    assert h.prepareFunnable(6).uses(7) == 42: "Should be 42";
    
    assert h.prepareFunnable(7).uses(8) == 56: "Should be 56";


//    vertx.runOnContext(v -> {
//      log.info("WUT THE FAK");
//    });

//    context.runOnContext(v1 -> {
//      System.out.println("One");
//      context.runOnContext(v2 -> {
//        System.out.println("Two");
//      });
//      System.out.println("Three");
//    });
  }
  
  public static Asyncc.AsyncTask<Object, Object> zoom1() {
    return v -> {
      v.done(null, null);
    };
  }
  
  public static <T, E> Asyncc.AsyncTask<T, E> zoom() {
    return v -> {
      v.done(null, null);
    };
  }
  
  static interface Rinnable {
    void rin();
  }
  
  static class Mop<T, E> implements Asyncc.IAsyncCallback<T, E> {

//    public abstract void ran();
    
    Rinnable r;
    
    public Mop(Rinnable r) {
      this.r = r;
    }
    
    @Override
    public void done(E e, T v) {
      CompletableFuture.runAsync(() -> {
        this.r.rin();
      });
    }
  }
  
  static <T, E> Mip<T, E> makeMip(Asyncc.IAsyncCallback<T, E> r) {
    return new Mip<T, E>(r);
  }
  
  static class Mip<T, E> implements Asyncc.IAsyncCallback<T, E> {

//    public abstract void ran();
    
    Asyncc.IAsyncCallback<T, E> r;
    
    public Mip(Asyncc.IAsyncCallback<T, E> r) {
      this.r = r;
    }
    
    @Override
    public void done(E e, T v) {
      CompletableFuture.runAsync(() -> {
        this.r.done(e, v);
      });
    }
  }
  
  @Test
  public void runComposed00(TestContext tc) {
    
    Async v = tc.async();

//    v.fail();
    
    Asyncc.Series(asList(
      zoom(),
      zoom()),
      new Asyncc.IAsyncCallback<>() {
        @Override
        public void done(Object o, List<Object> v) {
        
        }
      });
    
    Asyncc.Series(
      zoom(),
      zoom(),
      new Mop<>(() -> {
      
      }));
    
    Asyncc.Series(
      zoom(),
      zoom(),
      zoom(),
      zoom(),
      zoom(),
      zoom(),
      makeMip((e, results) -> {
      
      
      }));
    
    Asyncc.Series(
      zoom(),
      zoom()
      ,
      (e, results) -> {
        v.complete();
      });
    
  }
  
  @Test
  public void runComposed0(TestContext tc) {
    
    Async v = tc.async();

//    var m =  new Asyncc.IAsyncCallback<List<Object>, Object>() {
//      @Override
//      public void done(Object e, List<Object> v) {
//
//      }
//    };
    
    Asyncc.Series(asList(
      zoom(),
      Asyncc.Parallel(asList(
        z -> {
          z.done(null, null);
        },
        zoom()
        )
      )),
//      new Asyncc.IAsyncCallback<List<T>, E>() {
//        @Override
//        public void done(E e, List<T> v) {
//
//        }
//      }
//    );
      (e, results) -> {
        
        v.complete();
      });
    
  }
  
  @Test
  public void runComposed1(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Series(asList(
      zoom(),
      Asyncc.Parallel(asList(
        z -> {
          z.done(null, null);
        },
        zoom()
        )
      )),
//      new Asyncc.IAsyncCallback<>() {
//        @Override
//        public void done(Object e, List<Object> v) {
//
//        }
//      }
//
//    );
      (e, results) -> {
        v.complete();
      });
    
  }
  
  @Test
  public void runComposed2(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(asList(
      
      Asyncc.Parallel(
        zoom()
      ),
      
      Asyncc.Series(
        zoom(),
        zoom()
      ),
      
      v -> {
        v.done(null, null);
      },
      
      zoom()
      ),

//      new Asyncc.IAsyncCallback<Object, Object>() {
//        @Override
//        public void done(Object e, Object v) {
//
//        }
//      });
      
      (e, results) -> {
        z.complete();
      });
    
  }
  
  @Test
  public void runComposed3(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(
      v -> {
        v.done(null, null);
      },
      
      zoom(),
      
      (e, results) -> {
        z.complete();
      });
    
  }

  

 

  
}
