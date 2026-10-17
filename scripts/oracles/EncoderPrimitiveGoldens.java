import com.ibm.icu.charset.*;
import java.lang.reflect.*;
public class EncoderPrimitiveGoldens {
 public static void main(String[] args)throws Exception{
  var fallback=CharsetEncoderICU.class.getDeclaredMethod("isFromUUseFallback",boolean.class,int.class);fallback.setAccessible(true);
  var ignorable=CharsetCallback.class.getDeclaredMethod("IS_DEFAULT_IGNORABLE_CODE_POINT",int.class);ignorable.setAccessible(true);
  int h=1;for(int cp=-1;cp<=0x110000;cp++){h=31*h+((boolean)fallback.invoke(null,false,cp)?1:0);h=31*h+((boolean)fallback.invoke(null,true,cp)?1:0);h=31*h+((boolean)ignorable.invoke(null,cp)?1:0);}
  System.out.println("// Generated from ICU4J 78.3 by scripts/oracles/EncoderPrimitiveGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal const val encoderCodePointRulesGolden = "+h);
 }
}
