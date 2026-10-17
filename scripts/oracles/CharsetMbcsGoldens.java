import com.ibm.icu.text.*;
import java.lang.reflect.*;
public class CharsetMbcsGoldens {
 static String[] names={"CharsetRecog_sjis","CharsetRecog_big5","CharsetRecog_euc$CharsetRecog_euc_jp","CharsetRecog_euc$CharsetRecog_euc_kr","CharsetRecog_gb_18030"};
 static Object[] recognizers=new Object[5];static Method match,next,noTable;static Constructor<?> iter;
 static Field length;static Field[] state;
 static String row(String prefix,byte[] raw,int count)throws Exception {
  var detector=new CharsetDetector().setText(raw);length.setInt(detector,count);
  StringBuilder scores=new StringBuilder(),traces=new StringBuilder();
  for(Object recognizer:recognizers) {
   if(scores.length()>0){scores.append(',');traces.append(',');}
   var candidate=(CharsetMatch)match.invoke(recognizer,detector);scores.append(candidate==null?0:candidate.getConfidence());
   Object cursor=iter.newInstance();int hash=1;
   for(;;){boolean more=(boolean)next.invoke(recognizer,cursor,detector);hash=31*hash+(more?1:0);for(Field field:state){Object v=field.get(cursor);hash=31*hash+(v instanceof Boolean?((boolean)v?1:0):(int)v);}if(!more)break;}
   traces.append(hash);
  }
  scores.append(',').append(noTable.invoke(recognizers[0],detector,null));
  return prefix+"|"+scores+"|"+traces;
 }
 public static void main(String[]args)throws Exception {
  var base=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs");var cursor=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs$iteratedChar");
  iter=cursor.getDeclaredConstructor();iter.setAccessible(true);next=base.getDeclaredMethod("nextChar",cursor,CharsetDetector.class);next.setAccessible(true);
  noTable=base.getDeclaredMethod("match",CharsetDetector.class,int[].class);noTable.setAccessible(true);
  match=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",CharsetDetector.class);match.setAccessible(true);
  length=CharsetDetector.class.getDeclaredField("fRawLength");length.setAccessible(true);
  state=new Field[4];int i=0;for(String field:new String[]{"charValue","nextIndex","error","done"}){state[i]=cursor.getDeclaredField(field);state[i++].setAccessible(true);}
  for(i=0;i<5;i++){var ctor=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs$"+names[i]).getDeclaredConstructor();ctor.setAccessible(true);recognizers[i]=ctor.newInstance();}
  System.out.println("// Generated independently from ICU4J 78.3 by CharsetMbcsGoldens.java.\npackage io.github.heapy.icu4k.text\n\ninternal val charsetMbcsGoldenRows = listOf(");
  for(String hex:new String[]{"","81","8130","813081","81308130","817f","ff00","ffff","8f20ff","8ea0","8fff","a1ff","80ff","6162636465666768696a"}) {
   byte[] raw=java.util.HexFormat.of().parseHex(hex);
   for(int n=0;n<=raw.length;n++)for(boolean padded:new boolean[]{false,true})System.out.println("    \""+row("h|"+hex+"|"+n+"|"+padded,padded?java.util.Arrays.copyOf(raw,8000):raw,n)+"\",");
  }
  for(int n:new int[]{0,1,9,10,11,12,16,20,31,32,64,100,128,256,512,1024,4096}) {
   var counts=new java.util.LinkedHashSet<Integer>();for(int k:new int[]{0,1,2,3,4,7,11,n/4,n/2,n-1,n})if(k>=0&&k<=n)counts.add(k);
   for(int k:counts)for(int errors:new int[]{0,1,2}) {
    byte[] raw=new byte[2*(n+errors)];for(int p=0;p<n;p++){raw[p*2]=(byte)0x81;raw[p*2+1]=(byte)(p<k?0x40:0x90);}for(int p=n;p<n+errors;p++)raw[p*2]=(byte)0x81;
    System.out.println("    \""+row("g|"+n+"|"+k+"|"+errors,raw,raw.length)+"\",");
   }
  }
  System.out.println(")");
 }
}
