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
  
  @Test
  public void testQueue(TestContext tc) {
    
    Async z = tc.async();

//    Queue q = new Queue<Integer>((task, v) -> {
//      v.run(null, null);
//    });

//    Queue q = new Queue<Integer,Integer>(1, new ITaskHandler<Integer,Integer>() {
//      @Override
//      public void run(Task<Integer,Integer> t, IAsyncErrFirstCb<Integer> v) {
//            v.run(null,5);
//      }
//    });

//    context.runOnContext(v1 -> {
    
    var c = new ZoomCounter();
    
    synchronized (Asyncc.sync) {
      
      var q = new NeoQueue<Integer, Integer>(1, (task, v) -> {
        v.done(null, task.getValue() * 2 + 2);
      });
      
      System.out.println("The concurrency is: " + q.getConcurrency());
      
      q.onSaturated(queue -> {
        System.out.println("saturated");
      });
      
      q.onUnsaturated(queue -> {
        System.out.println("unsaturated");
      });
      
      q.onDrain(queue -> {
        System.out.println("Calling zz complete" + "/" + c.increment() + "/" + queue.getOnDrainCbs().size());
//      z.complete();
      });
      
      q.push(new NeoQueue.Task<>(3, (err, v) -> {
        log.debug((String) err, v);
      }));
      
      q.push(new NeoQueue.Task<>(5, (err, v) -> {
        log.debug((String) err, v);
      }));
      
      q.push(new NeoQueue.Task<>(5, (err, v) -> {
        log.debug((String) err, v);
      }));
      
      new Thread(() -> {
        
        try {
          Thread.sleep(100);
        } catch (Exception err) {
          log.debug(err.toString());
        }
        
        q.push(new NeoQueue.Task<>(3, (err, v) -> {
          log.debug((String) err, v);
        }));
        
        q.onDrain(queue -> {
          System.out.println("Calling zz 2 complete" + "/" + c.increment() + "/" + queue.getOnDrainCbs().size());
          z.complete();
        });
        
        
      }).start();
      
    }

//    });
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
        "foo", new NeoInject.Task<>(
          Set.of("star"),
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
  
  
  @Test
  public void testMap1(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Integer, Object>Map(
      
      asList(1, 2, 3),
      
      (kv, cb) -> {
        
        cb.done(null, kv.value + 2);
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
    
    Asyncc.<Integer, Integer, Object>MapSeries(List.of(3, 4, 5),
      
      (k, v) -> {
        v.done(null, 2 + k.value);
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
    
    Asyncc.<Integer, Integer, Object>MapLimit(3, List.of(3, 4, 5),
      
      (k, v) -> {
        v.done(null, 2 + k.value);
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
  public void testParallel(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Parallel(asList(
      
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
  public void testWaterfall(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Waterfall(
      
      v -> {
        v.set("stank", "kovich");
        v.map.put("foo", "bar");
        v.done(null);
      },
      
      v -> {
        var x = v.get("foo");
        tc.assertEquals(v.map.get("foo"), "bar");
        v.done(null, null);
      },
      
      v -> {
        tc.assertEquals(v.get("stank"), "kovich");
        v.done(null, "z", "zz");
      },
      
      v -> {
        tc.assertEquals(v.map.get("foo"), "bar");
        tc.assertEquals(v.map.get("z"), "zz");
        v.done(null, null);
      },
      
      (e, results) -> {
        
        
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
  
  @Test
  public void testReduce(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.Reduce(List.of(1,2,3), (r, v) -> {
      
      v.done(null, (Integer)r.prev + (Integer)r.curr);
      
    }, (err, result) -> {
      
      System.out.println("The result:");
      System.out.println(result);
      tc.assertEquals(result,6);
      z.complete();
      
    });
    
    
  }
  
  @Test
  public void testReduceInitialVal(TestContext tc) {
    
    Async z = tc.async();
    
   Asyncc.Reduce(1, List.of(1,2,3), (r, v) -> {
   
      v.done(null, (Integer)r.prev + (Integer)r.curr);
   
   }, (err, result) -> {
     
     System.out.println("The result:");
     System.out.println(result);
     tc.assertEquals(result,7);
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
