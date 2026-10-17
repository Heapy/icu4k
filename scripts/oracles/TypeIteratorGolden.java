import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.RangeValueIterator;
public class TypeIteratorGolden {public static void main(String[]args){
 var iterator=UCharacter.getTypeIterator();var element=new RangeValueIterator.Element();int count=0,hash=1;
 while(iterator.next(element)){count++;hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value;}
 System.out.println(count+"|"+hash+"|"+element.limit);
}}
