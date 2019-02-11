package org.ores.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class NeoConcat {
  
  static <T> List<T> flatten(int desiredDepth, int currentDepth, Collection<T> results) {
    
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
  
  static <T> List<T> concatenate(List<T> results) {
    
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
