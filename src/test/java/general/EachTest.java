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
  public void runEach(TestContext tc) {
    
    Async v = tc.async();

    Asyncc.Each(List.of(1,2,3), (x,cb) -> {
      
      System.out.println("We in the runeach 1");
      cb.done(null);
    
    }, err -> {
    
      assert err == null: "Err should be null";
      v.complete();
    
    });
    
  }
  
  @Test
  public void runComposed0(TestContext tc) {
    
    Async v = tc.async();
  
    Asyncc.Each(Map.of("foo","bar").entrySet(), (x,cb) -> {
  
      System.out.println("We in the runeach 2");
      cb.done(null);
    
    }, err -> {
    
      assert err == null: "Err should be null";
      v.complete();
    
    });
    
  }
  
  @Test
  public void runComposed1(TestContext tc) {
  
    Async v = tc.async();
  
    System.out.println("We in the 3");
  
    v.complete();
    
  }
  

  
}
