import com.ibm.icu.text.*;
public class LegacyIdnaGoldens {
 static String hex(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(String.format("%04x",(int)c));return b.toString();}
 interface Call {Object run() throws Exception;}
 static String result(Call call) {
  try{return "ok:"+call.run();}catch(StringPrepParseException e){return "error:"+e.getError()+":"+e.toString();}catch(IndexOutOfBoundsException e){return "bounds";}catch(Exception e){return e.getClass().getSimpleName()+":"+e.getMessage();}
 }
 public static void main(String[] args) {
  String[] inputs={"", ".", "Example.COM.", "bücher.de", "faß.de", "ς.gr", "a\u200cb", "אa", "א1א", "xn--", "xn---", "xn--a-", "xn--abc-", "xn--bcher-kva", "xn--fa-hia", "XN--BCHER-KVA", "xn--é", "a_b", "-abc-", "a b", "\u00ad", "a。b．c｡", "😀.com", "User Name", "Straße", "Iİı", "\u00adA\u034f", "\u0000\u0007", "a\u00a0b", "ＡＢＣ", "a\u0301", "\u212b\ufb03", "1א", "ا\u0301", "😀", "𐀀", "\u0221", "\u0340\u0341", "\uf951", "\u200b\u200c\u200d", "\ue000", "\uffff",new String(new char[]{'\ud800'}),new String(new char[]{'a','\udc00'})};
  System.out.println("// Generated independently from ICU4J 78.3 by LegacyIdnaGoldens.java.\npackage io.github.heapy.icu4k.text\n\ninternal val legacyIdnaGoldenRows = listOf(");
  for(int profile=0;profile<14;profile++)for(int options=0;options<2;options++)for(String input:inputs) {
   StringPrep prep=StringPrep.getInstance(profile);int opt=options;
   System.out.println("    \"S|"+profile+"|"+options+"|"+hex(input)+"|"+hex(result(()->prep.prepare(input,opt)))+"\",");
  }
  for(int options=0;options<4;options++)for(String input:inputs)for(int op=0;op<4;op++) {
   int opt=options, operation=op;
   System.out.println("    \"I|"+op+"|"+options+"|"+hex(input)+"|"+hex(result(()->switch(operation){case 0->IDNA.convertToASCII(input,opt);case 1->IDNA.convertToUnicode(input,opt);case 2->IDNA.convertIDNToASCII(input,opt);default->IDNA.convertIDNToUnicode(input,opt); }))+"\",");
  }
  System.out.println(")");
 }
}
