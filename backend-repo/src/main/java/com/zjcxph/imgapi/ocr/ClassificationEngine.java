package com.zjcxph.imgapi.ocr;

import com.google.re2j.Pattern;
import java.text.Normalizer;
import java.util.*;

/** Scores are rule weights, never probabilities. Raw text remains untouched. */
public class ClassificationEngine {
    public record Rule(Long id,int targetType,String keyword,String matchScope,String matchMode,double weight,
                       boolean negative,boolean required,int priority,boolean enabled) {}
    public record Lexicon(String term,List<String> aliases,List<String> commonOcrErrors) {}
    public record Outcome(Integer targetType,double score,List<Long> matched,List<Long> excluded,boolean autoEligible,String reason) {}
    public void validate(Rule r) {
        if (r.targetType()<1 || r.targetType()>15 || r.keyword()==null || r.keyword().isBlank() || r.keyword().length()>500
                || !Set.of("TITLE","BODY","ANY").contains(r.matchScope()) || !Set.of("CONTAINS","EXACT","REGEX").contains(r.matchMode())
                || !Double.isFinite(r.weight()) || r.weight()<=0 || r.weight()>1000 || r.priority()<0)
            throw new IllegalArgumentException("分类规则无效");
        if ("REGEX".equals(r.matchMode())) {
            try { Pattern.compile(r.keyword()); }
            catch (com.google.re2j.PatternSyntaxException e) { throw new IllegalArgumentException("分类正则无效"); }
        }
    }
    public Outcome classify(List<Rule> rules,List<Lexicon> lexicon,OcrProtocol.Result page) {
        Map<Integer,Double> scores=new HashMap<>(); Map<Integer,Integer> priorities=new HashMap<>();
        Set<Integer> excludedTypes=new HashSet<>(); Set<Integer> exactTitles=new HashSet<>();
        List<Long> matched=new ArrayList<>(); List<Long> excluded=new ArrayList<>();
        for (Rule rule:rules) {
            if (!rule.enabled()) continue;
            validate(rule);
            boolean hit=false,exactTitle=false;
            for(var line:page.lines()) {
                boolean title=line.box().get(1)<=page.height()*.25 && line.text().length()<=64;
                if (("TITLE".equals(rule.matchScope())&&!title)||("BODY".equals(rule.matchScope())&&title)) continue;
                String raw=normalize(line.text());
                boolean rawHit=matches(rule,raw);
                for (Lexicon entry:lexicon) {
                    if (normalize(entry.term()).equals(normalize(rule.keyword())) && "EXACT".equals(rule.matchMode()))
                        rawHit=rawHit||entry.aliases().stream().map(this::normalize).anyMatch(raw::equals);
                }
                String derived=raw;
                for(Lexicon entry:lexicon)
                    for(String error:entry.commonOcrErrors()) if (!normalize(error).isEmpty()) derived=derived.replace(normalize(error),normalize(entry.term()));
                boolean lineHit=rawHit||matches(rule,derived);
                hit=hit||lineHit;
                exactTitle=exactTitle||(rawHit&&title&&line.confidence()>=.98&&"TITLE".equals(rule.matchScope())&&"EXACT".equals(rule.matchMode()));
            }
            if ((rule.negative()&&hit)||(rule.required()&&!hit)) { excludedTypes.add(rule.targetType()); excluded.add(rule.id()); }
            if (!rule.negative()&&hit) {
                scores.merge(rule.targetType(),rule.weight(),Double::sum); priorities.merge(rule.targetType(),rule.priority(),Math::min);
                matched.add(rule.id()); if(exactTitle) exactTitles.add(rule.targetType());
            }
        }
        excludedTypes.forEach(scores::remove);
        if(scores.isEmpty()) return new Outcome(null,0,matched,excluded,false,"NO_MATCH");
        List<Integer> ranked=new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(priorities::get).thenComparingInt(Integer::intValue));
        Integer type=ranked.getFirst();
        boolean automatic=ranked.size()==1&&exactTitles.contains(type)&&page.confidence()>=.90;
        return new Outcome(type,scores.get(type),matched,excluded,automatic,
                ranked.size()>1?"CONFLICT":automatic?"EXACT_TITLE":"REVIEW_REQUIRED");
    }
    private boolean matches(Rule r,String text) {
        return switch(r.matchMode()) {
            case "EXACT" -> text.equals(normalize(r.keyword()));
            case "REGEX" -> Pattern.compile(r.keyword()).matcher(text).find();
            default -> text.contains(normalize(r.keyword()));
        };
    }
    private String normalize(String text) { return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","").trim(); }
}
