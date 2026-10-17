import com.ibm.icu.charset.*;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
public class SingleByteDecoderGoldens {
 static final String[] state={"toUnicodeStatus","toUBytesBegin","toULength","charErrorBufferLength","charErrorBufferBegin","invalidCharLength","preToUBegin","preToULength","preToUFirstLength","mode"};
 static Class<?> base=CharsetDecoderICU.class;static Method decode,nioLoop,flushLoop;static int calls;
 static Field f(String n)throws Exception{var x=base.getDeclaredField(n);x.setAccessible(true);return x;}
 static void set(Object d,String n,int v)throws Exception{f(n).setInt(d,v);}
 static byte[] bytes(Object d,String n)throws Exception{return (byte[])f(n).get(d);}
 static char[] chars(Object d,String n)throws Exception{return (char[])f(n).get(d);}
 static int hash(int h,int v){return 31*h+v;}
 static CharsetDecoderICU make(boolean latin,int mode,int seed)throws Exception{
  var d=(CharsetDecoderICU)CharsetICU.forNameICU(latin?"ISO-8859-1":"US-ASCII").newDecoder();
  if(mode==1||mode==8||mode==9){d.onMalformedInput(CodingErrorAction.REPLACE);d.onUnmappableCharacter(CodingErrorAction.REPLACE);if(mode==8)d.replaceWith("*");if(mode==9)d.replaceWith("\u001a");}
  else if(mode==2){d.onMalformedInput(CodingErrorAction.IGNORE);d.onUnmappableCharacter(CodingErrorAction.IGNORE);}
  else if(mode>=3){Object context=mode==4?"D":mode==5?"X":mode==6?"C":mode==7?"J":mode==10?"i":mode>=11?"replay":null;CharsetCallback.Decoder cb=mode==10?CharsetCallback.TO_U_CALLBACK_SKIP:CharsetCallback.TO_U_CALLBACK_ESCAPE;
   if(mode>=11){int[] count={0};cb=(decoder,ctx,source,target,offsets,buffer,length,error)->{try{if(count[0]++==0){bytes(decoder,"preToUArray")[0]=82;set(decoder,"preToULength",-1);return mode==11?CoderResult.UNDERFLOW:CoderResult.OVERFLOW;}return error;}catch(Exception e){throw new RuntimeException(e);}};}
   d.setToUCallback(CoderResult.malformedForLength(1),cb,context);d.setToUCallback(CoderResult.unmappableForLength(1),cb,context);
  }
  return d;
 }
 static String run(boolean latin,String hex,int split,int capacity,int mode,int seed)throws Exception{return run(latin,hex,split,capacity,mode,seed,false);}
 static String run(boolean latin,String hex,int split,int capacity,int mode,int seed,boolean nio)throws Exception{
  byte[] raw=HexFormat.of().parseHex(hex);byte[] padded=new byte[raw.length+4];System.arraycopy(raw,0,padded,2,raw.length);ByteBuffer source;if(seed==0||seed==3||seed==4)source=ByteBuffer.wrap(raw);else source=ByteBuffer.wrap(padded,2,raw.length).slice();if(seed==2||seed==3)source=source.asReadOnlyBuffer();if(seed==7){source=ByteBuffer.allocateDirect(raw.length);source.put(raw).flip();}var d=make(latin,mode,seed);int h=1,count=0;boolean flushing=false;
  int end=Math.min(split,raw.length);source.limit(end);
  for(;;){int cap=count%5==0?0:capacity;char[] output=new char[cap+4];CharBuffer target;if(seed==0||seed==3)target=CharBuffer.allocate(cap);else if(seed==4||seed==5)target=ByteBuffer.allocateDirect(cap*2).asCharBuffer();else target=CharBuffer.wrap(output,2,cap).slice();if(seed==6)target=target.asReadOnlyBuffer();IntBuffer offsets=IntBuffer.allocate(cap);boolean flush=end==raw.length;
   CoderResult result=null;String text;boolean failed=false;try{result=(CoderResult)(nio?(flushing?flushLoop.invoke(d,target):nioLoop.invoke(d,source,target)):decode.invoke(d,source,target,offsets,flush));text=result.toString();}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof BufferOverflowException)text="THROW:overflow";else if(cause instanceof IllegalStateException)text="THROW:state";else if(cause instanceof IllegalArgumentException)text="THROW:argument";else if(cause instanceof IndexOutOfBoundsException)text="THROW:bounds";else if(cause instanceof ReadOnlyBufferException)text="THROW:readonly";else throw e;failed=true;}h=hash(h,text.hashCode());h=hash(h,source.position());h=hash(h,target.position());h=hash(h,offsets.position());for(int i=0;i<target.position();i++)h=hash(h,target.get(i));for(int i=0;i<offsets.position();i++)h=hash(h,offsets.get(i));
   for(String n:state)h=hash(h,f(n).getInt(d));h=hash(h,Arrays.hashCode(bytes(d,"toUBytesArray")));h=hash(h,Arrays.hashCode(chars(d,"charErrorBufferArray")));h=hash(h,Arrays.hashCode(chars(d,"invalidCharBuffer")));h=hash(h,Arrays.hashCode(bytes(d,"preToUArray")));Object ctx=f("toUContext").get(d);h=hash(h,ctx==null?0:ctx.hashCode());h=hash(h,d.replacement().hashCode());h=hash(h,d.malformedInputAction().toString().hashCode());h=hash(h,d.unmappableCharacterAction().toString().hashCode());
   count++;if(count>1000)throw new AssertionError("No termination "+hex+"/"+mode+"/"+seed);
   if(failed)break;
   if(nio){if(result.isUnderflow()){if(flushing)break;if(end==raw.length)flushing=true;else {end=Math.min(end+split,raw.length);source.limit(end);}}continue;}
   if(result.isUnderflow()&&source.position()==end){if(flush)break;end=Math.min(end+split,raw.length);source.limit(end);}
  }
  return latin+"|"+hex+"|"+split+"|"+capacity+"|"+mode+"|"+seed+"|"+count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  decode=base.getDeclaredMethod("decode",ByteBuffer.class,CharBuffer.class,IntBuffer.class,boolean.class);decode.setAccessible(true);nioLoop=base.getDeclaredMethod("decodeLoop",ByteBuffer.class,CharBuffer.class);nioLoop.setAccessible(true);flushLoop=base.getDeclaredMethod("implFlush",CharBuffer.class);flushLoop.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/SingleByteDecoderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val singleByteDecoderGoldenRows = listOf(");
  for(boolean latin:new boolean[]{false,true})for(String hex:new String[]{"","00","4142","7f80","8041","418042","80ff00"})for(int split:new int[]{1,2,8})for(int cap:new int[]{1,3})for(int mode:new int[]{0,1,2,4})for(int backend=0;backend<8;backend++)System.out.println("    \""+run(latin,hex,split,cap,mode,backend)+"\",");
  for(boolean latin:new boolean[]{false,true})for(int b=0;b<256;b++)for(int backend:new int[]{0,2})for(int mode:new int[]{0,1})System.out.println("    \""+run(latin,String.format("%02x",b),1,2,mode,backend)+"\",");
  System.out.println(");\ninternal val singleByteDecoderNioGoldenRows = listOf(");
  for(boolean latin:new boolean[]{false,true})for(String hex:new String[]{"","4142","7f80","8041","418042","80ff00"})for(int split:new int[]{1,3})for(int cap:new int[]{1,2})for(int mode:new int[]{0,1,4})for(int backend:new int[]{0,1,2,5})System.out.println("    \""+run(latin,hex,split,cap,mode,backend,true)+"\",");
  System.out.println(");");
 }
}
