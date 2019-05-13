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

import java.util.*;

import static java.util.Arrays.asList;

@RunWith(VertxUnitRunner.class)
public class MapTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void runCompose1(TestContext tc) {
    
    Async v = tc.async();
    
    var mapper = Asyncc.Map((x, l, t) -> {
        
        l.add((Integer) x);
        t.done(null);
        
      },
      
      List.of(3, 4, 5),
      
      (val, cb) -> {
        cb.done(null, val);
      });
    
    var x = Asyncc.Map(List.of(1, 2, 3), mapper);
    
    x.run((err, results) -> {
      
      System.out.println(results.toString());
      assert err == null : err.toString();
      v.complete();
    });
    
  }
  
  @Test
  public void runEach(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Map(List.of(1, 2, 3), (x, cb) -> {
      
      cb.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
    
  }
  
  @Test
  public void runComposed0(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Map(Map.of("foo", "bar").entrySet(), (x, cb) -> {
      
      cb.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
    
  }
  
  @Test
  public void runComposed1(TestContext tc) {
    
    Async v = tc.async();
    
    Asyncc.Map(Map.ofEntries(Map.entry(1, 2)).entrySet(), (x, cb) -> {
      
      cb.done(null, null);
      
    }, (err, results) -> {
      
      assert err == null : "Err should be null";
      v.complete();
      
    });
  }
  
  @Test
  public void testMap1(TestContext tc) {
    
    Async z = tc.async();
    
    Asyncc.<Integer, Integer, Object>Map(
      
      asList(1, 2, 3),
      
      (v, cb) -> {
        
        cb.done(null, v + 2);
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
      
      (item, v) -> {
        v.done(null, 2 + item);
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
        v.done(null, 2 + k);
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
  
}
