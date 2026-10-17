import com.ibm.icu.impl.*;
public class TrieGoldens {
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"rfc3491","rfc3530cs","rfc3530csci","rfc3530mixp","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci"}) {
   var bytes=ICUBinary.getRequiredData(name+".spp");var reader=new StringPrepDataReader(bytes);reader.readIndexes(16);var trie=new CharTrie(bytes,null);
   int points=1,bmp=1,lead=1,latin=1;
   for(int cp=-1;cp<=0x110000;cp++)points=31*points+trie.getCodePointValue(cp);
   for(int cp=0;cp<=65535;cp++){bmp=31*bmp+trie.getBMPValue((char)cp);lead=31*lead+trie.getLeadValue((char)cp);}
   for(int cp=0;cp<=255;cp++)latin=31*latin+trie.getLatin1LinearValue((char)cp);
   System.out.println(name+"|"+points+"|"+bmp+"|"+lead+"|"+latin+"|"+trie.getSerializedDataSize()+"|"+trie.isLatin1Linear());
  }
 }
}
