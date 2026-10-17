import com.ibm.icu.charset.*;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
public class CharsetDecoderGoldens {
 static final String[] state={"toUnicodeStatus","toUBytesBegin","toULength","charErrorBufferLength","charErrorBufferBegin","invalidCharLength","preToUBegin","preToULength","preToUFirstLength","mode"};
 static Class<?> base=CharsetDecoderICU.class;static Method decode,nioLoop,flushLoop;static int calls;
 static Field f(String n)throws Exception{var x=base.getDeclaredField(n);x.setAccessible(true);return x;}
 static void set(Object d,String n,int v)throws Exception{f(n).setInt(d,v);}
 static byte[] bytes(Object d,String n)throws Exception{return (byte[])f(n).get(d);}
 static char[] chars(Object d,String n)throws Exception{return (char[])f(n).get(d);}
 static int hash(int h,int v){return 31*h+v;}
 static CharsetDecoderICU make(boolean cesu,int mode,int seed)throws Exception{
  var d=(CharsetDecoderICU)CharsetICU.forNameICU(cesu?"CESU-8":"UTF-8").newDecoder();
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
 static String run(boolean cesu,String hex,int split,int capacity,int mode,int seed)throws Exception{return run(cesu,hex,split,capacity,mode,seed,false);}
 static String run(boolean cesu,String hex,int split,int capacity,int mode,int seed,boolean nio)throws Exception{
  byte[] raw=HexFormat.of().parseHex(hex);byte[] padded=new byte[raw.length+4];System.arraycopy(raw,0,padded,2,raw.length);ByteBuffer source=ByteBuffer.wrap(padded,2,raw.length).slice();var d=make(cesu,mode,seed);int h=1,count=0;boolean flushing=false;
  int end=Math.min(split,raw.length);source.limit(end);
  for(;;){int cap=count%5==0?0:capacity;char[] output=new char[cap+4];CharBuffer target=CharBuffer.wrap(output,2,cap).slice();IntBuffer offsets=IntBuffer.allocate(cap);boolean flush=end==raw.length;
   CoderResult result=null;String text;boolean failed=false;try{result=(CoderResult)(nio?(flushing?flushLoop.invoke(d,target):nioLoop.invoke(d,source,target)):decode.invoke(d,source,target,offsets,flush));text=result.toString();}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof BufferOverflowException)text="THROW:overflow";else if(cause instanceof IllegalStateException)text="THROW:state";else if(cause instanceof IllegalArgumentException)text="THROW:argument";else if(cause instanceof IndexOutOfBoundsException)text="THROW:bounds";else throw e;failed=true;}h=hash(h,text.hashCode());h=hash(h,source.position());h=hash(h,target.position());h=hash(h,offsets.position());for(int i=0;i<target.position();i++)h=hash(h,target.get(i));for(int i=0;i<offsets.position();i++)h=hash(h,offsets.get(i));
   for(String n:state)h=hash(h,f(n).getInt(d));h=hash(h,Arrays.hashCode(bytes(d,"toUBytesArray")));h=hash(h,Arrays.hashCode(chars(d,"charErrorBufferArray")));h=hash(h,Arrays.hashCode(chars(d,"invalidCharBuffer")));h=hash(h,Arrays.hashCode(bytes(d,"preToUArray")));Object ctx=f("toUContext").get(d);h=hash(h,ctx==null?0:ctx.hashCode());h=hash(h,d.replacement().hashCode());h=hash(h,d.malformedInputAction().toString().hashCode());h=hash(h,d.unmappableCharacterAction().toString().hashCode());
   count++;if(count>1000)throw new AssertionError("No termination "+hex+"/"+mode+"/"+seed);
   if(failed)break;
   if(nio){if(result.isUnderflow()){if(flushing)break;if(end==raw.length)flushing=true;else {end=Math.min(end+split,raw.length);source.limit(end);}}continue;}
   if(result.isUnderflow()&&source.position()==end){if(flush)break;end=Math.min(end+split,raw.length);source.limit(end);}
  }
  return cesu+"|"+hex+"|"+split+"|"+capacity+"|"+mode+"|"+seed+"|"+count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  decode=base.getDeclaredMethod("decode",ByteBuffer.class,CharBuffer.class,IntBuffer.class,boolean.class);decode.setAccessible(true);nioLoop=base.getDeclaredMethod("decodeLoop",ByteBuffer.class,CharBuffer.class);nioLoop.setAccessible(true);flushLoop=base.getDeclaredMethod("implFlush",CharBuffer.class);flushLoop.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/CharsetDecoderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val charsetDecoderGoldenRows = listOf(");
  for(boolean cesu:new boolean[]{false,true})for(String hex:new String[]{"","00","616263","7f80","c080","c2a1","e282ac","e282","e228a1","eda080","edbfbf","eda080edb080","f09f9880","f48fbfbf","f4908080","f0808080","f5808080","efbbbfff41"})for(int split:new int[]{1,2,5})for(int cap:new int[]{1,2,5})for(int mode=0;mode<=10;mode++)System.out.println("    \""+run(cesu,hex,split,cap,mode,0)+"\",");
  for(boolean cesu:new boolean[]{false,true})for(String hex:new String[]{"","6162","82ac","ac80","f09f9880","8080"})for(int seed=1;seed<=4;seed++)for(int cap:new int[]{1,3})for(int mode:new int[]{0,1,3,10,11,12})System.out.println("    \""+run(cesu,hex,1,cap,mode,seed)+"\",");
  for(boolean cesu:new boolean[]{false,true})for(String hex:new String[]{"80","8080","e282","eda080"})for(int mode:new int[]{11,12})for(int cap:new int[]{1,2})System.out.println("    \""+run(cesu,hex,1,cap,mode,0)+"\",");
  System.out.println(");");
  System.out.println("internal val charsetDecoderNioGoldenRows = listOf(");
  for(boolean cesu:new boolean[]{false,true})for(String hex:new String[]{"","616263","e282ac","e282","80ff","eda080edb080","f09f9880","f4908080"})for(int split:new int[]{1,3})for(int cap:new int[]{1,2})for(int mode:new int[]{0,1,2,4})for(int seed:new int[]{0,3})System.out.println("    \""+run(cesu,hex,split,cap,mode,seed,true)+"\",");
  System.out.println(");");
 }
}
