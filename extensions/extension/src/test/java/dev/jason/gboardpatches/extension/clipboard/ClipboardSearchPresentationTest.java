package dev.jason.gboardpatches.extension.clipboard;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static dev.jason.gboardpatches.extension.clipboard.ClipboardSearchController.Section.*;
import static dev.jason.gboardpatches.extension.clipboard.ClipboardSearchPresentation.*;

public class ClipboardSearchPresentationTest {
    @Test public void sectionsCountsOrderAndOriginalModelIdentitySurviveFiltering() {
        Object rh=new Object(), ph=new Object(), sh=new Object(), r=new Object(), p=new Object(), s=new Object();
        List<Row<Object>> stock=List.of(new Row<>(-1,RECENT,rh,true),new Row<>(1,RECENT,r,false),
                new Row<>(-2,PINNED,ph,true),new Row<>(2,PINNED,p,false),
                new Row<>(-3,SPECIAL,sh,true),new Row<>(3,SPECIAL,s,false));
        Result<Object> all=filter(stock,List.of(1L,2L,3L)); assertEquals(6,all.items.size());
        assertSame(r,all.items.get(1)); assertSame(p,all.items.get(3));
        Result<Object> two=filter(stock,List.of(3L,2L,99L));
        assertEquals(List.of(ph,p,sh,s),two.items); assertEquals(0,two.recentCount); assertEquals(1,two.pinnedCount);
        assertTrue(filter(stock,List.of()).items.isEmpty()); assertEquals(6,stock.size());
    }
}
