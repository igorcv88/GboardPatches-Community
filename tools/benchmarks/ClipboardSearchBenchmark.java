import dev.jason.gboardpatches.extension.clipboard.ClipboardSearchMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host-only worst-case scan, intentionally separate from the unit/release gates. */
public class ClipboardSearchBenchmark {
    public static void main(String[] args) {
        List<String> clips=new ArrayList<>();
        String prefix="x".repeat(199990);
        for(int i=0;i<500;i++) clips.add(prefix+String.format("%010d",i));
        double[] times=new double[5];
        int matches=0;
        for(int sample=0;sample<times.length;sample++) {
            ClipboardSearchMatcher matcher=new ClipboardSearchMatcher("absent-query");
            long start=System.nanoTime();
            for(String clip:clips) if(matcher.matches(clip,()->false)) matches++;
            times[sample]=(System.nanoTime()-start)/1e6;
        }
        Arrays.sort(times);
        System.out.printf(java.util.Locale.ROOT,
                "Host JVM only; 500 x 200000 UTF-16 units; 5 full scans; p50=%.1fms p95=%.1fms matches=%d%n",
                times[2],times[4],matches);
    }
}
