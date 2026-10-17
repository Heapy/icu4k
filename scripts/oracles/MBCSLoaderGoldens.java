import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
public class MBCSLoaderGoldens {
 static Field field(Class<?> c,String name)throws Exception{for(;c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static int number(Object o,String n)throws Exception{Object v=get(o,n);return v instanceof Character?(char)v:((Number)v).intValue();}
 static int hash(Object o){if(o==null)return 0;if(o instanceof byte[] a)return Arrays.hashCode(a);if(o instanceof char[] a)return Arrays.hashCode(a);if(o instanceof int[] a)return Arrays.hashCode(a);if(o instanceof int[][] a)return Arrays.deepHashCode(a);throw new RuntimeException();}
 static String row(String name,Constructor<?> ctor)throws Exception{
  Object c=ctor.newInstance(name,"x-icu4k-test",new String[0]);Object shared=get(c,"sharedData"),t=get(shared,"mbcs");var values=new ArrayList<String>();
  values.add(name);values.add((String)get(c,"icuCanonicalName"));for(String n:new String[]{"options","maxBytesPerChar","minBytesPerChar"})values.add(Integer.toString(number(c,n)));values.add(Integer.toString(hash(get(c,"fromUSubstitution"))));values.add(Integer.toString(number(c,"subChar1")));
  for(String n:new String[]{"countStates","dbcsOnlyState"})values.add(Integer.toString(number(t,n)));values.add(get(t,"stateTableOwned").toString());for(String n:new String[]{"countToUFallbacks","fromUBytesLength","outputType","unicodeMask"})values.add(Integer.toString(number(t,n)));values.add(get(t,"utf8Friendly").toString());values.add(Integer.toString(number(t,"maxFastUChar")));values.add(Integer.toString(number(t,"asciiRoundtrips")));values.add(Integer.toString(((int[][])get(t,"stateTable")).length));
  Object base=get(t,"baseSharedData");values.add(base==null?"0":Integer.toString(((String)get(get(base,"staticData"),"name")).hashCode()));values.add(Boolean.toString(base!=null&&get(base,"mbcs")==t));
  for(String n:new String[]{"stateTable","unicodeCodeUnits","fromUnicodeTable","fromUnicodeTableInts","fromUnicodeBytes","fromUnicodeChars","fromUnicodeInts","swapLFNLStateTable","swapLFNLFromUnicodeChars"})values.add(Integer.toString(hash(get(t,n))));values.add(String.valueOf(get(t,"swapLFNLName")));
  int h=1;for(Object f:(Object[])get(t,"toUFallbacks")){h=31*h+number(f,"offset");h=31*h+number(f,"codePoint");}values.add(Integer.toString(h));
  ByteBuffer ext=(ByteBuffer)get(t,"extIndexes");values.add(ext==null?"0":Integer.toString(ext.position()));values.add(ext==null?"0":Integer.toString(ext.remaining()));if(ext==null)values.add("0");else {byte[] bytes=new byte[ext.remaining()];ext.duplicate().get(bytes);values.add(Integer.toString(hash(bytes)));}values.add(Boolean.toString(get(t,"mbcsIndex")==null));return String.join("|",values);
 }
 public static void main(String[]args)throws Exception{
  var type=Class.forName("com.ibm.icu.charset.CharsetMBCS");var ctor=type.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/MBCSLoaderGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val mbcsLoaderGoldenRows = listOf(");
  try(var paths=Files.list(Path.of(args[0]))){for(var p:paths.filter(p->p.toString().endsWith(".cnv")).sorted().toList()){String name=p.getFileName().toString().replace(".cnv","");for(String suffix:new String[]{"",",swaplfnl"})System.out.println("    \""+row(name+suffix,ctor)+"\",");}}
  System.out.println(");");
 }
}
