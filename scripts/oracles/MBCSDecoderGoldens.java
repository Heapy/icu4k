import com.ibm.icu.charset.*;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
public class MBCSDecoderGoldens {
 static final String[] state={"toUnicodeStatus","toUBytesBegin","toULength","charErrorBufferLength","charErrorBufferBegin","invalidCharLength","preToUBegin","preToULength","preToUFirstLength","mode"};
 static Class<?> base=CharsetDecoderICU.class;static Method decode,nioLoop,flushLoop;static int calls; static Map<String,Charset> cache=new HashMap<>();
 static Field f(String n)throws Exception{var x=base.getDeclaredField(n);x.setAccessible(true);return x;}
 static void set(Object d,String n,int v)throws Exception{f(n).setInt(d,v);}
 static byte[] bytes(Object d,String n)throws Exception{return (byte[])f(n).get(d);}
 static char[] chars(Object d,String n)throws Exception{return (char[])f(n).get(d);}
 static int hash(int h,int v){return 31*h+v;}
 static CharsetDecoderICU make(String name,int mode,int seed)throws Exception{
  Charset cs=cache.get(name);if(cs==null){var type=Class.forName("com.ibm.icu.charset.CharsetMBCS");var ctor=type.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);cs=(Charset)ctor.newInstance(name,"x-icu4k-test",new String[0]);cache.put(name,cs);}var d=(CharsetDecoderICU)cs.newDecoder();
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
   count++;if(count>1000)throw new AssertionError("No termination "+hex+"/"+mode+"/"+seed);
   if(failed)break;
   if(nio){if(result.isUnderflow()){if(flushing)break;if(end==raw.length)flushing=true;else {end=Math.min(end+split,raw.length);source.limit(end);}}continue;}
   if(result.isUnderflow()&&source.position()==(local?source.limit():end)){if(flush)break;int start=end;end=Math.min(end+split,raw.length);if(local)source=ByteBuffer.wrap(padded,2+start,end-start).slice();else source.limit(end);}
  }
  return name+"|"+hex+"|"+split+"|"+capacity+"|"+mode+"|"+seed+"|"+count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  decode=base.getDeclaredMethod("decode",ByteBuffer.class,CharBuffer.class,IntBuffer.class,boolean.class);decode.setAccessible(true);nioLoop=base.getDeclaredMethod("decodeLoop",ByteBuffer.class,CharBuffer.class);nioLoop.setAccessible(true);flushLoop=base.getDeclaredMethod("implFlush",CharBuffer.class);flushLoop.setAccessible(true);
  if(args.length==6){System.out.println(run(args[0],args[1],Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4]),Integer.parseInt(args[5])));return;}
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/MBCSDecoderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val mbcsDecoderGoldenRows = listOf(");
  try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(args[0]))){for(var p:paths.filter(p->p.toString().endsWith(".cnv")).sorted().toList())for(String suffix:new String[]{"",",swaplfnl"}){
   String name=p.getFileName().toString().replace(".cnv","")+suffix;
   for(int group=0;group<8;group++)System.out.println("    \""+name+"|"+group+"|"+group(name,group)+"\",");
  }}
  System.out.println(");");
 }
 static final String[] CASES={"","00","41427f80ff","0e41420f431b","81308130","90308130","ff00ff20","a1a2fefe","81ff4142"};
 static String group(String name,int group)throws Exception{
  int hash=1,count=0;
  if(group==7){byte[] pairs=new byte[131072];int i=0;for(int a=0;a<256;a++)for(int b=0;b<256;b++){pairs[i++]=(byte)a;pairs[i++]=(byte)b;}String r=run(name,HexFormat.of().formatHex(pairs),pairs.length,262144,1,0);return "1|"+(31+r.hashCode());}
  if(group==4||group==5){for(String hex:extra(name,group))for(int mode:new int[]{0,1,4}){String r=run(name,hex,mode==4?3:1,mode==4?3:1,mode,0,false,mode==1);hash=31*hash+r.hashCode();count++;}return count+"|"+hash;}
  if(group==6){var d=make(name,0,0);var m=d.getClass().getDeclaredMethod("simpleGetNextUChar",ByteBuffer.class,boolean.class);m.setAccessible(true);var inputs=new TreeSet<String>();inputs.addAll(extra(name,4));inputs.addAll(extra(name,5));inputs.addAll(Arrays.asList(CASES));for(int b=0;b<256;b++)inputs.add(String.format("%02x",b));for(String hex:inputs)for(int backend=0;backend<5;backend++)for(boolean fallback:new boolean[]{false,true}){
   byte[] raw=HexFormat.of().parseHex(hex);byte[] padded=new byte[raw.length+4];System.arraycopy(raw,0,padded,2,raw.length);ByteBuffer source=backend==0?ByteBuffer.wrap(raw):backend==1||backend==3?ByteBuffer.wrap(padded,2,raw.length).slice():ByteBuffer.wrap(padded).position(2).limit(2+raw.length);if(backend>=3)source=source.asReadOnlyBuffer();String result;try{result=m.invoke(d,source,fallback).toString();}catch(InvocationTargetException e){if(e.getCause() instanceof IndexOutOfBoundsException)result="THROW:bounds";else throw e;}String r=hex+"|"+backend+"|"+fallback+"|"+result+"|"+source.position()+"|"+source.limit();hash=31*hash+r.hashCode();count++;
  }return count+"|"+hash;}
  for(String hex:CASES)for(int split:new int[]{1,3})for(int cap:new int[]{1,3})for(int mode:new int[]{0,1,2,4}){
   if(group>0 && mode==2)continue;
   String result=run(name,hex,split,cap,mode,group==3?3:0,group==1,group==2);
   hash=31*hash+result.hashCode();count++;
  }
  if(group==0){byte[] all=new byte[256];for(int i=0;i<256;i++)all[i]=(byte)i;hash=31*hash+run(name,HexFormat.of().formatHex(all),256,512,0,0).hashCode();count++;}
  return count+"|"+hash;
 }
 static Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static List<String> extra(String name,int group)throws Exception{
  var inputs=new TreeSet<String>();
  if(group==4){make(name,0,0);var shared=get(cache.get(name),"sharedData");var table=get(shared,"mbcs");var ext=(ByteBuffer)get(table,"extIndexes");if(ext!=null&&ext.getInt(8)>0){var words=ext.duplicate().order(ext.order()).position(ext.getInt(4)).asIntBuffer();walk(words,0,"",inputs);}}
  else if(name.contains("gb18030")){for(long packed:GB)for(long delta:new long[]{-1,0,1})inputs.add(String.format("%08x",packed+delta));}
  return new ArrayList<>(inputs);
 }
 static void walk(IntBuffer words,int index,String prefix,Set<String> inputs){if(!prefix.isEmpty()){inputs.add(prefix);inputs.add(prefix+"55");}int length=words.get(index)>>>24;for(int i=1;i<=length;i++){int word=words.get(index+i),value=word&0xffffff;if(value==0)continue;String next=prefix+String.format("%02x",word>>>24);if(value<0x1f0000)walk(words,value,next,inputs);else {inputs.add(next);inputs.add(next+"55");}}}
 static final long[] GB={0x90308130L,0xe3329a35L,0x82358f33L,0x8336c738L,0x8130d330L,0x8135f436L,0x8135f438L,0x8136a531L,0x8336d030L,0x84308534L,0x8137a839L,0x8138fd38L,0x84309c38L,0x84318537L,0x8231d438L,0x8232af32L,0x8230a633L,0x8230f237L,0x8234a131L,0x8234e733L,0x8232c937L,0x8232f837L,0x8233e838L,0x82349638L,0x8233a339L,0x8233c931L,0x8431a234L,0x8431a439L};

}
