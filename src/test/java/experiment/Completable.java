package experiment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

public class Completable {
  
  private static ExecutorService executor = Executors.newFixedThreadPool(1);//creating a pool of 5 threads
  
  public static void main(String[] args){
  
  out.println("1");
  
  CompletableFuture.runAsync(() -> {
    
    out.println("2");
  
  }, executor);
  
  out.println("3");
  
  }
}
