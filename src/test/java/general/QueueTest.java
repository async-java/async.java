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
public class QueueTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
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
  
}
