import com.ibm.icu.charset.CharsetICU;
import java.nio.*;
import java.lang.reflect.*;
public class MBCSExtensionGoldens {
 static final int[] WORDS={6<<24,0x101f0041,0x20000007,0x3000000a,0x809fffff,0xf0b80000,0xff9f0000,
  (2<<24)|0x9f0061,0x019f0062,0xff00000d,(2<<24)|0x1f0063,0x10b80000,0x209f0064,
  (1<<24)|0x9f0065,0x229f0066};
 static final int[] ALPHABET={0,1,0x10,0x20,0x22,0x30,0x7f,0x80,0xf0,0xff};
 static ByteBuffer extension(){var b=ByteBuffer.allocate(256);b.putInt(0,32);b.putInt(4,128);b.putInt(8,WORDS.length);b.putInt(12,224);b.putInt(16,2);for(int i=0;i<WORDS.length;i++)b.putInt(128+4*i,WORDS[i]);b.putChar(224,'A');b.putChar(226,'\u0301');return b;}
 static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception{
  var cs=CharsetICU.forNameICU("ibm-943");var d=cs.newDecoder();var shared=field(cs.getClass(),"sharedData").get(cs);var table=field(shared.getClass(),"mbcs").get(shared);field(table.getClass(),"extIndexes").set(table,extension());
  var m=d.getClass().getDeclaredMethod("matchToU",byte.class,byte[].class,int.class,int.class,ByteBuffer.class,int[].class,boolean.class,boolean.class);m.setAccessible(true);
  System.out.println("// Generated from ICU4J 78.3 by scripts/oracles/MBCSExtensionGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val mbcsExtensionGoldenRows = listOf(");
  for(int siso=-1;siso<=1;siso++)for(boolean fallback:new boolean[]{false,true})for(boolean flush:new boolean[]{false,true})for(int split=0;split<=3;split++){
   int h=1,calls=0;
   for(int len=0;len<=3;len++){int count=(int)Math.pow(ALPHABET.length,len);for(int n=0;n<count;n++){
    byte[] input=new byte[len];int v=n;for(int i=0;i<len;i++){input[i]=(byte)ALPHABET[v%ALPHABET.length];v/=ALPHABET.length;}
    int prelen=Math.min(split,len);byte[] pre=new byte[prelen+4];System.arraycopy(input,0,pre,2,prelen);var src=ByteBuffer.allocate(len-prelen+4);for(int i=prelen;i<len;i++)src.put(2+i-prelen,input[i]);src.position(2);src.limit(2+len-prelen);int[] out={0x12345678};
    int result=(Integer)m.invoke(d,(byte)siso,pre,2,prelen,src,out,fallback,flush);h=31*h+result;h=31*h+out[0];h=31*h+src.position();calls++;
   }}
   System.out.println("    \""+siso+"|"+fallback+"|"+flush+"|"+split+"|"+calls+"|"+h+"\",");
  }
  System.out.println(");");
 }
}
