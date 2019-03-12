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

import static org.ores.async.NeoWaterfallI.AsyncTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;


@RunWith(VertxUnitRunner.class)
public class WaterfallTest {

  final static Logger log = LoggerFactory.getLogger(AsyncTest.class);

  @Before
  public void onBefore() {

  }

  @Test
  public void testWaterfallCompose0(TestContext tc) {

    Async z = tc.async();

    var t = Asyncc.Waterfall(

      (x, v) -> {
        v.set("begin", x);
        v.done(null);
      },

      v -> {
        v.set("stank", "kovich");
        v.map.put("foo", "bar");
        v.done(null);
      });

    t.map(3, (err, results) -> {
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      z.complete();
    });
  }


  @Test
  public void testWaterfallCompose(TestContext tc) {

    Async z = tc.async();

    var t = Asyncc.Waterfall(

      (x, v) -> {
        v.set("begin", x);
        v.done(null);
      },

      v -> {

        v.set("stank", "kovich");
        v.map.put("foo", "bar");
        v.done(null);
      });


//    t.map(3, (err, results) -> {
//
//    });

    Asyncc.Map(List.of(1, 2, 3), t, (err, results) -> {
      assert err == null : "Err should be null";
      System.out.println("Results: " + results);
      z.complete();
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
  public void speedTest1(TestContext tc) {

    Async z = tc.async();
    List<AsyncTask<Map<String, Object>, Object>> a = new ArrayList<>();

    for (int i = 0; i < 1000000; i++) {
      
      final int index = i;
      
      a.add(v -> {

        if (index % 15 != 0) {
          v.done(null);
          return;
        }

        CompletableFuture.runAsync(() -> {
          v.done(null);
        });

      });
      
    }


    final long startTime = System.currentTimeMillis();

    Asyncc.Waterfall(
      a,

      (e, results) -> {

        final long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime));
        assert e == null : e.toString();
        z.complete();

      });


  }


  @Test
  public void speedTest2(TestContext tc) {

    Async z = tc.async();
    List<AsyncTask<Map<String, Object>, Object>> a = new ArrayList<>();

    for (int i = 0; i < 1000000; i++) {
      
      final int index = i;
      
      a.add(v -> {

        if (index % 15 != 0) {
          v.done(null);
          return;
        }

        CompletableFuture.runAsync(() -> {
          v.done(null);
        });

      });
      
    }

    final long startTime = System.currentTimeMillis();

    Asyncc.Waterfall2(a, (e, results) -> {

      final long endTime = System.currentTimeMillis();
      System.out.println("Total execution time: " + (endTime - startTime));
      assert e == null : e.toString();
      z.complete();

    });


  }
}
