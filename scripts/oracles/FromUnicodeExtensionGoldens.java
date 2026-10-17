import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
public class FromUnicodeExtensionGoldens {
 static Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static Buffer array(ByteBuffer b,int index,boolean ints){var v=b.duplicate().order(b.order()).position(b.getInt(index*4));return ints?v.asIntBuffer():v.asCharBuffer();}
 static void record(Set<String> tails,String prefix){tails.add(prefix);tails.add(prefix+"5555");}
 static void walk(CharBuffer chars,IntBuffer values,int index,String prefix,Set<String> tails){record(tails,prefix);for(int i=1;i<=chars.get(index);i++){int v=values.get(index+i);if(v==0)continue;String next=prefix+String.format("%04x",(int)chars.get(index+i));if((v>>>24)==0)walk(chars,values,v,next,tails);else record(tails,next);}}
 static String fingerprint(Object encoder,ByteBuffer b)throws Exception{
  if(b==null)return "0|1|0|1";
  var match=encoder.getClass().getDeclaredMethod("matchFromU",int.class,char[].class,int.class,int.class,CharBuffer.class,int[].class,boolean.class,boolean.class);match.setAccessible(true);
  var simple=encoder.getClass().getDeclaredMethod("simpleMatchFromU",int.class,int[].class,boolean.class);simple.setAccessible(true);
  var outputs=new TreeSet<Integer>();int hash=1,count=0,stage1Length=b.getInt(44);if(stage1Length<=0)return "0|1|0|1";
  var s12=(CharBuffer)array(b,10,false);var s3=(CharBuffer)array(b,13,false);var s3b=(IntBuffer)array(b,15,true);var chars=(CharBuffer)array(b,5,false);var values=(IntBuffer)array(b,6,true);
  for(int cp=0;cp<Math.min(stage1Length*1024,0x110000);cp++){
   int index=s3.get((s12.get(s12.get(cp>>>10)+((cp>>>4)&63))<<2)+(cp&15)),value=s3b.get(index);if(value==0)continue;var tails=new TreeSet<String>();if(value>=0&&value<0x1f0000)walk(chars,values,value,"",tails);else record(tails,"");
   for(String hex:tails){char[] input=new char[hex.length()/4];for(int i=0;i<input.length;i++)input[i]=(char)Integer.parseInt(hex.substring(i*4,i*4+4),16);
    for(int split:new LinkedHashSet<Integer>(Arrays.asList(0,input.length/2,input.length)))for(boolean fallback:new boolean[]{false,true})for(boolean flush:new boolean[]{false,true}){
     char[] pre=new char[split+4];System.arraycopy(input,0,pre,2,split);char[] backing=new char[input.length-split+4];System.arraycopy(input,split,backing,1,input.length-split);var source=CharBuffer.wrap(backing).position(1).limit(1+input.length-split);int[] out={123};
     int result=(Integer)match.invoke(encoder,cp,pre,2,split,source,out,fallback,flush);if(result>=2)outputs.add(out[0]);hash=31*hash+result;hash=31*hash+out[0];hash=31*hash+source.position();hash=31*hash+b.position();int[] single={123};hash=31*hash+(Integer)simple.invoke(encoder,cp,single,fallback);hash=31*hash+single[0];count++;
    }
   }
  }
  return count+"|"+hash+"|"+writes(encoder,b,outputs);
 }
 static String writes(Object original,ByteBuffer b,Set<Integer> values)throws Exception{
  var method=original.getClass().getDeclaredMethod("writeFromU",int.class,ByteBuffer.class,IntBuffer.class,int.class);method.setAccessible(true);
  var base=Class.forName("com.ibm.icu.charset.CharsetEncoderICU");var status=base.getDeclaredField("fromUnicodeStatus");status.setAccessible(true);var length=base.getDeclaredField("errorBufferLength");length.setAccessible(true);var errors=base.getDeclaredField("errorBuffer");errors.setAccessible(true);
  int h=1,count=0;for(int value:values)for(int previous:new int[]{0,1,2})for(int cap:new int[]{0,1,3,32})for(boolean readonly:new boolean[]{false,true}){
   var e=((java.nio.charset.CharsetEncoder)original).charset().newEncoder();status.setInt(e,previous);byte[] out=new byte[cap+4];ByteBuffer target=ByteBuffer.wrap(out,2,cap).slice();if(readonly)target=target.asReadOnlyBuffer();var offsets=IntBuffer.allocate(cap);String result;
   try{result=method.invoke(e,value,target,offsets,7).toString();}catch(InvocationTargetException ex){var t=ex.getCause();if(t instanceof BufferOverflowException)result="THROW:overflow";else if(t instanceof BufferUnderflowException)result="THROW:underflow";else if(t instanceof ReadOnlyBufferException)result="THROW:readonly";else if(t instanceof IndexOutOfBoundsException)result="THROW:bounds";else if(t instanceof IllegalArgumentException)result="THROW:argument";else throw ex;}
   h=31*h+result.hashCode();h=31*h+target.position();h=31*h+offsets.position();h=31*h+Arrays.hashCode(out);h=31*h+Arrays.hashCode(offsets.array());h=31*h+status.getInt(e);h=31*h+length.getInt(e);h=31*h+Arrays.hashCode((byte[])errors.get(e));h=31*h+b.position();count++;
  }return count+"|"+h;
 }
 public static void main(String[]args)throws Exception{
  var c=Class.forName("com.ibm.icu.charset.CharsetMBCS");var ctor=c.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/FromUnicodeExtensionGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val fromUnicodeExtensionGoldenRows = listOf(");
  try(var paths=Files.list(Path.of(args[0]))){for(var p:paths.filter(p->p.toString().endsWith(".cnv")).sorted().toList()){String name=p.getFileName().toString().replace(".cnv","");var cs=(Charset)ctor.newInstance(name,"x-icu4k-oracle",new String[0]);var table=get(get(cs,"sharedData"),"mbcs");var b=(ByteBuffer)get(table,"extIndexes");System.out.println("    \""+name+"|"+fingerprint(cs.newEncoder(),b)+"\",");}}
  System.out.println(");");
 }
}
