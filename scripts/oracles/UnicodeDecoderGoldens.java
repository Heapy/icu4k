import com.ibm.icu.charset.*;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
public class UnicodeDecoderGoldens {
 static final String[] state={"toUnicodeStatus","toUBytesBegin","toULength","charErrorBufferLength","charErrorBufferBegin","invalidCharLength","preToUBegin","preToULength","preToUFirstLength","mode"};
 static Class<?> base=CharsetDecoderICU.class;static Method decode,nioLoop,flushLoop;static int calls;
 static Field f(String n)throws Exception{var x=base.getDeclaredField(n);x.setAccessible(true);return x;}
 static void set(Object d,String n,int v)throws Exception{f(n).setInt(d,v);}
 static byte[] bytes(Object d,String n)throws Exception{return (byte[])f(n).get(d);}
 static char[] chars(Object d,String n)throws Exception{return (char[])f(n).get(d);}
 static int hash(int h,int v){return 31*h+v;}
 static CharsetDecoderICU make(String name,int mode,int seed)throws Exception{
  var type=Class.forName("com.ibm.icu.charset.Charset"+name.split(",")[0].replace("-",""));var ctor=type.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);var d=(CharsetDecoderICU)((Charset)ctor.newInstance(name,"x-icu4k-test",new String[0])).newDecoder();
  if(mode==1||mode==8||mode==9){d.onMalformedInput(CodingErrorAction.REPLACE);d.onUnmappableCharacter(CodingErrorAction.REPLACE);if(mode==8)d.replaceWith("*");if(mode==9)d.replaceWith("\u001a");}
  else if(mode==2){d.onMalformedInput(CodingErrorAction.IGNORE);d.onUnmappableCharacter(CodingErrorAction.IGNORE);}
  else if(mode>=3){Object context=mode==4?"D":mode==5?"X":mode==6?"C":mode==7?"J":mode==10?"i":mode>=11?"replay":null;CharsetCallback.Decoder cb=mode==10?CharsetCallback.TO_U_CALLBACK_SKIP:CharsetCallback.TO_U_CALLBACK_ESCAPE;
   if(mode>=11){int[] count={0};cb=(decoder,ctx,source,target,offsets,buffer,length,error)->{try{if(count[0]++==0){bytes(decoder,"preToUArray")[0]=82;set(decoder,"preToULength",-1);return mode==11?CoderResult.UNDERFLOW:CoderResult.OVERFLOW;}return error;}catch(Exception e){throw new RuntimeException(e);}};}
   d.setToUCallback(CoderResult.malformedForLength(1),cb,context);d.setToUCallback(CoderResult.unmappableForLength(1),cb,context);
  }
  if(seed==1||seed==2){byte[] p=bytes(d,"preToUArray");p[0]=(byte)(seed==1?65:0xe2);p[1]=(byte)(seed==1?66:0x82);set(d,"preToULength",-2);}
  else if(seed==3){chars(d,"charErrorBufferArray")[0]=0x51;chars(d,"charErrorBufferArray")[1]=0xdc00;set(d,"charErrorBufferLength",2);}
  else if(seed==4){bytes(d,"toUBytesArray")[0]=(byte)0xe2;set(d,"toUnicodeStatus",2);set(d,"mode",3);set(d,"toULength",1);}
  return d;
 }
 static String run(String name,String hex,int split,int capacity,int mode,int seed)throws Exception{return run(name,hex,split,capacity,mode,seed,false);}
 static String run(String name,String hex,int split,int capacity,int mode,int seed,boolean nio)throws Exception{return run(name,hex,split,capacity,mode,seed,nio,false);}
 static String run(String name,String hex,int split,int capacity,int mode,int seed,boolean nio,boolean local)throws Exception{
  byte[] raw=HexFormat.of().parseHex(hex);byte[] padded=new byte[raw.length+4];System.arraycopy(raw,0,padded,2,raw.length);ByteBuffer source=ByteBuffer.wrap(padded,2,raw.length).slice();var d=make(name,mode,seed);int h=1,count=0;boolean flushing=false;
  int end=Math.min(split,raw.length);source.limit(end);if(local)source=ByteBuffer.wrap(padded,2,end).slice();
  for(;;){int cap=count%5==0?0:capacity;char[] output=new char[cap+4];CharBuffer target=CharBuffer.wrap(output,2,cap).slice();IntBuffer offsets=IntBuffer.allocate(cap);boolean flush=end==raw.length;
   CoderResult result=null;String text;boolean failed=false;try{result=(CoderResult)(nio?(flushing?flushLoop.invoke(d,target):nioLoop.invoke(d,source,target)):decode.invoke(d,source,target,offsets,flush));text=result.toString();}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof BufferOverflowException)text="THROW:overflow";else if(cause instanceof IllegalStateException)text="THROW:state";else if(cause instanceof IllegalArgumentException)text="THROW:argument";else if(cause instanceof IndexOutOfBoundsException)text="THROW:bounds";else if(cause instanceof NullPointerException)text="THROW:null";else throw e;failed=true;}h=hash(h,text.hashCode());h=hash(h,source.position());h=hash(h,target.position());h=hash(h,offsets.position());for(int i=0;i<target.position();i++)h=hash(h,target.get(i));for(int i=0;i<offsets.position();i++)h=hash(h,offsets.get(i));
   for(String n:state)h=hash(h,f(n).getInt(d));h=hash(h,Arrays.hashCode(bytes(d,"toUBytesArray")));h=hash(h,Arrays.hashCode(chars(d,"charErrorBufferArray")));h=hash(h,Arrays.hashCode(chars(d,"invalidCharBuffer")));h=hash(h,Arrays.hashCode(bytes(d,"preToUArray")));Object ctx=f("toUContext").get(d);h=hash(h,ctx==null?0:ctx.hashCode());h=hash(h,d.replacement().hashCode());h=hash(h,d.malformedInputAction().toString().hashCode());h=hash(h,d.unmappableCharacterAction().toString().hashCode());
   for(String n:new String[]{"isBOMReadYet","actualEndianXOR","actualBOM"}){var pf=d.getClass().getDeclaredField(n);pf.setAccessible(true);Object v=pf.get(d);h=hash(h,v==null?0:v instanceof Boolean?((boolean)v?1:0):v instanceof byte[] a?Arrays.hashCode(a):((Number)v).intValue());}
   count++;if(count>1000)throw new AssertionError("No termination "+hex+"/"+mode+"/"+seed);
   if(failed)break;
   if(nio){if(result.isUnderflow()){if(flushing)break;if(end==raw.length)flushing=true;else {end=Math.min(end+split,raw.length);source.limit(end);}}continue;}
   if(result.isUnderflow()&&source.position()==(local?source.limit():end)){if(flush)break;int start=end;end=Math.min(end+split,raw.length);if(local)source=ByteBuffer.wrap(padded,2+start,end-start).slice();else source.limit(end);}
  }
  return name+"|"+hex+"|"+split+"|"+capacity+"|"+mode+"|"+seed+"|"+count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  decode=base.getDeclaredMethod("decode",ByteBuffer.class,CharBuffer.class,IntBuffer.class,boolean.class);decode.setAccessible(true);nioLoop=base.getDeclaredMethod("decodeLoop",ByteBuffer.class,CharBuffer.class);nioLoop.setAccessible(true);flushLoop=base.getDeclaredMethod("implFlush",CharBuffer.class);flushLoop.setAccessible(true);
  String[] names={"UTF-16","UTF-16BE","UTF-16LE","UTF-16,version=1","UTF-16BE,version=1","UTF-16LE,version=1","UTF-32","UTF-32BE","UTF-32LE"};
  String[] cases={"","00","feff","fffe","fe00ff00","00410042","41004200","d800dc00","00d800dc","d8000041","dc00","0000feff","fffe0000","0001f600","0010ffff","0000d800","00000041","42000000","ffffffff"};
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/UnicodeDecoderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val unicodeDecoderGoldenRows = listOf(");
  for(String name:names)for(String hex:cases)for(int split:new int[]{1,3,8})for(int cap:new int[]{1,2})for(int mode:new int[]{0,1,2,4})System.out.println("    \""+run(name,hex,split,cap,mode,0)+"\",");
  System.out.println(");\ninternal val unicodeDecoderNioGoldenRows = listOf(");
  for(String name:names)for(String hex:new String[]{"","feff0041","fffe4100","d800dc00","0001f600","0000feff00000041"})for(int split:new int[]{1,3})for(int cap:new int[]{1,2})for(int mode:new int[]{0,1,4})System.out.println("    \""+run(name,hex,split,cap,mode,0,true)+"\",");
  System.out.println(");\ninternal val unicodeDecoderLocalGoldenRows = listOf(");
  for(String name:names)for(String hex:new String[]{"","feff0041","fffe4100","d8000041","00d84100","0000feff00000041"})for(int split:new int[]{1,2,3})for(int cap:new int[]{1,2})for(int mode:new int[]{0,1,4})System.out.println("    \""+run(name,hex,split,cap,mode,0,false,true)+"\",");
  System.out.println(");");
 }
}
