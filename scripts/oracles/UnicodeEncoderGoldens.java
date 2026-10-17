import com.ibm.icu.charset.*;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
public class UnicodeEncoderGoldens {
 static final Class<?> BASE=CharsetEncoderICU.class;
 static final String[] INTS={"errorBufferLength","fromUnicodeStatus","fromUChar32","preFromUFirstCP","preFromUBegin","preFromULength","invalidUCharLength"};
 static Method encode,nio,flush;
 static Field f(String n)throws Exception{var f=BASE.getDeclaredField(n);f.setAccessible(true);return f;}
 static void set(Object o,String n,int v)throws Exception{f(n).setInt(o,v);}
 static char[] chars(Object o,String n)throws Exception{return (char[])f(n).get(o);}
 static byte[] bytes(Object o,String n)throws Exception{return (byte[])f(n).get(o);}
 static final Object NON_STRING=new Object(){public int hashCode(){return 17;}public boolean equals(Object o){return o instanceof String&&o.equals("D");}};
 static CharsetEncoderICU make(String name,int mode,int seed)throws Exception{
  var clazz=Class.forName("com.ibm.icu.charset.Charset"+name.split(",")[0].replace("-",""));var ctor=clazz.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);var e=(CharsetEncoderICU)((Charset)ctor.newInstance(name,"x-icu4k-test",new String[0])).newEncoder();
  if(mode==1||mode==8||mode==9){e.onMalformedInput(CodingErrorAction.REPLACE);e.onUnmappableCharacter(CodingErrorAction.REPLACE);if(mode==8)e.replaceWith(new byte[]{42});if(mode==9)e.replaceWith(new byte[]{26});}
  else if(mode==2){e.onMalformedInput(CodingErrorAction.IGNORE);e.onUnmappableCharacter(CodingErrorAction.IGNORE);}
  else if(mode>=3){Object context=mode==4?"D":mode==5?"X":mode==6?"C":mode==7?"J":mode==10||mode==16?"i":mode==11||mode==12?"replay":mode==13?"U":mode==14?"S":mode==15||mode==17||mode==18?NON_STRING:mode==19?"other":null;
   CharsetCallback.Encoder cb=mode==10||mode==18?CharsetCallback.FROM_U_CALLBACK_SKIP:mode>=16?CharsetCallback.FROM_U_CALLBACK_SUBSTITUTE:CharsetCallback.FROM_U_CALLBACK_ESCAPE;
   if(mode==11||mode==12){int[] count={0};cb=(encoder,ctx,source,target,offsets,buffer,length,cp,error)->{try{if(count[0]++==0){chars(encoder,"preFromUArray")[0]='R';set(encoder,"preFromULength",-1);return mode==11?CoderResult.UNDERFLOW:CoderResult.OVERFLOW;}return error;}catch(Exception ex){throw new RuntimeException(ex);}};}
   e.setFromUCallback(CoderResult.malformedForLength(1),cb,context);e.setFromUCallback(CoderResult.unmappableForLength(1),cb,context);
  }
  if(seed==1||seed==2){chars(e,"preFromUArray")[0]=(char)(seed==1?'A':0xd800);chars(e,"preFromUArray")[1]=(char)(seed==1?'B':0xdc00);set(e,"preFromULength",-2);}
  else if(seed==3){bytes(e,"errorBuffer")[0]=1;bytes(e,"errorBuffer")[1]=2;bytes(e,"errorBuffer")[2]=(byte)0xff;set(e,"errorBufferLength",3);}
  else if(seed==4)set(e,"fromUChar32",0xd800);
  else if(seed==5){set(e,"preFromUFirstCP",0x10000);set(e,"preFromULength",1);chars(e,"preFromUArray")[0]='X';}
  return e;
 }
 static String run(String name,String hex,int split,int capacity,int mode,int seed,int backend,boolean nioMode,boolean local)throws Exception{
  char[] raw=new char[hex.length()/4];for(int i=0;i<raw.length;i++)raw[i]=(char)Integer.parseInt(hex.substring(i*4,i*4+4),16);
  char[] padded=new char[raw.length+4];System.arraycopy(raw,0,padded,2,raw.length);
  CharBuffer source=backend==1||backend==2||backend==7?CharBuffer.wrap(padded,2,raw.length).slice():CharBuffer.wrap(raw);
  if(backend==2||backend==3||backend==5)source=source.asReadOnlyBuffer();if(backend==7){source=ByteBuffer.allocateDirect(raw.length*2).asCharBuffer();source.put(raw).flip();}
  var e=make(name,mode,seed);int h=1,count=0;boolean flushing=false;int end=Math.min(split,raw.length);source.limit(end);
  if(local)source=CharBuffer.wrap(padded,2,end).slice();
  for(;;){int cap=count%5==0?0:capacity;byte[] output=new byte[cap+4];ByteBuffer target=backend==1||backend==2||backend==7?ByteBuffer.wrap(output,2,cap).slice():backend==5||backend==6?ByteBuffer.allocateDirect(cap):ByteBuffer.allocate(cap);if(backend==4)target=target.asReadOnlyBuffer();var offsets=IntBuffer.allocate(cap);
   CoderResult result=null;String text;boolean failed=false;
   try{result=(CoderResult)(nioMode?(flushing?flush.invoke(e,target):nio.invoke(e,source,target)):encode.invoke(e,source,target,offsets,end==raw.length));text=result.toString();}
   catch(InvocationTargetException ex){Throwable t=ex.getCause();if(t instanceof BufferOverflowException)text="THROW:overflow";else if(t instanceof IllegalArgumentException)text="THROW:argument";else if(t instanceof IndexOutOfBoundsException)text="THROW:bounds";else if(t instanceof ClassCastException)text="THROW:cast";else if(t instanceof ReadOnlyBufferException)text="THROW:readonly";else if(t instanceof IllegalStateException)text="THROW:state";else throw ex;failed=true;}
   h=31*h+text.hashCode();h=31*h+source.position();h=31*h+target.position();h=31*h+offsets.position();for(int i=0;i<target.position();i++)h=31*h+target.get(i);for(int i=0;i<offsets.position();i++)h=31*h+offsets.get(i);
   for(String n:INTS)h=31*h+f(n).getInt(e);for(String n:new String[]{"useSubChar1","useFallback"})h=31*h+(f(n).getBoolean(e)?1:0);
   h=31*h+Arrays.hashCode(bytes(e,"errorBuffer"));h=31*h+Arrays.hashCode(chars(e,"preFromUArray"));h=31*h+Arrays.hashCode(chars(e,"invalidUCharBuffer"));Object ctx=f("fromUContext").get(e);h=31*h+(ctx==null?0:ctx.hashCode());h=31*h+Arrays.hashCode(e.replacement());h=31*h+e.malformedInputAction().toString().hashCode();h=31*h+e.unmappableCharacterAction().toString().hashCode();
   if(name.equals("UTF-8")||name.equals("CESU-8")){for(String n:new String[]{"sourceIndex","targetIndex"}){var pf=e.getClass().getDeclaredField(n);pf.setAccessible(true);h=31*h+pf.getInt(e);}}
   else {var pf=e.getClass().getDeclaredField("temp");pf.setAccessible(true);h=31*h+Arrays.hashCode((byte[])pf.get(e));}
   if(++count>1000)throw new AssertionError("No termination "+name+"/"+hex+"/"+mode+"/"+seed+"/"+backend);if(failed)break;
   if(nioMode){if(result.isUnderflow()){if(flushing)break;if(end==raw.length)flushing=true;else{end=Math.min(end+split,raw.length);source.limit(end);}}continue;}
   if(result.isUnderflow()&&source.position()==source.limit()){if(end==raw.length)break;int start=end;end=Math.min(end+split,raw.length);if(local)source=CharBuffer.wrap(padded,2+start,end-start).slice();else source.limit(end);}
  }
  return name+"|"+hex+"|"+split+"|"+capacity+"|"+mode+"|"+seed+"|"+backend+"|"+nioMode+"|"+local+"|"+count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  encode=BASE.getDeclaredMethod("encode",CharBuffer.class,ByteBuffer.class,IntBuffer.class,boolean.class);encode.setAccessible(true);nio=BASE.getDeclaredMethod("encodeLoop",CharBuffer.class,ByteBuffer.class);nio.setAccessible(true);flush=BASE.getDeclaredMethod("implFlush",ByteBuffer.class);flush.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/UnicodeEncoderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val unicodeEncoderGoldenRows = listOf(");
  for(String name:NAMES)for(int group=0;group<5;group++)System.out.println("    \""+name+"|"+group+"|"+group(name,group)+"\",");
  System.out.println(");\ninternal val unicodeEncoderBulkGoldenRows = listOf(");
  var all=new StringBuilder(262144);for(int c=0;c<65536;c++)all.append(String.format("%04x",c));
  for(String name:NAMES)for(int mode:new int[]{1,2})System.out.println("    \""+name+"|"+mode+"|"+run(name,all.toString(),65536,262144,mode,0,0,false,false).hashCode()+"\",");
  System.out.println(");\ninternal val unicodeEncoderScalarGoldenRows = listOf(");
  for(String name:NAMES)for(boolean generic:new boolean[]{false,true})System.out.println("    \""+name+"|"+generic+"|"+scalars(name,generic)+"\",");
  System.out.println(");");
 }
 static String scalars(String name,boolean generic)throws Exception{
  var e=make(name,0,0);var loop=e.getClass().getDeclaredMethod("encodeLoop",CharBuffer.class,ByteBuffer.class,IntBuffer.class,boolean.class);loop.setAccessible(true);
  char[] units=new char[2];CharBuffer source=CharBuffer.wrap(units);if(generic)source=source.asReadOnlyBuffer();var target=ByteBuffer.allocate(8);int h=1,count=0;
  for(int cp=0;cp<=0x10ffff;cp++){if(cp>=0xd800&&cp<=0xdfff)continue;int length=cp<0x10000?1:2;if(length==1)units[0]=(char)cp;else{units[0]=(char)((cp>>>10)+0xd7c0);units[1]=(char)((cp&1023)+0xdc00);}source.position(0).limit(length);target.clear();var result=(CoderResult)loop.invoke(e,source,target,null,false);h=31*h+result.toString().hashCode();h=31*h+source.position();h=31*h+target.position();for(int i=0;i<target.position();i++)h=31*h+target.get(i);h=31*h+f("fromUChar32").getInt(e);count++;}
  for(String n:INTS)h=31*h+f(n).getInt(e);h=31*h+Arrays.hashCode(bytes(e,"errorBuffer"));
  if(name.equals("UTF-8")||name.equals("CESU-8")){for(String n:new String[]{"sourceIndex","targetIndex"}){var pf=e.getClass().getDeclaredField(n);pf.setAccessible(true);h=31*h+pf.getInt(e);}}
  else{var pf=e.getClass().getDeclaredField("temp");pf.setAccessible(true);h=31*h+Arrays.hashCode((byte[])pf.get(e));}
  return count+"|"+h;
 }
 static final String[] NAMES={"UTF-8","CESU-8","UTF-16","UTF-16BE","UTF-16LE","UTF-16,version=1","UTF-16BE,version=1","UTF-16LE,version=1","UTF-16BE,version=2","UTF-32","UTF-32BE","UTF-32LE"};
 static final String[] CASES={"","0000","007f008007ff0800","00410042","d7ffd800","dbffdfff","d800dc00","d83dde00","d8000041","dc000041","d800d800dc00","db40dc00","fefffffe","ffff"};
 static String group(String name,int group)throws Exception{
  int hash=1,count=0;
  if(group==0){for(String hex:CASES)for(int split:new int[]{1,3})for(int cap:new int[]{1,3,4})for(int mode:new int[]{0,1,2,4,6})for(int backend:new int[]{0,1,2,6}){hash=31*hash+run(name,hex,split,cap,mode,0,backend,false,false).hashCode();count++;}}
  else if(group==1||group==2){for(String hex:new String[]{"","0041d800dc00","d8000041","dc00"})for(int split:new int[]{1,3})for(int cap:new int[]{1,4})for(int mode:new int[]{0,1,4}){hash=31*hash+run(name,hex,split,cap,mode,0,0,group==1,group==2).hashCode();count++;}}
  else if(group==3){for(String hex:new String[]{"","00410042","d800dc00"})for(int seed=1;seed<=5;seed++)for(int cap:new int[]{1,4})for(int mode:new int[]{0,1,4,12}){hash=31*hash+run(name,hex,1,cap,mode,seed,0,false,false).hashCode();count++;}}
  else {for(String hex:new String[]{"0041dfff0042","d8000041"})for(int backend:new int[]{3,4,5,7})for(int mode:new int[]{3,5,7,8,9,10,13,14,15,16,17,18,19}){hash=31*hash+run(name,hex,1,3,mode,0,backend,false,false).hashCode();count++;}}
  return count+"|"+hash;
 }
}
