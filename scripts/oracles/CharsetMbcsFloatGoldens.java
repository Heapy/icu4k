import com.ibm.icu.text.*;
public class CharsetMbcsFloatGoldens {
 public static void main(String[] args)throws Exception {
  var type=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_sjis");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object recog=ctor.newInstance();
  var method=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",CharsetDetector.class);method.setAccessible(true);
  for(int count:new int[]{16777216,16777217,16777219,16777221})for(int common:new int[]{4194303,4194304}) {
   byte[] raw=new byte[count*2];for(int i=0;i<count;i++){raw[i*2]=(byte)0x81;raw[i*2+1]=(byte)(i<common?0x40:0x90);}
   var result=(CharsetMatch)method.invoke(recog,new CharsetDetector().setText(raw));System.out.println(count+"|"+common+"|"+result.getConfidence());
  }
 }
}
