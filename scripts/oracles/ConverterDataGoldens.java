import java.nio.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
public class ConverterDataGoldens {
 static Class<?> reader,sd,header,table,fallback;static Constructor<?> ctor;static Method readStatic,readHeader,readTable,readBase,readExt,unicode,consumed;
 static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static Object make(Class<?> c)throws Exception{var x=c.getDeclaredConstructor();x.setAccessible(true);return x.newInstance();}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static int number(Object o,String n)throws Exception{return ((Number)get(o,n)).intValue();}
 static Method method(String n,Class<?>...args)throws Exception{var m=reader.getDeclaredMethod(n,args);m.setAccessible(true);return m;}
 static int hash(Object v){if(v==null)return 0;if(v instanceof byte[] a)return Arrays.hashCode(a);if(v instanceof char[] a)return Arrays.hashCode(a);if(v instanceof int[] a)return Arrays.hashCode(a);if(v instanceof int[][] a)return Arrays.deepHashCode(a);if(v instanceof CharBuffer a){int h=1;for(int i=0;i<a.remaining();i++)h=31*h+a.get(i);return h;}throw new RuntimeException(v.getClass().toString());}
 static String row(String name,byte[] raw)throws Exception{
  var b=ByteBuffer.wrap(raw);var r=ctor.newInstance(b);var s=make(sd);readStatic.invoke(r,s);var h=make(header);readHeader.invoke(r,h);
  var values=new ArrayList<String>();values.add(name);values.add(Boolean.toString((boolean)unicode.invoke(r)));values.add(Integer.toString(b.position()));
  // Canonical string fingerprint retains all 60 NUL-padded characters.
  values.add(Integer.toString(((String)get(s,"name")).hashCode()));
  for(String n:new String[]{"structSize","codepage","platform","conversionType","minBytesPerChar","maxBytesPerChar","subCharLen","hasToUnicodeFallback","hasFromUnicodeFallback","unicodeMask","subChar1"})values.add(Integer.toString(number(s,n)));
  values.add(Integer.toString(hash(get(s,"subChar"))));values.add(Integer.toString(hash(get(s,"reserved"))));values.add(Integer.toString(hash(get(h,"version"))));
  for(String n:new String[]{"countStates","countToUFallbacks","offsetToUCodeUnits","offsetFromUTable","offsetFromUBytes","flags","fromUBytesLength","options","fullStage2Length"})values.add(Integer.toString(number(h,n)));
  int flags=number(h,"flags"), output=(byte)flags, offset=flags>>>8;
  if(output==14){values.add((String)readBase.invoke(r));}else{
   var t=make(table);field(table,"outputType").setShort(t,(short)output);field(table,"unicodeMask").setShort(t,(short)((boolean)unicode.invoke(r)?number(s,"unicodeMask")&3:3));
   readTable.invoke(r,h,t);
   for(String n:new String[]{"countStates","countToUFallbacks","fromUBytesLength"})values.add(Integer.toString(number(t,n)));
   for(String n:new String[]{"stateTable","unicodeCodeUnits","fromUnicodeTable","fromUnicodeTableInts","fromUnicodeBytes","fromUnicodeChars","fromUnicodeInts","mbcsIndex"})values.add(Integer.toString(hash(get(t,n))));
   int fh=1;for(Object f:(Object[])get(t,"toUFallbacks")){fh=31*fh+number(f,"offset");fh=31*fh+number(f,"codePoint");}values.add(Integer.toString(fh));
  }
  values.add(Integer.toString((int)consumed.invoke(r)));
  if(offset!=0){var ext=(ByteBuffer)readExt.invoke(r,offset-(int)consumed.invoke(r));byte[] copy=new byte[ext.remaining()];ext.get(copy);values.add(Integer.toString(hash(copy)));values.add(Integer.toString(copy.length));}else{values.add("0");values.add("0");}
  values.add(Integer.toString(b.position()));values.add(Integer.toString((int)consumed.invoke(r)));
  return String.join("|",values);
 }
 public static void main(String[]args)throws Exception{
  reader=Class.forName("com.ibm.icu.charset.UConverterDataReader");sd=Class.forName("com.ibm.icu.charset.UConverterStaticData");header=Class.forName("com.ibm.icu.charset.CharsetMBCS$MBCSHeader");table=Class.forName("com.ibm.icu.charset.CharsetMBCS$UConverterMBCSTable");
  ctor=reader.getDeclaredConstructor(ByteBuffer.class);ctor.setAccessible(true);readStatic=method("readStaticData",sd);readHeader=method("readMBCSHeader",header);readTable=method("readMBCSTable",header,table);readBase=method("readBaseTableName");readExt=method("readExtIndexes",int.class);unicode=method("dataFormatHasUnicodeMask");consumed=method("bytesReadAfterStaticData");
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/ConverterDataGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val converterDataGoldenRows = listOf(");
  try(var paths=Files.list(Path.of(args[0]))){for(var p:paths.filter(p->p.toString().endsWith(".cnv")).sorted().toList())System.out.println("    \""+row(p.getFileName().toString(),Files.readAllBytes(p))+"\",");}
  System.out.println(");");
 }
}
