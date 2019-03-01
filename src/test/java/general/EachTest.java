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
  public void eachWithEntrySet(TestContext tc) {
    
    Async v = tc.async();
  
    var z = Map.of("foo","bar").entrySet();
    
    Asyncc.Each(z, (x,cb) -> {
      
      System.out.println("We in the runeach 2");
      cb.done(null);
    
    }, err -> {
    
      assert err == null: "Err should be null";
      v.complete();
    
    });
    
  }
  
  @Test
  public void eachWithMap(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.<Map.Entry<String,Integer>, Object>Each(Map.of("foo", Integer.valueOf(5)), (x,cb) -> {
      
      var z = ((Map.Entry)x).getValue();
      
      System.out.println("We in the runeach 2");
      cb.done(null);
      
    }, err -> {
      
      assert err == null: "Err should be null";
      v.complete();
      
    });
    
  }

  
}
