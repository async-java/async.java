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
import org.ores.async.NeoInjectI;
import org.ores.async.NeoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

@RunWith(VertxUnitRunner.class)
public class GroupByTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void testInjectCompose(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.GroupBy(List.of(1, 2, 3), (v, t) -> {
      
      t.done(null, v.toString());
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      System.out.println(results.toString());
      z.complete();
    });
    
  }
  
  @Test
  public void testInjectCircular(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.GroupBy(List.of(1, 2, 3), (v, t) -> {
      
      t.done(null, Integer.valueOf(2).toString());
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      System.out.println(results.toString());
      z.complete();
    });
    
  }
  
}
