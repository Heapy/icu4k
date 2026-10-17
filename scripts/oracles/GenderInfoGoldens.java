import com.ibm.icu.util.*;
public class GenderInfoGoldens {
 public static void main(String[] args) {
  for(String id:new String[]{"", "en", "fr", "el", "is", "ru", "ar", "de", "es", "he", "it", "pt", "zh", "fr_CA_POSIX", "el_GR", "zz_ZZ", "el_GR@currency=EUR", "iw_IL"}) {
   GenderInfo info=GenderInfo.getInstance(new ULocale(id));StringBuilder out=new StringBuilder();
   for(int length=0;length<=4;length++){int total=(int)Math.pow(3,length);for(int n=0;n<total;n++){int v=n;GenderInfo.Gender[] values=new GenderInfo.Gender[length];for(int i=0;i<length;i++){values[i]=GenderInfo.Gender.values()[v%3];v/=3;}out.append(info.getListGender(values).ordinal());}}
   System.out.println(id+"|"+out);
  }
 }
}
