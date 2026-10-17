import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
public class MBCSReconstitutionGoldens {
 static Class<?> outer,type;static Method write,reconstitute;
 static void set(Object o,String n,Object v)throws Exception{var f=type.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Object get(Object o,String n)throws Exception{var f=type.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object make()throws Exception{var c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 static String hashes(Object t)throws Exception{var v=new ArrayList<String>();for(String n:new String[]{"fromUnicodeTableInts","fromUnicodeChars","fromUnicodeBytes","fromUnicodeInts"}){Object a=get(t,n);int hash=a==null?0:a instanceof int[] x?Arrays.hashCode(x):a instanceof char[] x?Arrays.hashCode(x):Arrays.hashCode((byte[])a);v.add(Integer.toString(hash));}return String.join("|",v);}
 static Object writer(int output)throws Exception{
  Object t=make();set(t,"outputType",(short)output);char[] first=new char[64];Arrays.fill(first,(char)32);set(t,"fromUnicodeTable",first);int[] second=new int[96];for(int i=0;i<second.length;i++)second[i]=i%4+1;set(t,"fromUnicodeTableInts",second);
  switch(output){case 2:case 9:set(t,"fromUnicodeBytes",new byte[384]);break;case 3:set(t,"fromUnicodeInts",new int[128]);break;default:set(t,"fromUnicodeChars",new char[128]);}return t;
 }
 static int end(int state,int action,int value){return 0x80000000|state<<24|action<<20|value;}
 static Object reconstitution(int output,boolean supplementary)throws Exception{
  Object t=make();set(t,"outputType",(short)output);set(t,"countStates",(short)2);set(t,"maxFastUChar",(char)0x7ff);int len=supplementary?0x440:0x40;char[] first=new char[len];Arrays.fill(first,(char)(len/2));first[0]=(char)(len/2+1);set(t,"fromUnicodeTable",first);set(t,"fromUnicodeTableInts",new int[len/2+64]);set(t,"fromUBytesLength",12288);
  char[] index=new char[32];for(int i=0;i<16;i++)index[i]=(char)(i==2?0:(i+1)*64);set(t,"mbcsIndex",CharBuffer.wrap(index));
  int[][] states=new int[2][256];for(int[] row:states)Arrays.fill(row,end(0,6,0));states[0][0]=end(0,0,0);states[0][1]=end(0,0,1);states[0][32]=1<<24;states[0][255]=end(0,0,255);
  states[1][1]=end(0,0,1);states[1][2]=end(0,4,0);states[1][3]=end(0,4,1);states[1][4]=end(0,5,2);states[1][5]=end(0,5,4);states[1][6]=end(0,5,6);states[1][7]=end(0,1,0);states[1][8]=end(0,0,65535);
  if(!supplementary){states[1][4]=end(0,5,4);states[1][7]=end(0,0,3);}
  set(t,"stateTable",states);set(t,"unicodeCodeUnits",new char[]{'A',0xfffe,0xd800,0xdc00,0xe000,'B',0xdc00});return t;
 }
 static byte[] file(int output,boolean supplementary)throws Exception{
  Object t=reconstitution(output,supplementary);int len=supplementary?0x440:0x40;int to=40+2048,from=to+14,bytes=from+len*2+256;
  ByteBuffer b=ByteBuffer.allocate(132+bytes+64);b.putShort((short)32).put((byte)0xda).put((byte)0x27).putShort((short)20).putShort((short)0).put((byte)1).put((byte)0).put((byte)2).put((byte)0).putInt(0x636e7674).putInt(0x06010000).putInt(0).putInt(0).putInt(0);
  b.putInt(32,100);b.put(101,(byte)2);b.put(102,(byte)1);b.put(103,(byte)4);b.put(104,(byte)63);b.put(108,(byte)1);b.put(111,(byte)(supplementary?1:0));b.position(132);
  b.put(new byte[]{5,3,(byte)0x80,0}).putInt(2).putInt(0).putInt(to).putInt(from).putInt(bytes).putInt(output).putInt(12288).putInt(0x40).putInt(128);
  for(int[] row:(int[][])get(t,"stateTable"))for(int entry:row)b.putInt(entry);for(char c:(char[])get(t,"unicodeCodeUnits"))b.putChar(c);for(char c:(char[])get(t,"fromUnicodeTable"))b.putChar(c);for(int i=0;i<64;i++)b.putInt(0);CharBuffer index=(CharBuffer)get(t,"mbcsIndex");for(int i=0;i<index.remaining();i++)b.putChar(index.get(i));return b.array();
 }
 public static void main(String[]args)throws Exception{
  outer=Class.forName("com.ibm.icu.charset.CharsetMBCS");type=Class.forName("com.ibm.icu.charset.CharsetMBCS$UConverterMBCSTable");write=outer.getDeclaredMethod("writeStage3Roundtrip",type,long.class,int[].class);write.setAccessible(true);reconstitute=outer.getDeclaredMethod("reconstituteData",type,int.class,int.class,int.class);reconstitute.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/MBCSReconstitutionGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val mbcsReconstitutionGoldenRows = listOf(");
  for(int output:new int[]{1,2,3,8,9,12}){
   for(long value:new long[]{0,65535,0x8eff00,0x8fff00,0xffffff,0x8effff00L,0x8fff0000L,-1,Long.MAX_VALUE-20}){Object t=writer(output);int[] cp=new int[32];for(int i=0;i<32;i++)cp[i]=i%4==0?-1:i*3%64;write.invoke(null,t,value,cp);System.out.println("    \"w|"+output+"|"+value+"|"+hashes(t)+"\",");}
   for(boolean supplementary:new boolean[]{false,true}){Object t=reconstitution(output,supplementary);reconstitute.invoke(null,t,supplementary?0x440:0x40,64,128);System.out.println("    \"r|"+output+"|"+supplementary+"|"+hashes(t)+"\",");}
  }
  var ctor=outer.getDeclaredConstructor(String.class,String.class,String[].class,String.class,ClassLoader.class);ctor.setAccessible(true);
  for(int output:new int[]{1,2,3,8,9,12})for(boolean supplementary:new boolean[]{false,true}){byte[] bytes=file(output,supplementary);ClassLoader loader=new ClassLoader(){public java.io.InputStream getResourceAsStream(String name){return new java.io.ByteArrayInputStream(bytes);}};Object converter=ctor.newInstance("fixture","x-icu4k-test",new String[0],"custom",loader);var sf=outer.getDeclaredField("sharedData");sf.setAccessible(true);Object shared=sf.get(converter);var mf=shared.getClass().getDeclaredField("mbcs");mf.setAccessible(true);Object t=mf.get(shared);System.out.println("    \"l|"+output+"|"+supplementary+"|"+hashes(t)+"|"+get(t,"utf8Friendly")+"|"+(int)(char)get(t,"maxFastUChar")+"|"+get(t,"asciiRoundtrips")+"|"+(get(t,"mbcsIndex")==null)+"\",");}
  System.out.println(");");
 }
}
