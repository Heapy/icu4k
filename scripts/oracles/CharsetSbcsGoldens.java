import com.ibm.icu.text.*;
import java.lang.reflect.*;
import java.util.*;
public class CharsetSbcsGoldens {
 static String[] names={"8859_1","8859_2","8859_5_ru","8859_6_ar","8859_7_el","8859_8_I_he","8859_8_he","8859_9_tr","windows_1251","windows_1256","KOI8_R","IBM424_he_rtl","IBM424_he_ltr","IBM420_ar_rtl","IBM420_ar_ltr"};
 static Object[] recognizers=new Object[15];static Method match;static Field input,len,c1;
 static void row(byte[] bytes,int length,boolean flag)throws Exception {
  var det=new CharsetDetector().setText(new byte[]{0});input.set(det,bytes);len.setInt(det,length);c1.setBoolean(det,flag);
  var results=new StringJoiner(",");for(var r:recognizers){var m=(CharsetMatch)match.invoke(r,det);results.add(m==null?"-":m.getName()+":"+m.getConfidence()+":"+m.getLanguage());}
  System.out.println("    \""+HexFormat.of().formatHex(bytes)+"|"+length+"|"+flag+"|"+results+"\",");
 }
 static Field field(Class<?> c,String name)throws Exception {for(;c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 public static void main(String[]args)throws Exception {
  input=field(CharsetDetector.class,"fInputBytes");len=field(CharsetDetector.class,"fInputLen");c1=field(CharsetDetector.class,"fC1Bytes");
  match=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",CharsetDetector.class);match.setAccessible(true);
  for(int i=0;i<15;i++){var ctor=Class.forName("com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_"+names[i]).getDeclaredConstructor();ctor.setAccessible(true);recognizers[i]=ctor.newInstance();}
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/CharsetSbcsGoldens.java.\npackage io.github.heapy.icu4k.text\n\ninternal val charsetSbcsGoldenRows = listOf(");
  for(String hex:new String[]{"","00","202000202020","4040004040","b2b3b4b5b8b9","56b256b356b456b556b856b9","56b20056b3","7f80819f20ff","74686520616e6420746865206f746865722070656f706c65"}) {
   byte[] raw=HexFormat.of().parseHex(hex);for(int n=0;n<=raw.length;n++)for(boolean flag:new boolean[]{false,true})row(raw,n,flag);
  }
  for(Object rec:recognizers){
   var c=rec.getClass();var models=new ArrayList<int[]>();
   try{models.add((int[])field(c,"ngrams").get(null));}catch(NoSuchFieldException e){
    String f=c.getSimpleName().endsWith("_1")?"ngrams_8859_1":"ngrams_8859_2";
    for(Object model:(Object[])field(c,f).get(null))models.add((int[])field(model.getClass(),"fNGrams").get(model));
   }
   for(int[] model:models){byte[] bytes=new byte[model.length*4];int i=0;for(int v:model){bytes[i++]=(byte)(v>>16);bytes[i++]=(byte)(v>>8);bytes[i++]=(byte)v;bytes[i++]=0x20;}for(boolean flag:new boolean[]{false,true})row(bytes,bytes.length,flag);}
  }
  System.out.println(");");
 }
}
