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

@RunWith(VertxUnitRunner.class)
public class TimesTest {
  
  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);
  
  @Before
  public void onBefore() {
  
  }
  
  @Test
  public void runCompose1(TestContext tc) {
    
    Async v = tc.async();
    
    var o = new Object(){
      public Integer count = 0;
    };
    
    Asyncc.Times(5, (n, x) -> {
      
      o.count++;
      x.done(null,null);
    
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert o.count == 5 : "count is wrong.";
      v.complete();
    });
    
  }
  
  
  @Test
  public void runCompose2(TestContext tc) {
    
    Async v = tc.async();
    
    var o = new Object(){
      public Integer count = 0;
    };
    
    Asyncc.Times(5, (n, x) -> {
      
      o.count++;
      x.done(null,n);
      
    }, (err, results) -> {
      
      assert err == null : err.toString();
      assert o.count == 5 : "count is wrong.";
      System.out.println(results.toString());
      v.complete();
    });
    
  }
  
}
