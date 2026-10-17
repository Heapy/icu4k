import com.ibm.icu.impl.*;
import com.ibm.icu.util.RangeValueIterator;
public class TrieIteratorGoldens {
 public static void main(String[] args)throws Exception {
  for(String name:new String[]{"rfc3491","rfc3530cs","rfc3530csci","rfc3530mixp","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci"})for(int mode=0;mode<4;mode++) {
   var bytes=ICUBinary.getRequiredData(name+".spp");new StringPrepDataReader(bytes).readIndexes(16);var trie=new CharTrie(bytes,null);
   int operation=mode;int[] trace={0,1};
   var iterator=new TrieIterator(trie){protected int extract(int v){trace[0]++;trace[1]=31*trace[1]+v;return switch(operation){case 1->v&3;case 2->0;case 3->v>>>8;default->v;};}};
   var element=new RangeValueIterator.Element();int count=0,hash=1;
   while(iterator.next(element)){count++;hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value;}
   if(iterator.next(element))throw new AssertionError();
   iterator.reset();if(!iterator.next(element))throw new AssertionError();
   hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value;
   System.out.println(name+"|"+mode+"|"+count+"|"+hash+"|"+trace[0]+"|"+trace[1]);
  }
 }
}
