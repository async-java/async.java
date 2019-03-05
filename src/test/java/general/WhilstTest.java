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
      
      assert err == null: err.toString();
      assert results.size() == 0: "results size should be zero.";
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
      
      assert err == null: err.toString();
      assert results.size() == 1: "results size should be one.";
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
    
    Asyncc.Whilst(test -> test.done(null,false), t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null: err.toString();
      assert results.size() == 0: "results size should be zero.";
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
    
    Asyncc.DoWhilst(test -> test.done(null,false), t -> {
      
      c.count++;
      t.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null: err.toString();
      assert results.size() == 1: "results size should be one.";
      assert c.count == 1 : "count should be one.";
      z.complete();
      
    });
  }
}
