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
public class RaceTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void shouldBeFirst(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(1, 2, 3), (x, cb) -> {
      
      cb.done(null, cb.setValue(1));
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 1) : "Should be 1";
      v.complete();
      
    });
    
  }
  
  @Test
  public void shouldBeSecond(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 2) {
        cb.done(null, cb.setValue(true, x));
        return;
      }
      
      cb.done(null, cb.setValue(false, x));
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 2) : "Should be 2";
      v.complete();
      
    });
    
  }
  
  
  @Test
  public void shouldBeThird(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 3) {
        cb.done(null, cb.setValue(true, x));
        return;
      }
      
      cb.done(null, cb.setValue(false, x));
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 3) : "Should be 3";
      v.complete();
      
    });
    
  }
  
  
  @Test
  public void shouldBeFirstTask(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(cb -> {
      
      cb.done(null, cb.setValue(1));
      
    }), (err, results) -> {
      
      System.out.println(err);
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 1) : "Should be 1";
      v.complete();
      
    });
    
  }
  
  @Test
  public void shouldBeSecondTask(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 2) {
        cb.done(null, cb.setValue(true, x));
        return;
      }
      
      cb.done(null, cb.setValue(false, x));
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 2) : "Should be 2";
      v.complete();
      
    });
    
  }
  
  
  @Test
  public void shouldBeThirdTask(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Race(List.of(1, 2, 3), (x, cb) -> {
      
      if (x == 3) {
        cb.done(null, cb.setValue(true, x));
        return;
      }
      
      cb.done(null, cb.setValue(false, x));
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      assert Objects.equals(results, 3) : "Should be 3";
      v.complete();
      
    });
    
  }
}
