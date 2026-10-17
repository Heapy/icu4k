import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
public class SyntheticFromUnicodeGoldens {
 static ByteBuffer fixture(int kind){
  var b=ByteBuffer.allocate(2048);b.putInt(40,128);b.putInt(44,65);b.putInt(48,256);b.putInt(52,640);b.putInt(56,128);b.putInt(60,896);b.putInt(64,4);b.putInt(20,1024);b.putInt(24,1280);b.putInt(28,64);b.putInt(32,1792);b.putInt(36,64);
  for(int i=0;i<65;i++)b.putChar(128+i*2,(char)65);for(int i:new int[]{0,56,64})b.putChar(128+i*2,(char)129);b.putChar(128+129*2,(char)16);b.putChar(128+133*2,(char)16);b.putChar(640+64*2,(char)1);b.putChar(640+65*2,(char)1);
  int root=kind==0?0x81000041:kind==1?0x01000041:kind==2?0x80000001:kind==3?0xa1000041:kind==4?0x00200000:1;b.putInt(900,root);
  b.putChar(1026,(char)2);b.putChar(1028,'A');b.putChar(1030,(char)0xd800);b.putInt(1284,0x01000058);b.putInt(1288,0x82008140);b.putInt(1292,4);b.putChar(1032,(char)1);b.putChar(1034,'B');b.putInt(1296,0x81000059);b.putInt(1300,kind==7?0xa1000041:kind==8?0x80000001:0x9f000000);
  if(kind==6){for(int depth=0;depth<=20;depth++){int index=1+2*depth;b.putChar(1024+index*2,(char)1);b.putChar(1024+(index+1)*2,'A');b.putInt(1280+index*4,0x81000050);b.putInt(1280+(index+1)*4,depth==20?0x81000051:index+2);}}
  for(int i=0;i<64;i++)b.put(1792+i,(byte)(i+1));return b;
 }
 public static void main(String[]args)throws Exception{
  var c=Class.forName("com.ibm.icu.charset.CharsetMBCS");var ctor=c.getDeclaredConstructor(String.class,String.class,String[].class);ctor.setAccessible(true);
  System.out.println("// Generated independently from ICU4J 78.3 by scripts/oracles/SyntheticFromUnicodeGoldens.java.\npackage io.github.heapy.icu4k.charset\n\ninternal val syntheticFromUnicodeGoldenRows = listOf(");
  for(int kind=0;kind<9;kind++){var cs=(Charset)ctor.newInstance("ibm-943_P130-1999","x-icu4k-oracle",new String[0]);var table=FromUnicodeExtensionGoldens.get(FromUnicodeExtensionGoldens.get(cs,"sharedData"),"mbcs");var field=table.getClass().getDeclaredField("extIndexes");field.setAccessible(true);var b=fixture(kind);field.set(table,b);System.out.println("    \""+kind+"|"+FromUnicodeExtensionGoldens.fingerprint(cs.newEncoder(),b)+"\",");}
  System.out.println(");");
 }
}
