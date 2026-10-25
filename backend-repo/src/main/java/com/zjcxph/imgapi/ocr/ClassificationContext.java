package com.zjcxph.imgapi.ocr;

import java.util.List;

/** Context supports review only. A rule weight is not a classification probability. */
public final class ClassificationContext {
    public record Page(int scanId,Integer number,Integer type,boolean independent,boolean boundary,
                       boolean continuation,boolean reverse,boolean usable,int width,int height) {}
    public record Advice(Integer type,Integer reference,boolean reversePair) {}
    public Advice analyze(List<Page> preceding,Page current) {
        Advice none=new Advice(null,null,false);
        if(preceding.isEmpty()||preceding.size()>2||!current.usable()||current.boundary()
                ||current.type()!=null||!current.continuation()) return none;
        Page anchor=preceding.getFirst();
        if(!anchor.independent()||anchor.type()==null||!anchor.usable()||anchor.number()==null||anchor.number()<=0) return none;
        Page previous=anchor;
        for(int i=1;i<=preceding.size();i++) {
            Page next=i==preceding.size()?current:preceding.get(i);
            if(!next.usable()||next.number()==null||next.number()!=previous.number()+1
                    ||next.boundary()||next.type()!=null||!next.continuation()
                    ||!geometry(anchor,next)) return none;
            previous=next;
        }
        return new Advice(anchor.type(),anchor.scanId(),preceding.size()==1&&current.reverse());
    }
    private boolean geometry(Page a,Page b) {
        return a.width()>0&&a.height()>0&&b.width()>0&&b.height()>0
                &&Math.abs((double)b.width()/a.width()-1)<=.05
                &&Math.abs((double)b.height()/a.height()-1)<=.05;
    }
}
