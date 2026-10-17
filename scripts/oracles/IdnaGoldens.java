import com.ibm.icu.text.IDNA;
public class IdnaGoldens {
 static String hex(String s) {StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(String.format("%04x",(int)c));return b.toString();}
 public static void main(String[] args) {
  String[] inputs={"", ".", "..", "EXAMPLE.COM.", "bücher.de", "faß.de", "ς.gr", "a\u200cb", "a\u200db", "क्\u200dष", "א1.2a", "a.אב", "ل\u200cا", "ل\u0301\u200c\u0301ا", "l·l", "a·b", "͵α", "͵a", "א׳", "a׳", "カ・", "a・", "١۲", "١٢", "۱۲", "\u0301a", "-a-", "ab--cd", "xn--", "xn---", "xn--a-", "xn--abc-", "xn--bcher-kva", "xn--fa-hia", "xn--xn---epa", "a_b", "a b", "😀.com", "Ａ．Ｂ。Ｃ｡", "a".repeat(64), String.join(".", java.util.Collections.nCopies(4,"a".repeat(63))),new String(new char[]{'\ud800'}),new String(new char[]{'a','\udc00','b'}),"𞤀𞤁", "𐐀𐐨"};
  System.out.println("// Generated independently from ICU4J 78.3 by IdnaGoldens.java.\npackage io.github.heapy.icu4k.text\n\ninternal val idnaGoldenRows = listOf(");
  for(int options:new int[]{0,2,14,48,62,126}) {
   IDNA processor=IDNA.getUTS46Instance(options);
   for(String input:inputs)for(int op=0;op<4;op++) {
    IDNA.Info info=new IDNA.Info();StringBuilder out=new StringBuilder();
    switch(op){case 0:processor.labelToASCII(input,out,info);break;case 1:processor.labelToUnicode(input,out,info);break;case 2:processor.nameToASCII(input,out,info);break;default:processor.nameToUnicode(input,out,info);}
    String errors=String.join(",",info.getErrors().stream().map(e->e.name()).toList());
    System.out.println("    \""+options+"|"+op+"|"+hex(input)+"|"+hex(out.toString())+"|"+errors+"|"+info.isTransitionalDifferent()+"\",");
   }
  }
  System.out.println(")");
 }
}
