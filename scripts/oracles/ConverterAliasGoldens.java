import java.lang.reflect.*;
import java.util.*;
public class ConverterAliasGoldens {
 static Class<?> type;static Method count,get,canonical,standard,available,size;
 static Method method(String name,Class<?>...args)throws Exception{var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
 static Object field(String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static String string(byte[] bytes,int index){int end=index*2;while(bytes[end]!=0)end++;return new String(bytes,index*2,end-index*2,java.nio.charset.StandardCharsets.ISO_8859_1);}
 static String quoted(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("$","\\$")+"\"";}
 public static void main(String[]args)throws Exception{
  type=Class.forName("com.ibm.icu.charset.UConverterAlias");count=method("countAliases",String.class);get=method("getAlias",String.class,int.class);canonical=method("getCanonicalName",String.class,String.class);standard=method("getStandardName",String.class,String.class);size=method("countAvailable");available=method("getAvailableName",int.class);
  int n=(int)size.invoke(null);char[] tags=(char[])field("gTagList"),aliases=(char[])field("gAliasList");byte[] strings=(byte[])field("gStringTable");
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/ConverterAliasGoldens.java.\npackage io.github.heapy.icu4k.charset\n");
  System.out.print("internal val converterAliasStandards = listOf(");for(char tag:tags)System.out.print(quoted(string(strings,tag))+",");System.out.println("\"unknown\",\"iana\")");
  System.out.println("internal val converterAvailableNames = listOf(");for(int i=0;i<n;i++)System.out.println("    "+quoted((String)available.invoke(null,i))+",");System.out.println(")");
  var queries=new LinkedHashSet<String>();for(char alias:aliases)queries.add(string(strings,alias));queries.addAll(List.of("", "not-a-charset", "UTF-0008", "u.t_f 008", "ISO8859-0001", "ibm-00037", "utf😀8"));
  System.out.println("internal val converterAliasGoldenRows = listOf(");
  var standards=new ArrayList<String>();for(char tag:tags)standards.add(string(strings,tag));standards.add("unknown");standards.add("iana");
  for(String alias:queries){int c=(int)count.invoke(null,alias);var names=new StringJoiner(";");for(int i=0;i<c;i++)names.add((String)get.invoke(null,alias,i));var result=new StringJoiner(";");for(String tag:standards){result.add(String.valueOf(standard.invoke(null,alias,tag)));result.add(String.valueOf(canonical.invoke(null,alias,tag)));}System.out.println("    "+quoted(alias+"|"+c+"|"+names+"|"+result)+",");}
  System.out.println(")");
 }
}
