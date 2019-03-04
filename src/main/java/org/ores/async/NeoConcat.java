package org.ores.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 **/
class NeoConcat {
  
  static <T> List<T> flatten(final int desiredDepth, int currentDepth, final Collection<T> results) {
    
    if (currentDepth >= desiredDepth) {
      return (List) results;
    }
    
    ArrayList<T> concatenated = new ArrayList<>();
    
    for (T l : results) {
      
      if(l instanceof Collection){
        concatenated.addAll(flatten(desiredDepth, ++currentDepth, (Collection) l));
      }
      else{
        concatenated.add(l);
      }
    }
    
    return concatenated;
  }
  
  static <T> List<T> concatenate(final List<T> results) {
    
    ArrayList<T> concatenated = new ArrayList<>();
    
    for (T l : results) {
      if(l instanceof Collection){
        concatenated.addAll((Collection)l);
      } else{
        concatenated.add(l);
      }
    }
    
    return concatenated;
  }
  
  
}
