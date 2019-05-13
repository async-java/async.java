package general;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ores.async.Asyncc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@RunWith(VertxUnitRunner.class)
public class WhilstTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
    System.out.println("Running whilst test.");
  }
  
  @Test
  public void testWhilst(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(() -> false, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 0 : "results size should be zero.";
      assert c.count == 0 : "count should be zero.";
      z.complete();
      
    });
  }
  
  @Test
  public void testDoWhilst(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(() -> false, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 1 : "results size should be one.";
      assert c.count == 1 : "count should be one.";
      z.complete();
      
    });
  }
  
  @Test
  public void testWhilstAsyncTest(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(test -> test.done(null, false), t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 0 : "results size should be zero.";
      assert c.count == 0 : "count should be zero.";
      z.complete();
      
    });
  }
  
  @Test
  public void testDoWhilstAsyncTest(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(test -> test.done(null, false), t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 1 : "results size should be one.";
      assert c.count == 1 : "count should be one.";
      z.complete();
      
    });
  }
  
  @Test
  public void testWhilstAsyncTest2(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(
      test -> {
        CompletableFuture.runAsync(() -> {
          test.done(null, false);
        });
      },
      task -> {
        c.count++;
        task.done(null, null);
        
      }, (err, results) -> {
        
        assert err == null : err.toString();
        assert results.size() == 0 : "results size should be zero.";
        assert c.count == 0 : "count should be zero.";
        z.complete();
        
      });
  }
  
  @Test
  public void testDoWhilstAsyncTest2(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(
      test -> {
        CompletableFuture.runAsync(() -> {
          test.done(null, false);
        });
      },
      
      task -> {
        c.count++;
        task.done(null, null);
        
      }, (err, results) -> {
        
        assert err == null : err.toString();
        assert results.size() == 1 : "results size should be one.";
        assert c.count == 1 : "count should be one.";
        z.complete();
        
      });
  }
  
  @Test
  public void testWhilstWithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(() -> c.count < 3, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      System.out.println(results.toString());
      assert results.size() == 3 : "results size should be 2.";
      assert c.count == 3 : "count should be 2.";
      z.complete();
      
    });
  }
  
  @Test
  public void testDoWhilstWithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(() -> c.count < 3, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 3 : "results size should be one.";
      assert c.count == 3 : "count should be one.";
      z.complete();
      
    });
  }
  
  @Test
  public void testWhilstAsyncTestWithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(test -> {
      
      test.done(null, c.count < 3);
      
    }, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 3 : "results size should be zero.";
      assert c.count == 3 : "count should be zero.";
      z.complete();
      
    });
  }
  
  @Test
  public void testDoWhilstAsyncTestWithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(test -> {
      
      test.done(null, c.count < 5);
      
    }, t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert results.size() == 5 : "results size should be one.";
      assert c.count == 5 : "count should be one.";
      z.complete();
      
    });
  }
  
  @Test
  public void testWhilstAsyncTest2WithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.Whilst(
      test -> {
        CompletableFuture.runAsync(() -> {
          test.done(null, c.count < 5);
        });
      },
      task -> {
        c.count++;
        task.done(null, null);
        
      }, (err, results) -> {
        
        assert err == null : err.toString();
        assert results.size() == 5 : "results size should be zero.";
        assert c.count == 5 : "count should be zero.";
        z.complete();
        
      });
  }
  
  @Test
  public void testDoWhilstAsyncTest2WithTrue(TestContext tc) {
    
    Async z = tc.async();
    
    var c = new Object() {
      int count = 0;
    };
    
    Asyncc.DoWhilst(
      test -> {
        CompletableFuture.runAsync(() -> {
          test.done(null, c.count < 3);
        });
      },
      
      task -> {
        c.count++;
        task.done(null, null);
        
      }, (err, results) -> {
        
        assert err == null : err.toString();
        assert results.size() == 3 : "results size should be one.";
        assert c.count == 3 : "count should be one.";
        z.complete();
        
      });
  }
}
