import com.ibm.icu.text.*;
import java.lang.reflect.*;
public class CharsetRecognizerGoldens {
 static String[] names={"CharsetRecog_UTF8","CharsetRecog_Unicode$CharsetRecog_UTF_16_BE","CharsetRecog_Unicode$CharsetRecog_UTF_16_LE","CharsetRecog_Unicode$CharsetRecog_UTF_32_BE","CharsetRecog_Unicode$CharsetRecog_UTF_32_LE","CharsetRecog_2022$CharsetRecog_2022JP","CharsetRecog_2022$CharsetRecog_2022KR","CharsetRecog_2022$CharsetRecog_2022CN"};
 static byte[] decode(String hex){return java.util.HexFormat.of().parseHex(hex);}
 public static void main(String[]args)throws Exception {
  var method=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",CharsetDetector.class);method.setAccessible(true);
  String[] cases={"","00","61626364","efbbbf","efbbbfc0","c080","eda080","f48fbfbf","f4908080","e282","fffe","feff","fffe0000","0000feff","0010fffe","0010ffff","00110000","0000d800","80000000","004100420043004400450046","410042004300440045004600","1b242843","1b242943","1b242941","1b4e","1b4f","1b2428430e0f0e0f","1b2428431b2428431b2428431b2428431b242843","1b2428431b424144","efbbbfe282ac","f09f9880","c2a1".repeat(12)+"ff","efbbbf"+"c2a1".repeat(12)+"ff",
    "00000041".repeat(12)+"ffffffff","0000feff"+"00000041".repeat(12)+"ffffffff",
    "41000000".repeat(12)+"ffffffff","fffe0000"+"41000000".repeat(12)+"ffffffff",
    "1b242843".repeat(3)+"1b424144","1b242843".repeat(5)+"1b424144"};
  Object[] recognizers=new Object[names.length];for(int i=0;i<names.length;i++){var ctor=Class.forName("com.ibm.icu.text."+names[i]).getDeclaredConstructor();ctor.setAccessible(true);recognizers[i]=ctor.newInstance();}
  var rawLength=CharsetDetector.class.getDeclaredField("fRawLength");rawLength.setAccessible(true);
  var inputBytes=CharsetDetector.class.getDeclaredField("fInputBytes");inputBytes.setAccessible(true);
  var inputLength=CharsetDetector.class.getDeclaredField("fInputLen");inputLength.setAccessible(true);
  for(String hex:cases)for(boolean padded:new boolean[]{false,true}) {
   byte[] exact=decode(hex),raw=padded?java.util.Arrays.copyOf(exact,8000):exact;
   var detector=new CharsetDetector().setText(raw);rawLength.setInt(detector,exact.length);inputBytes.set(detector,exact);inputLength.setInt(detector,exact.length);
   StringBuilder result=new StringBuilder();for(Object recognizer:recognizers){var match=(CharsetMatch)method.invoke(recognizer,detector);if(result.length()>0)result.append(',');result.append(match==null?0:match.getConfidence());}
   System.out.println(hex+"|"+padded+"|"+result);
  }
 }
}
