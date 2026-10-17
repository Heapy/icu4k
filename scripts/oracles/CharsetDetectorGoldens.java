import com.ibm.icu.text.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class CharsetDetectorGoldens {
 static Field input,len,stats,c1;
 static String[] tags={"<b>","<b><i><u><p><a>","<<b><i><u><p><a>","<<<b><i><u><p><a>","<<<<<",">>>>>"};
 static Field field(String name)throws Exception{var f=CharsetDetector.class.getDeclaredField(name);f.setAccessible(true);return f;}
 @SuppressWarnings("deprecation") static void row(String description,byte[] bytes,boolean filter,int mode)throws Exception{
  var d=new CharsetDetector(); if(description.startsWith("p:"))d.setText(new java.io.ByteArrayInputStream(bytes));else d.setText(bytes);d.enableInputFilter(filter);
  if(mode!=0)for(String name:CharsetDetector.getAllDetectableCharsets())d.setDetectableCharset(name,mode==1||(mode==2&&name.equals("UTF-8"))||(mode==4&&name.startsWith("IBM")));
  var results=new StringJoiner(",");for(var m:d.detectAll())results.add(m.getName()+":"+m.getConfidence()+":"+m.getLanguage());
  int n=len.getInt(d);int hash=Arrays.hashCode(Arrays.copyOf((byte[])input.get(d),n));
  System.out.println("    \""+description+"|"+filter+"|"+mode+"|"+n+"|"+c1.getBoolean(d)+"|"+hash+"|"+Arrays.hashCode((short[])stats.get(d))+"|"+results+"\",");
 }
 @SuppressWarnings("deprecation") public static void main(String[]args)throws Exception{
  input=field("fInputBytes");len=field("fInputLen");stats=field("fByteStats");c1=field("fC1Bytes");
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/CharsetDetectorGoldens.java.\npackage io.github.heapy.icu4k.text\n\ninternal val charsetDetectorGoldenRows = listOf(");
  for(String hex:new String[]{"","00","61626364","efbbbf","fffe","feff","0000feff","fffe0000","1b2429430e0f","56b256b356b456b556b856b9","74686520616e6420746865206f746865722070656f706c6520696e2074686520776f726c64","8081829f20ff"})for(boolean filter:new boolean[]{false,true})for(int mode=0;mode<5;mode++)row("h:"+hex,HexFormat.of().parseHex(hex),filter,mode);
  for(String hex:new String[]{"","00","fffe","feff","efbbbf","0000feff","6162"})row("p:"+hex,HexFormat.of().parseHex(hex),false,0);
  for(int n:new int[]{0,99,100,601,8001})for(int t=0;t<tags.length;t++)for(int tail=0;tail<2;tail++)for(boolean filter:new boolean[]{false,true}){
   String raw=tags[t]+"a".repeat(n)+(tail==0?"":"<z>\u0080</z>");
   row("t:"+n+":"+t+":"+tail,raw.getBytes(StandardCharsets.ISO_8859_1),filter,0);
  }
  System.out.println(");");
 }
}
